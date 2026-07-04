package com.velikececi.udfdonusturucu.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback
import com.velikececi.udfdonusturucu.BuildConfig
import com.velikececi.udfdonusturucu.data.LimitRepository
import com.velikececi.udfdonusturucu.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * AdsManager.swift'in Kotlin karşılığı. iOS `MobileAds.shared.start` + ATT izniyle başlıyordu;
 * Android'de ATT karşılığı yok, onun yerine [com.velikececi.udfdonusturucu.ads.ConsentManager]
 * ile UMP (GDPR) onayı alınıp [initialize] çağrılır.
 *
 * TODO(Faz 6): `interstitialUnitIdRelease`/`bannerUnitIdRelease` AdMob'da yeni Android uygulaması
 * oluşturulduğunda gerçek üretim ID'leriyle değiştirilecek — şu an Google'ın herkese açık Android
 * test ID'leri kullanılıyor (release derlemede bile), bu yüzden **gerçek gelir Faz 6 tamamlanana
 * kadar oluşmaz**.
 */
class AdsManager(
    private val context: Context,
    private val limitRepository: LimitRepository,
    private val settingsRepository: SettingsRepository,
    private val externalScope: CoroutineScope,
) {
    companion object {
        private const val TAG = "AdsManager"

        val BANNER_UNIT_ID = if (BuildConfig.DEBUG) {
            "ca-app-pub-3940256099942544/9214589741" // Google Android adaptive banner test ID
        } else {
            "ca-app-pub-3940256099942544/9214589741" // TODO(Faz 6): gerçek Android banner ID
        }
        val INTERSTITIAL_UNIT_ID = if (BuildConfig.DEBUG) {
            "ca-app-pub-3940256099942544/1033173712" // Google Android interstitial test ID
        } else {
            "ca-app-pub-3940256099942544/1033173712" // TODO(Faz 6): gerçek Android interstitial ID
        }
        val REWARDED_INTERSTITIAL_UNIT_ID = if (BuildConfig.DEBUG) {
            "ca-app-pub-3940256099942544/5354046379" // Google Android rewarded interstitial test ID
        } else {
            "ca-app-pub-3940256099942544/5354046379" // TODO(Faz 6): gerçek Android rewarded interstitial ID
        }
    }

    private var loadedInterstitial: InterstitialAd? = null
    private var loadedRewardedInterstitial: RewardedInterstitialAd? = null
    private var isInitialized = false

    val shouldShowAds: StateFlow<Boolean> = limitRepository.state
        .map { !it.isPremium }
        .stateIn(externalScope, SharingStarted.Eagerly, true)

    fun initialize() {
        if (isInitialized) return
        isInitialized = true
        MobileAds.initialize(context) {
            preloadInterstitial()
            preloadRewardedInterstitial()
        }
    }

    // MARK: - Interstitial

    private fun preloadInterstitial() {
        if (!shouldShowAds.value || loadedInterstitial != null) return
        InterstitialAd.load(
            context,
            INTERSTITIAL_UNIT_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    loadedInterstitial = ad
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            loadedInterstitial = null
                            preloadInterstitial()
                        }

                        override fun onAdFailedToShowFullScreenContent(error: AdError) {
                            loadedInterstitial = null
                            preloadInterstitial()
                        }
                    }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(TAG, "Interstitial yüklenemedi: ${error.message}")
                    loadedInterstitial = null
                }
            },
        )
    }

    /** `interstitialFrequency` (her 2. çağrı) eşiğine göre, hazırsa gösterir; değilse sessizce atlar. */
    suspend fun showInterstitialIfDue(activity: Activity) {
        if (!shouldShowAds.value) return
        val shouldShow = settingsRepository.shouldShowInterstitial()
        val ad = loadedInterstitial
        if (shouldShow && ad != null) {
            ad.show(activity)
        }
    }

    // MARK: - Rewarded Interstitial

    private fun preloadRewardedInterstitial() {
        if (!shouldShowAds.value || loadedRewardedInterstitial != null) return
        RewardedInterstitialAd.load(
            context,
            REWARDED_INTERSTITIAL_UNIT_ID,
            AdRequest.Builder().build(),
            object : RewardedInterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedInterstitialAd) {
                    loadedRewardedInterstitial = ad
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            loadedRewardedInterstitial = null
                            preloadRewardedInterstitial()
                        }

                        override fun onAdFailedToShowFullScreenContent(error: AdError) {
                            loadedRewardedInterstitial = null
                            preloadRewardedInterstitial()
                        }
                    }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(TAG, "Rewarded interstitial yüklenemedi: ${error.message}")
                    loadedRewardedInterstitial = null
                }
            },
        )
    }

    /**
     * Ödüllü geçiş reklamını gösterir. Kullanıcı ödülü kazanırsa [LimitRepository.addBonusConversions]
     * çağrılır ve [onReward] çalıştırılır. Reklam hazır değilse [onUnavailable] sessizce çalıştırılır
     * (iOS ile aynı davranış — kullanıcıyı hata mesajıyla rahatsız etmez).
     */
    fun showRewarded(activity: Activity, onReward: () -> Unit = {}, onUnavailable: () -> Unit = {}) {
        val ad = loadedRewardedInterstitial
        if (!shouldShowAds.value || ad == null) {
            onUnavailable()
            return
        }
        ad.show(activity) {
            externalScope.launch {
                limitRepository.addBonusConversions(1)
                onReward()
            }
        }
    }
}
