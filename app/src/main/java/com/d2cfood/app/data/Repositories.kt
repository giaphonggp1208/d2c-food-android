package com.d2cfood.app.data

import com.d2cfood.app.domain.MenuItem
import com.d2cfood.app.domain.Store
import com.d2cfood.app.domain.UserProfile
import kotlinx.coroutines.flow.Flow

/**
 * Repository contracts (design §5.2): presentation/domain depend only on these;
 * implementations (Firebase or demo) live in `data.firebase` / `data.demo`.
 */
interface AuthRepository {
    /** Email/Password sign-in (design D2 — primary demo path). */
    suspend fun signIn(email: String, password: String): Result<UserProfile>
    /** Email/Password registration; bootstraps `users/{uid}` with roles ['CUSTOMER'] (rules §users). */
    suspend fun register(email: String, password: String, name: String): Result<UserProfile>
    /** Reactive current user; null while signed out. */
    fun currentUser(): Flow<UserProfile?>
    suspend fun signOut()
}

/** Stable realtime read paths for Store/Menu (design §5.2 — read screens land first). */
interface StoreRepository {
    fun stores(): Flow<List<Store>>
    fun menu(storeId: String): Flow<List<MenuItem>>
}
