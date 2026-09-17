package com.velikececi.udfdonusturucu.ads

import android.app.Activity
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

class ConsentManager(private val activity: Activity) {

    private val consentInformation: ConsentInformation =
        UserMessagingPlatform.getConsentInformation(activity)

    fun requestConsent(onConsentReady: () -> Unit) {
        val params = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
            .build()

        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (consentInformation.canRequestAds()) {
                        onConsentReady()
                    }
                }
            },
            { requestConsentError ->
                if (consentInformation.canRequestAds()) {
                    onConsentReady()
                }
            }
        )

        if (consentInformation.canRequestAds()) {
            onConsentReady()
        }
    }

    val isPrivacyOptionsRequired: Boolean
        get() = consentInformation.privacyOptionsRequirementStatus ==
                ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    fun showPrivacyOptionsForm(onDismissed: () -> Unit) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) {
            onDismissed()
        }
    }
}
