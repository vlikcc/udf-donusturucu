package com.velikececi.udfdonusturucu.billing

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
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.velikececi.udfdonusturucu.data.LimitRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PurchaseState { IDLE, LOADING, PURCHASED, FAILED }

data class BillingUiState(
    val purchaseState: PurchaseState = PurchaseState.IDLE,
    val errorMessage: String? = null,
    val priceText: String? = null,
)

/**
 * PurchaseService.swift'in Kotlin karşılığı — StoreKit 2 yerine Play Billing Library kullanır.
 * Tek seferlik (non-consumable/INAPP) "sınırsız" ürünü satın alındığında [LimitRepository.activatePremium]
 * çağrılır; uygulama her açılışta [restorePurchases] ile mevcut satın almalar taranır (StoreKit'in
 * `Transaction.currentEntitlements` taramasının karşılığı — hem geri yükleme hem de satın alma
 * onaylamayı [handlePurchase] üzerinden kaçırmama garantisi sağlar).
 *
 * NOT: Play Billing Library sürüm-hassas bir API'dir (builder metodları minor sürümler arasında
 * değişebilir); bu dosya Android Studio dışında derlenip doğrulanamadı — ilk Gradle senkronizasyonunda
 * `PendingPurchasesParams`/`queryProductDetails` imzalarının kullanılan billing-ktx sürümüyle
 * uyuştuğunu kontrol et.
 */
class BillingManager(
    context: Context,
    private val limitRepository: LimitRepository,
    private val externalScope: CoroutineScope,
) : PurchasesUpdatedListener {

    companion object {
        const val UNLIMITED_PRODUCT_ID = "unlimited_premium"
        private const val TAG = "BillingManager"
    }

    private val _state = MutableStateFlow(BillingUiState())
    val state: StateFlow<BillingUiState> = _state

    private var productDetails: ProductDetails? = null

    private val billingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    init {
        startConnection()
    }

    private fun startConnection() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    externalScope.launch {
                        restorePurchases()
                        loadProductDetails()
                    }
                } else {
                    Log.w(TAG, "Billing kurulumu başarısız: ${result.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                // Basit yeniden bağlanma — StoreKit tarafında karşılığı yok (Apple otomatik yönetir).
                startConnection()
            }
        })
    }

    suspend fun loadProductDetails() {
        _state.update { it.copy(purchaseState = PurchaseState.LOADING) }

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(UNLIMITED_PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build(),
                ),
            )
            .build()

        val result = billingClient.queryProductDetails(params)
        val details = result.productDetailsList?.firstOrNull()
        productDetails = details

        if (details == null) {
            _state.update {
                it.copy(
                    purchaseState = PurchaseState.FAILED,
                    errorMessage = "Ürün bulunamadı. Lütfen internet bağlantınızı kontrol edip tekrar deneyin.",
                )
            }
        } else {
            val price = details.oneTimePurchaseOfferDetails?.formattedPrice
            _state.update { it.copy(purchaseState = PurchaseState.IDLE, priceText = price, errorMessage = null) }
        }
    }

    suspend fun purchase(activity: Activity) {
        val details = productDetails ?: run {
            loadProductDetails()
            productDetails
        } ?: return

        val paramsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(details)
                .build(),
        )
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(paramsList)
            .build()

        _state.update { it.copy(purchaseState = PurchaseState.LOADING) }
        billingClient.launchBillingFlow(activity, flowParams)
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { purchase -> externalScope.launch { handlePurchase(purchase) } }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                _state.update { it.copy(purchaseState = PurchaseState.IDLE) }
            }
            else -> {
                _state.update {
                    it.copy(purchaseState = PurchaseState.FAILED, errorMessage = "Satın alma başarısız: ${result.debugMessage}")
                }
            }
        }
    }

    /** Uygulama açılışında ve satın alma sonrasında çağrılır — StoreKit'in entitlement taraması karşılığı. */
    suspend fun restorePurchases(): Boolean {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val result = billingClient.queryPurchasesAsync(params)
        val purchases = result.purchasesList

        var found = false
        for (purchase in purchases) {
            if (purchase.products.contains(UNLIMITED_PRODUCT_ID) && purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                found = true
            }
            handlePurchase(purchase)
        }
        return found
    }

    private suspend fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        if (!purchase.products.contains(UNLIMITED_PRODUCT_ID)) return

        limitRepository.activatePremium()
        _state.update { it.copy(purchaseState = PurchaseState.PURCHASED, errorMessage = null) }

        // Onaylanmamış satın alma 3 gün içinde otomatik iade edilir — hem burada hem her
        // restorePurchases() taramasında acknowledge edilerek bu risk kapatılır.
        if (!purchase.isAcknowledged) {
            val ackParams = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            billingClient.acknowledgePurchase(ackParams)
        }
    }
}
