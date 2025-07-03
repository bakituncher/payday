package com.codenzi.payday

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat // <-- EKLENDİ
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

        // Kenardan kenara görünüm için DÜZELTME
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityAchievementsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repository = PaydayRepository(this)

        setupToolbar()
        lifecycleScope.launch {
            setupRecyclerView()
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private suspend fun setupRecyclerView() {
        val allAchievements = AchievementsManager.getAllAchievements().toMutableList()
        val unlockedIds = repository.getUnlockedAchievementIds().first()

        allAchievements.forEach { achievement ->
            if (unlockedIds.contains(achievement.id)) {
                achievement.isUnlocked = true
            }
        }

        binding.achievementsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.achievementsRecyclerView.adapter = AchievementsAdapter(allAchievements)
    }
}