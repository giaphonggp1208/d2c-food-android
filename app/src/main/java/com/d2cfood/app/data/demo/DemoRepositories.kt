package com.d2cfood.app.data.demo

import com.d2cfood.app.data.AuthRepository
import com.d2cfood.app.data.StoreRepository
import com.d2cfood.app.data.firebase.UserDto
import com.d2cfood.app.domain.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update

/** In-memory stand-ins used only when no Firebase project is configured (demo build). */
class DemoAuthRepository : AuthRepository {
    private val profile = MutableStateFlow<UserProfile?>(null)

    override suspend fun signIn(email: String, password: String): Result<UserProfile> {
        val p = UserProfile("demo-owner", name = "Chủ quán demo", email = email, roles = setOf(Role.CUSTOMER, Role.STORE_OWNER))
        profile.value = p
        return Result.success(p)
    }

    override suspend fun register(email: String, password: String, name: String): Result<UserProfile> {
        val p = UserProfile("demo-customer", name = name, email = email, roles = setOf(Role.CUSTOMER))
        profile.value = p
        return Result.success(p)
    }

    override fun currentUser(): Flow<UserProfile?> = profile

    override suspend fun signOut() { profile.value = null }
}

class DemoStoreRepository : StoreRepository {
    override fun stores(): Flow<List<Store>> = flowOf(
        listOf(Store("demo-owner-uid", "demo-owner-uid", "D2C Food Demo", "Hà Nội", "OPEN", 5000))
    )

    override fun menu(storeId: String): Flow<List<MenuItem>> = flowOf(
        listOf(
            MenuItem("bun-cha-ca", storeId, "Bún chả cá", "Cá tươi, nước dùng thanh", 45000, "bun_cha_ca"),
            MenuItem("tra-dao", storeId, "Trà đào", "Trà đào cam sả", 25000, "tra_dao"),
        )
    )
}
