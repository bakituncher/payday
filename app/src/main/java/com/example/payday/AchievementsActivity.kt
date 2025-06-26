// Dosya: app/src/main/java/com/example/payday/AchievementsActivity.kt

package com.example.payday

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.payday.databinding.ActivityAchievementsBinding

class AchievementsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAchievementsBinding
    private lateinit var repository: PaydayRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAchievementsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repository = PaydayRepository(this)

        setupToolbar()
        setupRecyclerView()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        val allAchievements = AchievementsManager.getAllAchievements().toMutableList()
        val unlockedIds = repository.getUnlockedAchievementIds()

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