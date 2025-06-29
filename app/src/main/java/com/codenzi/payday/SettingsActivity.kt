package com.codenzi.payday

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.codenzi.payday.databinding.ActivitySettingsContainerBinding

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Bu aktiviteden tüm tema yükleme kodları kaldırılmıştır.
        // Tema, uygulama açılırken PaydayApplication sınıfında ayarlanır.

        val binding = ActivitySettingsContainerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }
    }
}