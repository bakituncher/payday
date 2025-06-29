package com.codenzi.payday

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking

class PaydayApplication : Application() {

    private val repository by lazy { PaydayRepository(this) }

    override fun onCreate() {
        super.onCreate()

        runBlocking {
            val theme = repository.getTheme().firstOrNull() ?: "System"
            when (theme) {
                "Light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                "Dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                "System" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
        }
    }
}
