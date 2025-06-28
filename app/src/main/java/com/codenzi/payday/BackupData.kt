package com.codenzi.payday

data class BackupData(
    val transactions: List<Transaction>,
    val savingsGoals: List<SavingsGoal>,
    val settings: Map<String, String?> // Ayarları esnek bir şekilde saklamak için Map
)