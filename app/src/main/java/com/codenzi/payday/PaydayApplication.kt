package com.codenzi.payday

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PaydayApplication : Application() {

    // Repository'i uygulama seviyesinde oluşturuyoruz
    private val repository by lazy { PaydayRepository(this) }

    // Uygulama seviyesinde bir coroutine scope'u
    private val applicationScope = CoroutineScope(Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()

        // Uygulama ilk açıldığında kaydedilmiş temayı yükle
        // Bu, arayüzü kilitlemeden arka planda çalışır.
        applicationScope.launch {
            val theme = repository.getTheme().first()
            when (theme) {
                "Light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                "Dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                "System" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
        }
    }
}