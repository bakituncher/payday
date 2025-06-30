package com.codenzi.payday

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class PaydayApplication : Application() {

    // Arka plan işlemleri için uygulama genelinde bir CoroutineScope
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Repository'i lazy ile başlatma
    private val repository by lazy { PaydayRepository(this) }

    override fun onCreate() {
        super.onCreate()

        // Uygulamanın yaşam döngüsünü dinlemeye başla
        ProcessLifecycleOwner.get().lifecycle.addObserver(AppLifecycleObserver())

        // Tema ayarlarını yükle
        runBlocking {
            val theme = repository.getTheme().firstOrNull() ?: "System"
            when (theme) {
                "Light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                "Dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                "System" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
        }
    }

    /**
     * Uygulamanın ön plana ve arka plana geçişlerini dinleyen dahili bir sınıf.
     */
    private inner class AppLifecycleObserver : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) {
            // Uygulama arka plana alındığında (kullanıcı ana ekrana döndüğünde vs.)
            // akıllı yedekleme fonksiyonunu tetikle.
            applicationScope.launch {
                repository.performSmartBackup()
            }
        }
    }
}