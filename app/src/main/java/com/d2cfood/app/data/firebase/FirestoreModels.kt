package com.d2cfood.app.data.firebase

import com.d2cfood.app.domain.*

/**
 * Firestore access pattern (design §7.1): fixed Store ID = owner UID.
 *
 *   users/{uid}                              — profile, roles (client only ever creates CUSTOMER)
 *   stores/{ownerUid}                        — store doc, ownerId immutable after create
 *   stores/{ownerUid}/categories/{id}        — menu groups
 *   stores/{ownerUid}/menuItems/{id}         — items (imageAsset = bundled asset name, D9)
 *   stores/{ownerUid}/menuItems/{id}/optionGroups/{id} — customization
 *   stores/{ownerUid}/vouchers/{id}          — voucher + usedCount/lastRedemptionOrderId
 *   users/{uid}/cartItems/{id}              — per-user cart snapshot
 *   orders/{orderId} (+ /lines/{lineId})     — order, money fields locked after create
 *   ratings/{orderId}                        — one per order, owner-only, create-only
 *
 * Mapping to domain models is intentionally thin: denormalised, flat fields (RK7) so Security
 * Rules stay shallow. Money is stored as Long (VND, integer) — never Double.
 */

data class UserDto(
    val uid: String,
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val roles: List<Role> = listOf(Role.CUSTOMER),
) {
    fun toDomain() = UserProfile(uid, name, email, phone, roles.toSet())
}

data class StoreDto(
    val ownerId: String,
    val name: String,
    val address: String,
    val status: String = StoreStatus.OPEN.name,
    val fallbackPerKmFee: Long = 0,
) {
    fun toDomain(id: String) =
        Store(id = id, ownerId = ownerId, name = name, address = address, status = status, fallbackPerKmFee = fallbackPerKmFee)
}

enum class StoreStatus { OPEN, BUSY, CLOSED }

data class MenuItemDto(
    val storeId: String,
    val name: String,
    val description: String = "",
    val imageAsset: String = "",
    val basePrice: Long,
    val available: Boolean = true,
) {
    fun toDomain(id: String) = MenuItem(id, storeId, name, description, basePrice, imageAsset, available)
}
