package com.velikececi.udfdonusturucu.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.velikececi.udfdonusturucu.ads.AdsManager

@Composable
fun AdBanner(
    isPremium: Boolean,
    modifier: Modifier = Modifier
) {
    if (isPremium) return

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = AdsManager.BANNER_UNIT_ID
                loadAd(AdRequest.Builder().build())
            }
        }
    )
}
