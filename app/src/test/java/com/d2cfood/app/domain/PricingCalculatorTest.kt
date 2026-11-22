package com.d2cfood.app.domain
import org.junit.Assert.*; import org.junit.Test
class PricingCalculatorTest {
 @Test fun discountCannotExceedSubtotal() {
 // Discount clamps to the subtotal (food+topping); it is never subtracted from the shipping fee (M5).
 // subtotal=100000, requested discount=500000 -> clamped to 100000 -> food cost 0; + shipping 5000.
 val q = PricingCalculator.calculate(100000, 500000, 5000); assertEquals(5000L, q.total)
 }
 @Test fun discountNeverAppliesToShippingFee() { val q = PricingCalculator.calculate(100000, 30000, 10000); assertEquals(80000L, q.total) }
 @Test fun negativeShippingClampsToZero() { val q = PricingCalculator.calculate(10000, 0, -5); assertEquals(10000L, q.total) }
}
