package com.velikececi.udfdonusturucu.ads

import android.app.Activity
import android.util.Log
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

/**
 * iOS'taki App Tracking Transparency (ATT) izninin Android/AB karşılığı: Google User Messaging
 * Platform (UMP) ile GDPR onay akışı. Reklam SDK'sı yalnızca bu akış tamamlandıktan sonra
 * başlatılır (onay gerekmiyorsa — ör. AB dışı bölge — akış anında tamamlanır).
 */
object ConsentManager {
    private const val TAG = "ConsentManager"

    fun requestConsentAndInitializeAds(activity: Activity, adsManager: AdsManager) {
        val params = ConsentRequestParameters.Builder().build()
        val consentInformation: ConsentInformation = UserMessagingPlatform.getConsentInformation(activity)

        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (formError != null) {
                        Log.w(TAG, "Onay formu hatası: ${formError.message}")
                    }
                    // Onay verilmese/form gösterilemese de reklamlar en iyi çaba prensibiyle başlatılır
                    // (UMP SDK'sı zaten kişiselleştirilmemiş reklam moduna otomatik düşer).
                    adsManager.initialize()
                }
            },
            { requestError ->
                Log.w(TAG, "Onay bilgisi alınamadı: ${requestError.message}")
                adsManager.initialize()
            },
        )
    }
}
