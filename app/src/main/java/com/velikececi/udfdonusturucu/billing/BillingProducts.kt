package com.velikececi.udfdonusturucu.billing

/**
 * PurchaseService.swift'teki ürün kimlikleri ve plan sıralamasının Kotlin karşılığı. Play
 * Console'da bu id'lerle (abonelikler için ilgili temel plan kimlikleriyle: `monthly` / `yearly`)
 * ürünler oluşturulmalı — aksi halde `queryProductDetails` boş döner ve paywall
 * "Ürün bulunamadı" hatası gösterir.
 */
object BillingProducts {
    const val MONTHLY = "com.evrakdonus.pro.month"
    const val YEARLY = "com.evrakdonus.pro.yearly"
    const val LIFETIME = "com.evrakdonus.pro.unlimited"

    const val MONTHLY_BASE_PLAN_ID = "monthly"
    const val YEARLY_BASE_PLAN_ID = "yearly"

    // Geriye dönük uyumluluk: eski satın almalar hâlâ premium hakkı verir ama paywall'da
    // gösterilmez — iOS `allProductIDs` (entitlement taraması) / `paywallProductIDs` (satılan
    // ürünler) ayrımıyla aynı.
    const val LEGACY_UNLIMITED = "com.evrakdonus.unlimited"
    const val LEGACY_WEEKLY = "com.evrakdonus.pro.weekly"

    val SUBSCRIPTION_IDS = listOf(MONTHLY, YEARLY, LEGACY_WEEKLY)
    val ONE_TIME_IDS = listOf(LIFETIME, LEGACY_UNLIMITED)
    val ALL_ENTITLEMENT_IDS = (SUBSCRIPTION_IDS + ONE_TIME_IDS).toSet()
    val PAYWALL_IDS = setOf(MONTHLY, YEARLY, LIFETIME)
}
