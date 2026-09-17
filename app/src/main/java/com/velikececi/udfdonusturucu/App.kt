package com.velikececi.udfdonusturucu

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.velikececi.udfdonusturucu.di.AppContainer
import kotlinx.coroutines.launch

class App : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // iOS'taki UIApplication.willEnterForegroundNotification karşılığı: uygulama ön plana
        // her geldiğinde (a) günlük limitin gece yarısı sıfırlanıp sıfırlanmadığı kontrol edilir,
        // (b) PurchaseService'in foreground entitlement taramasıyla aynı şekilde abonelik/ömür
        // boyu satın alma durumu yeniden doğrulanır (süresi dolan bir abonelik burada düşürülür).
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                container.applicationScope.launch {
                    container.limitRepository.resetIfNewDay()
                    container.billingManager.refreshEntitlements()
                }
            }
        })
    }
}
