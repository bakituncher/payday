package com.codenzi.payday

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.codenzi.payday.databinding.ActivityAchievementsBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AchievementsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAchievementsBinding
    private lateinit var repository: PaydayRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAchievementsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repository = PaydayRepository(this)

        setupToolbar()
        // RecyclerView'ı bir coroutine içinde kur
        lifecycleScope.launch {
            setupRecyclerView()
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    // Bu fonksiyon artık suspend olabilir veya içinde coroutine başlatabilir.
    private suspend fun setupRecyclerView() {
        val allAchievements = AchievementsManager.getAllAchievements().toMutableList()
        // Flow'dan kilitli ID listesini asenkron olarak al
        val unlockedIds = repository.getUnlockedAchievementIds().first()

        // Başarımların kilit durumunu güncelle
        allAchievements.forEach { achievement ->
            if (unlockedIds.contains(achievement.id)) {
                achievement.isUnlocked = true
            }
        }

        binding.achievementsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.achievementsRecyclerView.adapter = AchievementsAdapter(allAchievements)
    }
}