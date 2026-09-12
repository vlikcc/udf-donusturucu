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
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

enum class PurchaseState { IDLE, LOADING, PURCHASED, FAILED }

/** PaywallView.swift'teki `EN AVANTAJLI` / `TEK SEFER` rozetlerinin karşılığı. */
enum class PlanBadge { EN_AVANTAJLI, TEK_SEFER }

/**
 * Paywall'da gösterilen tek bir planın çözümlenmiş (Play Billing → UI) hâli.
 * [offerToken] yalnızca abonelikler için doludur; ömür boyu (INAPP) üründe `null` olmalı ve
 * satın alma akışında ASLA `setOfferToken` ile gönderilmemelidir.
 */
data class PaywallPlan(
    val productId: String,
    val offerToken: String?,
    val isSubscription: Boolean,
    val title: String,
    val subtitle: String,
    val formattedPrice: String,
    val priceAmountMicros: Long,
    val currencyCode: String,
    val periodSuffix: String?,
    val badge: PlanBadge?,
    val savingsPercent: Int?,
    val strikethroughPrice: String?,
)

data class BillingUiState(
    val purchaseState: PurchaseState = PurchaseState.IDLE,
    val errorMessage: String? = null,
    val plans: List<PaywallPlan> = emptyList(),
    val selectedProductId: String? = null,
)

/**
 * PurchaseService.swift'in Kotlin karşılığı — StoreKit 2 yerine Play Billing Library kullanır.
 * iOS'un 3 planlı yapısıyla (Aylık/Yıllık abonelik + Ömür Boyu tek seferlik ürün) birebir eşleşir.
 *
 * SUBS ve INAPP tipleri Play Billing'de **ayrı ayrı** sorgulanır — tek bir
 * `QueryProductDetailsParams` içinde karıştırmak `DEVELOPER_ERROR` ile sonuçlanır.
 *
 * [refreshEntitlements] hem uygulama açılışında hem her foreground'da (bkz. App.kt) çağrılır —
 * StoreKit'in `Transaction.currentEntitlements` taramasının karşılığı. Sorgu hata dönerse premium
 * durumu **asla düşürülmez**: bir bağlantı sorununun ödeme yapmış bir kullanıcının erişimini
 * yanlışlıkla alması engellenir.
 */
class BillingManager(
    context: Context,
    private val limitRepository: LimitRepository,
    private val externalScope: CoroutineScope,
) : PurchasesUpdatedListener {

    companion object {
        private const val TAG = "BillingManager"
        private val YEARLY_REFERENCE_PRICE = BigDecimal("599.99")
    }

    private val _state = MutableStateFlow(BillingUiState())
    val state: StateFlow<BillingUiState> = _state

    private var productDetailsById: Map<String, ProductDetails> = emptyMap()

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
                        refreshEntitlements()
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

    private suspend fun queryType(type: String, ids: List<String>): List<ProductDetails> {
        if (ids.isEmpty()) return emptyList()
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                ids.map { id ->
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(id)
                        .setProductType(type)
                        .build()
                },
            )
            .build()
        return billingClient.queryProductDetails(params).productDetailsList.orEmpty()
    }

    suspend fun loadProductDetails() {
        _state.update { it.copy(purchaseState = PurchaseState.LOADING) }

        val subs = queryType(BillingClient.ProductType.SUBS, BillingProducts.SUBSCRIPTION_IDS)
        val inapp = queryType(BillingClient.ProductType.INAPP, BillingProducts.ONE_TIME_IDS)
        val all = subs + inapp
        productDetailsById = all.associateBy { it.productId }

        val plans = all
            .filter { it.productId in BillingProducts.PAYWALL_IDS }
            .mapNotNull(::toPaywallPlan)
            .sortedBy(::planPriority)

        if (plans.isEmpty()) {
            _state.update {
                it.copy(
                    purchaseState = PurchaseState.FAILED,
                    errorMessage = "Ürün bulunamadı. Lütfen internet bağlantınızı kontrol edip tekrar deneyin.",
                    plans = emptyList(),
                )
            }
        } else {
            _state.update { current ->
                val selected = current.selectedProductId?.takeIf { id -> plans.any { it.productId == id } }
                    ?: plans.firstOrNull { it.productId == BillingProducts.YEARLY }?.productId
                    ?: plans.first().productId
                current.copy(
                    purchaseState = PurchaseState.IDLE,
                    errorMessage = null,
                    plans = plans,
                    selectedProductId = selected,
                )
            }
        }
    }

    /** Paywall'daki plan kartına dokunulduğunda çağrılır. */
    fun selectPlan(productId: String) {
        _state.update { it.copy(selectedProductId = productId) }
    }

    private fun planPriority(plan: PaywallPlan) = when (plan.productId) {
        BillingProducts.MONTHLY -> 0
        BillingProducts.YEARLY -> 1
        BillingProducts.LIFETIME -> 2
        else -> 3
    }

    private fun toPaywallPlan(details: ProductDetails): PaywallPlan? {
        return if (details.productType == BillingClient.ProductType.INAPP) {
            val offer = details.oneTimePurchaseOfferDetails ?: return null
            PaywallPlan(
                productId = details.productId,
                offerToken = null,
                isSubscription = false,
                title = "Ömür Boyu",
                subtitle = "Tek ödeme — abonelik yok, kalıcı erişim",
                formattedPrice = offer.formattedPrice,
                priceAmountMicros = offer.priceAmountMicros,
                currencyCode = offer.priceCurrencyCode,
                periodSuffix = null,
                badge = PlanBadge.TEK_SEFER,
                savingsPercent = null,
                strikethroughPrice = null,
            )
        } else {
            val basePlanId = when (details.productId) {
                BillingProducts.MONTHLY -> BillingProducts.MONTHLY_BASE_PLAN_ID
                BillingProducts.YEARLY -> BillingProducts.YEARLY_BASE_PLAN_ID
                else -> null
            }
            val offerDetails = details.subscriptionOfferDetails.orEmpty()
            // Süslü/kampanyalı teklifler yerine düz temel plan teklifi tercih edilir (offerId == null).
            val offer = offerDetails.firstOrNull { it.basePlanId == basePlanId && it.offerId == null }
                ?: offerDetails.firstOrNull { it.basePlanId == basePlanId }
                ?: offerDetails.firstOrNull()
                ?: return null
            // İlk aşamalar deneme/indirim olabilir (priceAmountMicros == 0 veya finite recurrence);
            // gösterilecek "asıl" fiyat her zaman son (yinelenen) aşamadır.
            val recurringPhase = offer.pricingPhases.pricingPhaseList.lastOrNull() ?: return null

            val isYearly = details.productId == BillingProducts.YEARLY
            var savingsPercent: Int? = null
            var strikethroughPrice: String? = null
            if (isYearly && recurringPhase.priceCurrencyCode == "TRY") {
                val price = BigDecimal(recurringPhase.priceAmountMicros).divide(BigDecimal(1_000_000))
                if (price < YEARLY_REFERENCE_PRICE) {
                    val percent = YEARLY_REFERENCE_PRICE.subtract(price)
                        .divide(YEARLY_REFERENCE_PRICE, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal(100))
                    savingsPercent = percent.toInt()
                    val currencyFormat = NumberFormat.getCurrencyInstance(Locale("tr", "TR")).apply {
                        currency = Currency.getInstance("TRY")
                    }
                    strikethroughPrice = currencyFormat.format(YEARLY_REFERENCE_PRICE)
                }
            }

            PaywallPlan(
                productId = details.productId,
                offerToken = offer.offerToken,
                isSubscription = true,
                title = if (isYearly) "Yıllık Pro" else "Aylık Pro",
                subtitle = if (isYearly) {
                    "12 aylık pakete göre tasarruf — yıllık yenilenir"
                } else {
                    "Esnek kullanım — istediğiniz zaman iptal"
                },
                formattedPrice = recurringPhase.formattedPrice,
                priceAmountMicros = recurringPhase.priceAmountMicros,
                currencyCode = recurringPhase.priceCurrencyCode,
                periodSuffix = billingPeriodSuffix(recurringPhase.billingPeriod),
                badge = if (isYearly) PlanBadge.EN_AVANTAJLI else null,
                savingsPercent = savingsPercent,
                strikethroughPrice = strikethroughPrice,
            )
        }
    }

    private fun billingPeriodSuffix(isoPeriod: String): String = when (isoPeriod) {
        "P1M" -> "/ ay"
        "P1Y" -> "/ yıl"
        "P1W" -> "/ hafta"
        else -> ""
    }

    suspend fun purchase(activity: Activity, productId: String) {
        var details = productDetailsById[productId]
        if (details == null) {
            loadProductDetails()
            details = productDetailsById[productId] ?: return
        }
        val plan = _state.value.plans.firstOrNull { it.productId == productId }

        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .apply { plan?.offerToken?.let { setOfferToken(it) } }
            .build()

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
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

    /**
     * Uygulama açılışında ve her foreground'da (bkz. App.kt `ProcessLifecycleOwner`) çağrılır.
     * Hem SUBS hem INAPP taranır; sorgulardan biri hata dönerse mevcut premium durumu **korunur**
     * (asla düşürülmez) — yalnızca ikisi de başarılıysa ve hiçbir geçerli satın alma bulunamazsa
     * `restorePremiumStatus(false)` çağrılır.
     */
    suspend fun refreshEntitlements(): Boolean {
        val subsResult = billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build(),
        )
        val inappResult = billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),
        )

        if (subsResult.billingResult.responseCode != BillingClient.BillingResponseCode.OK ||
            inappResult.billingResult.responseCode != BillingClient.BillingResponseCode.OK
        ) {
            return limitRepository.state.value.isPremium
        }

        val purchases = subsResult.purchasesList + inappResult.purchasesList
        purchases.forEach { purchase -> handlePurchase(purchase) }

        val found = purchases.any { purchase ->
            purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
                purchase.products.any { it in BillingProducts.ALL_ENTITLEMENT_IDS }
        }
        limitRepository.restorePremiumStatus(found)
        return found
    }

    /** Ayarlar ekranındaki "Satın Almaları Geri Yükle" butonundan çağrılır. */
    suspend fun restorePurchases(): Boolean = refreshEntitlements()

    private suspend fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        if (purchase.products.none { it in BillingProducts.ALL_ENTITLEMENT_IDS }) return

        limitRepository.activatePremium()
        _state.update { it.copy(purchaseState = PurchaseState.PURCHASED, errorMessage = null) }

        // Onaylanmamış satın alma 3 gün içinde otomatik iade edilir — hem burada hem her
        // refreshEntitlements() taramasında acknowledge edilerek bu risk kapatılır.
        if (!purchase.isAcknowledged) {
            val ackParams = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            billingClient.acknowledgePurchase(ackParams)
        }
    }
}
