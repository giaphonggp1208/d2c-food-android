package com.d2cfood.app.data.firebase

import com.d2cfood.app.data.AuthRepository
import com.d2cfood.app.data.StoreRepository
import com.d2cfood.app.domain.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await

/** Email/Password Auth. Profile creation is deliberately CUSTOMER-only; STORE_OWNER is Admin-seeded. */
class FirebaseAuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : AuthRepository {
    override suspend fun signIn(email: String, password: String): Result<UserProfile> = runCatching {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
        requireNotNull(readProfile(auth.currentUser!!.uid))
    }

    override suspend fun register(email: String, password: String, name: String): Result<UserProfile> = runCatching {
        val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
        val firebaseUser = requireNotNull(result.user)
        if (name.isNotBlank()) firebaseUser.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name.trim()).build()).await()
        val profile = UserProfile(firebaseUser.uid, name.trim(), email.trim(), roles = setOf(Role.CUSTOMER))
        // Matches firestore.rules: client can create only its own CUSTOMER bootstrap document.
        db.collection("users").document(firebaseUser.uid).set(
            mapOf("uid" to profile.uid, "name" to profile.name, "email" to profile.email, "phone" to profile.phone, "roles" to listOf("CUSTOMER"))
        ).await()
        profile
    }

    override fun currentUser(): Flow<UserProfile?> = callbackFlow {
        var registration: ListenerRegistration? = null
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            registration?.remove()
            val uid = firebaseAuth.currentUser?.uid
            registration = if (uid == null) {
                trySend(null); null
            } else {
                db.collection("users").document(uid).addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null || !snapshot.exists()) trySend(null)
                    else trySend(snapshot.toUserProfile())
                }
            }
        }
        auth.addAuthStateListener(listener)
        awaitClose { registration?.remove(); auth.removeAuthStateListener(listener) }
    }

    override suspend fun signOut() = auth.signOut()

    private suspend fun readProfile(uid: String): UserProfile? = db.collection("users").document(uid).get().await().toUserProfile()
}

private fun com.google.firebase.firestore.DocumentSnapshot.toUserProfile(): UserProfile? {
    if (!exists()) return null
    val roleNames = get("roles") as? List<*> ?: listOf("CUSTOMER")
    val roles = roleNames.mapNotNull { value -> runCatching { Role.valueOf(value.toString()) }.getOrNull() }.toSet()
    return UserProfile(id, getString("name").orEmpty(), getString("email").orEmpty(), getString("phone").orEmpty(), roles.ifEmpty { setOf(Role.CUSTOMER) })
}

/** Stable realtime read paths for Store/Menu; all writes remain behind owner-only rules. */
class FirestoreStoreRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : StoreRepository {
    override fun stores(): Flow<List<Store>> = callbackFlow {
        val registration = db.collection("stores").addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) trySend(emptyList())
            else trySend(snapshot.documents.mapNotNull { doc -> doc.toStore() })
        }
        awaitClose { registration.remove() }
    }

    override fun menu(storeId: String): Flow<List<MenuItem>> {
        // An empty storeId (no store selected yet) must NOT build a document
        // reference — db.collection("stores").document("") is an invalid reference
        // and Firestore throws IllegalArgumentException on the main thread,
        // crashing the app at startup.
        if (storeId.isBlank()) return flowOf(emptyList())
        return callbackFlow {
            val registration = db.collection("stores").document(storeId).collection("menuItems")
                .whereEqualTo("available", true).addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) trySend(emptyList())
                    else trySend(snapshot.documents.mapNotNull { it.toMenuItem(storeId) })
                }
            awaitClose { registration.remove() }
        }
    }
}

private fun com.google.firebase.firestore.DocumentSnapshot.toStore(): Store? = runCatching {
    Store(id, getString("ownerId")!!, getString("name").orEmpty(), getString("address").orEmpty(), getString("status") ?: "OPEN", getLong("fallbackPerKmFee") ?: 0)
}.getOrNull()

private fun com.google.firebase.firestore.DocumentSnapshot.toMenuItem(storeId: String): MenuItem? = runCatching {
    MenuItem(id, storeId, getString("name").orEmpty(), getString("description").orEmpty(), getLong("basePrice") ?: 0, getString("imageAsset").orEmpty(), getBoolean("available") ?: true)
}.getOrNull()
