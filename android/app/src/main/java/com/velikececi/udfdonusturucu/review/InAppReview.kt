package com.velikececi.udfdonusturucu.review

import android.app.Activity
import com.google.android.play.core.review.ReviewManagerFactory

/**
 * `SKStoreReviewController.requestReview(in:)`'in Kotlin karşılığı — Play In-App Review akışı.
 * Google'ın kotasına tabidir (kullanıcıya her istekte gerçek bir dialog gösterileceği garanti
 * değildir); bu yüzden herhangi bir başarı/başarısızlık geri bildirimi beklenmemelidir.
 */
object InAppReview {
    fun request(activity: Activity) {
        val manager = ReviewManagerFactory.create(activity)
        val request = manager.requestReviewFlow()
        request.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                manager.launchReviewFlow(activity, task.result)
            }
            // Başarısız olursa sessizce yok sayılır — iOS'un SKStoreReviewController'ı da
            // hiçbir hata geri bildirimi sunmaz.
        }
    }
}
