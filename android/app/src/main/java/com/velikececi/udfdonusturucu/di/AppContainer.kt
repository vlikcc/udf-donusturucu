package com.velikececi.udfdonusturucu.di

import android.content.Context
import com.velikececi.udfdonusturucu.ads.AdsManager
import com.velikececi.udfdonusturucu.billing.BillingManager
import com.velikececi.udfdonusturucu.data.ConversionRepository
import com.velikececi.udfdonusturucu.data.HistoryRepository
import com.velikececi.udfdonusturucu.data.LimitRepository
import com.velikececi.udfdonusturucu.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/**
 * Uygulama genelinde tekil (singleton) bağımlılıkların elle (Hilt'siz) tutulduğu kap.
 * Bu proje boyutunda (~5K satır hedef) bir DI çerçevesi gereksiz karmaşıklık katar.
 */
class AppContainer(context: Context) {
    val applicationScope = CoroutineScope(SupervisorJob())

    val settingsRepository = SettingsRepository(context)
    val limitRepository = LimitRepository(context, applicationScope)
    val historyRepository = HistoryRepository(context, applicationScope)
    val conversionRepository = ConversionRepository(context, limitRepository, historyRepository)

    val billingManager = BillingManager(context, limitRepository, applicationScope)
    val adsManager = AdsManager(context, limitRepository, settingsRepository, applicationScope)
}
