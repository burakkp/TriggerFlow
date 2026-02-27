package com.triggerflow

import javax.inject.Inject
import javax.inject.Singleton

class PromoCodeManager(private val preferencesManager: PreferencesManager) {

    fun redeemCode(code: String): RedeemResult {
        val cleanCode = code.trim().lowercase()

        // 1. Check for Bypass Codes
        if (BYPASS_CODES.contains(cleanCode)) {
            preferencesManager.setPromoBypass(true)
            return RedeemResult.SuccessBypass
        }

        // 2. Check for Discount Codes
        val discountOffer = DISCOUNT_CODES[cleanCode]
        if (discountOffer != null) {
            return RedeemResult.SuccessDiscount(discountOffer)
        }

        return RedeemResult.Invalid
    }

    fun isPromoBypassActive(): Boolean {
        return preferencesManager.isPromoBypassActive
    }

    sealed class RedeemResult {
        object SuccessBypass : RedeemResult()
        data class SuccessDiscount(val offerId: String) : RedeemResult()
        object Invalid : RedeemResult()
    }

    companion object {
        // In a real app, these might be hashed or validated against a backend
        private val BYPASS_CODES = setOf(
            "family",
            "friends",
            "dev_team"
        )

        // Mapping code -> Offer ID (configured in Play Console)
        private val DISCOUNT_CODES = mapOf(
            "discount10" to "offer_10_percent_off",
            "student" to "offer_student_deal"
        )
    }
}
