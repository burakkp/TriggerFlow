package com.triggerflow

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BillingManager(
    private val context: Context,
    private val preferencesManager: PreferencesManager,
    private val promoCodeManager: PromoCodeManager
) {

    private val _isPremium = MutableStateFlow(false)
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                handlePurchase(purchase)
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Log.d(TAG, "User canceled purchase")
        } else {
            Log.e(TAG, "Purchase error: ${billingResult.debugMessage}")
        }
    }

    private val billingClient = BillingClient.newBuilder(context)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .build()

    init {
        updatePremiumState()
        startConnection()
    }

    private fun startConnection() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Billing Setup Finished")
                    queryPurchases()
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.d(TAG, "Billing Service Disconnected. Retrying...")
            }
        })
    }
    
    fun queryProductDetails(productIds: List<String>, onResult: (List<ProductDetails>) -> Unit) {
        val productList = productIds.map { productId ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(
                    if (productId == LIFETIME) BillingClient.ProductType.INAPP 
                    else BillingClient.ProductType.SUBS
                )
                .build()
        }

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                onResult(productDetailsList)
            } else {
                Log.e(TAG, "Error querying products: ${billingResult.debugMessage}")
                onResult(emptyList())
            }
        }
    }

    fun launchBillingFlow(activity: Activity, productDetails: ProductDetails, offerToken: String?) {
        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .apply {
                    if (offerToken != null) {
                        setOfferToken(offerToken)
                    }
                }
                .build()
        )

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        billingClient.launchBillingFlow(activity, billingFlowParams)
    }

    private fun queryPurchases() {
        val subsParams = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        billingClient.queryPurchasesAsync(subsParams) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                processPurchases(purchases)
            }
        }

        val inappParams = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        billingClient.queryPurchasesAsync(inappParams) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                processPurchases(purchases)
            }
        }
    }

    private fun processPurchases(purchases: List<Purchase>) {
        var hasActivePurchase = false
        for (purchase in purchases) {
            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                hasActivePurchase = true
                if (!purchase.isAcknowledged) {
                    handlePurchase(purchase)
                }
            }
        }
        
        if (hasActivePurchase) {
             preferencesManager.isPremium = true
        }
        updatePremiumState()
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            if (!purchase.isAcknowledged) {
                val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        Log.d(TAG, "Purchase acknowledged")
                        preferencesManager.isPremium = true
                        updatePremiumState()
                    }
                }
            } else {
                 preferencesManager.isPremium = true
                 updatePremiumState()
            }
        }
    }
    
    fun updatePremiumState() {
        val isLocalPremium = preferencesManager.isPremium
        val isPromoPremium = promoCodeManager.isPromoBypassActive()
        
        _isPremium.value = isLocalPremium || isPromoPremium
    }

    companion object {
        private const val TAG = "BillingManager"
        
        const val SUB_MONTHLY = "triggerflow_premium_monthly"
        const val SUB_YEARLY = "triggerflow_premium_yearly"
        const val LIFETIME = "triggerflow_premium_lifetime"
    }
}
