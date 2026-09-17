package com.velikececi.udfdonusturucu.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import com.velikececi.udfdonusturucu.data.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BillingManager(
    private val context: Context,
    private val preferencesRepository: UserPreferencesRepository
) : PurchasesUpdatedListener {

    private val scope = CoroutineScope(Dispatchers.IO)

    private val _premiumPrice = MutableStateFlow<String?>(null)
    val premiumPrice: StateFlow<String?> = _premiumPrice.asStateFlow()

    private var productDetails: ProductDetails? = null

    private val billingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases()
        .build()

    init {
        startConnection()
    }

    private fun startConnection() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryPurchases()
                    queryProductDetails()
                }
            }

            override fun onBillingServiceDisconnected() {
                // Bağlantı koptuğunda yeniden bağlanma denenebilir
            }
        })
    }

    private fun queryPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                var hasActivePremium = false
                for (purchase in purchases) {
                    if (purchase.products.contains(PRODUCT_ID_PREMIUM) &&
                        purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        hasActivePremium = true
                        if (!purchase.isAcknowledged) {
                            acknowledgePurchase(purchase)
                        }
                    }
                }
                scope.launch {
                    preferencesRepository.setPremium(hasActivePremium)
                }
            }
        }
    }

    private fun queryProductDetails() {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_ID_PREMIUM)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val details = productDetailsList.firstOrNull { it.productId == PRODUCT_ID_PREMIUM }
                productDetails = details
                details?.oneTimePurchaseOfferDetails?.formattedPrice?.let { price ->
                    _premiumPrice.value = price
                }
            }
        }
    }

    fun launchBillingFlow(activity: Activity) {
        val details = productDetails ?: return
        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(details)
                .build()
        )
        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        billingClient.launchBillingFlow(activity, billingFlowParams)
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                if (purchase.products.contains(PRODUCT_ID_PREMIUM) &&
                    purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    acknowledgePurchase(purchase)
                    scope.launch {
                        preferencesRepository.setPremium(true)
                    }
                }
            }
        }
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        billingClient.acknowledgePurchase(acknowledgePurchaseParams) { }
    }

    companion object {
        const val PRODUCT_ID_PREMIUM = "unlimited_premium"
    }
}
