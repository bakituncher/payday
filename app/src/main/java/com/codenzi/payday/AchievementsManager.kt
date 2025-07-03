package com.codenzi.payday

object AchievementsManager {

    fun getAllAchievements(): List<Achievement> {
        return listOf(
            Achievement("SETUP_COMPLETE", R.string.achievement_title_setup_complete, R.string.achievement_desc_setup_complete, false, R.drawable.ic_settings),
            Achievement("FIRST_GOAL", R.string.achievement_title_first_goal, R.string.achievement_desc_first_goal, false, R.drawable.ic_add_goal),
            Achievement("FIRST_TRANSACTION", R.string.achievement_title_first_transaction, R.string.achievement_desc_first_transaction, false, R.drawable.ic_add_expense),
            Achievement("REPORTS_VIEWED", R.string.achievement_title_reports_viewed, R.string.achievement_desc_reports_viewed, false, R.drawable.ic_reports),
            Achievement("BACKUP_HERO", R.string.achievement_title_backup_hero, R.string.achievement_desc_backup_hero, false, R.drawable.ic_google_logo),
            Achievement("STREAK_7_DAYS", R.string.achievement_title_streak_7_days, R.string.achievement_desc_streak_7_days, false, R.drawable.ic_achievement_calendar),
            Achievement("SAVER_LV1", R.string.achievement_title_saver_lv1, R.string.achievement_desc_saver_lv1, false, R.drawable.ic_achievement_money),
            Achievement("CATEGORY_EXPERT", R.string.achievement_title_category_expert, R.string.achievement_desc_category_expert, false, R.drawable.ic_category_other),
            Achievement("AUTO_PILOT", R.string.achievement_title_auto_pilot, R.string.achievement_desc_auto_pilot, false, R.drawable.autorenew),
            Achievement("PAYDAY_HYPE", R.string.achievement_title_payday_hype, R.string.achievement_desc_payday_hype, false, R.drawable.ic_achievement_payday),
            Achievement("STREAK_30_DAYS", R.string.achievement_title_streak_30_days, R.string.achievement_desc_streak_30_days, false, R.drawable.ic_achievement_calendar),
            Achievement("GOAL_COMPLETED", R.string.achievement_title_goal_completed, R.string.achievement_desc_goal_completed, false, R.drawable.ic_achievement_goal),
            Achievement("BUDGET_WIZARD", R.string.achievement_title_budget_wizard, R.string.achievement_desc_budget_wizard, false, R.drawable.ic_reports),
            Achievement("SAVER_LV2", R.string.achievement_title_saver_lv2, R.string.achievement_desc_saver_lv2, false, R.drawable.ic_achievement_money),
            Achievement("COLLECTOR", R.string.achievement_title_collector, R.string.achievement_desc_collector, false, R.drawable.inventory_2),
            Achievement("STREAK_180_DAYS", R.string.achievement_title_streak_180_days, R.string.achievement_desc_streak_180_days, false, R.drawable.ic_achievement_calendar),
            Achievement("SAVER_LV3", R.string.achievement_title_saver_lv3, R.string.achievement_desc_saver_lv3, false, R.drawable.ic_achievement_money),
            Achievement("CYCLE_CHAMPION", R.string.achievement_title_cycle_champion, R.string.achievement_desc_cycle_champion, false, R.drawable.workspace_premium),
            Achievement("DARK_SIDE", R.string.achievement_title_dark_side, R.string.achievement_desc_dark_side, false, R.drawable.nightlight),
            Achievement("LEGEND_ONE_YEAR", R.string.achievement_title_legend_one_year, R.string.achievement_desc_legend_one_year, false, R.drawable.military_tech)
        )
    }
}