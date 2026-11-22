package com.d2cfood.app.domain

enum class Role { CUSTOMER, STORE_OWNER }
enum class MenuItemType { SINGLE_REQUIRED, MULTI_OPTIONAL }
data class UserProfile(val uid:String, val name:String="", val email:String="", val phone:String="", val roles:Set<Role> = setOf(Role.CUSTOMER))
data class Store(val id:String, val ownerId:String, val name:String, val address:String, val status:String="OPEN", val fallbackPerKmFee:Long=0)
data class MenuItem(val id:String, val storeId:String, val name:String, val description:String, val basePrice:Long, val imageAsset:String="", val available:Boolean=true)
data class OrderQuote(val subtotal:Long, val discountAmount:Long, val shippingFee:Long) { val total:Long get() = subtotal - discountAmount + shippingFee }
object PricingCalculator { fun calculate(subtotal:Long, discount:Long, shipping:Long) = OrderQuote(subtotal, discount.coerceIn(0, subtotal), shipping.coerceAtLeast(0)) }
