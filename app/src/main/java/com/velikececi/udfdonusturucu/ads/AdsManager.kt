package com.velikececi.udfdonusturucu.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback
import com.velikececi.udfdonusturucu.BuildConfig
import com.velikececi.udfdonusturucu.data.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AdsManager(
    private val context: Context,
    private val preferencesRepository: UserPreferencesRepository
) {
    private val scope = CoroutineScope(Dispatchers.Main)

    private var interstitialAd: InterstitialAd? = null
    private var rewardedInterstitialAd: RewardedInterstitialAd? = null
    private var isInitialized = false

    companion object {
        // Google Resmi Test ID'leri
        const val TEST_BANNER_ID = "ca-app-pub-3940256099942544/6300978111"
        const val TEST_INTERSTITIAL_ID = "ca-app-pub-3940256099942544/1033173712"
        const val TEST_REWARDED_INTERSTITIAL_ID = "ca-app-pub-3940256099942544/5354046379"

        // Build tipine ve FORCE_REAL_ADS bayrağına göre dinamik ID seçimi
        val BANNER_UNIT_ID: String
            get() = if (!BuildConfig.DEBUG || BuildConfig.FORCE_REAL_ADS) BuildConfig.PROD_BANNER_ID else TEST_BANNER_ID

        val INTERSTITIAL_UNIT_ID: String
            get() = if (!BuildConfig.DEBUG || BuildConfig.FORCE_REAL_ADS) BuildConfig.PROD_INTERSTITIAL_ID else TEST_INTERSTITIAL_ID

        val REWARDED_INTERSTITIAL_ID: String
            get() = if (!BuildConfig.DEBUG || BuildConfig.FORCE_REAL_ADS) BuildConfig.PROD_REWARDED_INTERSTITIAL_ID else TEST_REWARDED_INTERSTITIAL_ID
    }

    fun initialize(activity: Activity) {
        if (isInitialized) return
        MobileAds.initialize(context) {
            isInitialized = true
            loadInterstitial()
            loadRewardedInterstitial()
        }
    }

    fun loadInterstitial() {
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            context,
            INTERSTITIAL_UNIT_ID,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                }
            }
        )
    }

    fun showInterstitial(activity: Activity, onAdDismissed: () -> Unit) {
        scope.launch {
            val isPremium = preferencesRepository.isPremium.first()
            if (isPremium) {
                onAdDismissed()
                return@launch
            }

            val ad = interstitialAd
            if (ad != null) {
                ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        interstitialAd = null
                        loadInterstitial()
                        onAdDismissed()
                    }
                    override fun onAdFailedToShowFullScreenContent(error: AdError) {
                        interstitialAd = null
                        loadInterstitial()
                        onAdDismissed()
                    }
                }
                ad.show(activity)
            } else {
                loadInterstitial()
                onAdDismissed()
            }
        }
    }

    fun loadRewardedInterstitial() {
        val adRequest = AdRequest.Builder().build()
        RewardedInterstitialAd.load(
            context,
            REWARDED_INTERSTITIAL_ID,
            adRequest,
            object : RewardedInterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedInterstitialAd) {
                    rewardedInterstitialAd = ad
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedInterstitialAd = null
                }
            }
        )
    }

    fun showRewardedInterstitial(activity: Activity, onRewardEarned: () -> Unit) {
        val ad = rewardedInterstitialAd
        if (ad != null) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    rewardedInterstitialAd = null
                    loadRewardedInterstitial()
                }
                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    rewardedInterstitialAd = null
                    loadRewardedInterstitial()
                }
            }
            ad.show(activity) {
                onRewardEarned()
            }
        } else {
            loadRewardedInterstitial()
        }
    }
}
