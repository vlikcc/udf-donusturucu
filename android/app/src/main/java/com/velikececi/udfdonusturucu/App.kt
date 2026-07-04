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

        // iOS'taki UIApplication.willEnterForegroundNotification karşılığı: uygulama ön
        // plana her geldiğinde günlük limitin gece yarısı sıfırlanıp sıfırlanmadığını kontrol eder.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                container.applicationScope.launch {
                    container.limitRepository.resetIfNewDay()
                }
            }
        })
    }
}
