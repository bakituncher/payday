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

class PaydayApplication : Application() {

    // Arka plan işlemleri için uygulama genelinde bir CoroutineScope
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Repository'i lazy ile başlatma
    private val repository by lazy { PaydayRepository(this) }

    override fun onCreate() {
        super.onCreate()

        // Uygulamanın yaşam döngüsünü dinlemeye başla
        ProcessLifecycleOwner.get().lifecycle.addObserver(AppLifecycleObserver())

        // --- DÜZELTME: Tema ayarlarını ana iş parçacığını bloklamadan yükle ---
        // runBlocking kaldırıldı, yerine asenkron bir coroutine başlatıldı.
        applicationScope.launch {
            val theme = repository.getTheme().firstOrNull() ?: "System"
            // Tema değişikliğini ana iş parçacığına geri gönder
            launch(Dispatchers.Main) {
                when (theme) {
                    "Light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                    "Dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                    "System" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                }
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