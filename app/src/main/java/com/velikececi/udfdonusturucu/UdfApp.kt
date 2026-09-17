package com.velikececi.udfdonusturucu

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.velikececi.udfdonusturucu.ads.AdsManager
import com.velikececi.udfdonusturucu.billing.BillingManager
import com.velikececi.udfdonusturucu.data.AppDatabase
import com.velikececi.udfdonusturucu.data.UserPreferencesRepository

class UdfApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var preferencesRepository: UserPreferencesRepository
        private set

    lateinit var billingManager: BillingManager
        private set

    lateinit var adsManager: AdsManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        PDFBoxResourceLoader.init(applicationContext)

        database = AppDatabase.getDatabase(this)
        preferencesRepository = UserPreferencesRepository(this)
        billingManager = BillingManager(this, preferencesRepository)
        adsManager = AdsManager(this, preferencesRepository)
    }

    companion object {
        lateinit var instance: UdfApp
            private set
    }
}
