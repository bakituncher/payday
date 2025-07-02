package com.codenzi.payday

import com.github.mikephil.charting.data.PieEntry

data class PaydayUiState(
    val daysLeftText: String = "--",
    val daysLeftSuffix: String = "",
    val isPayday: Boolean = false,
    val savingsGoals: List<SavingsGoal> = emptyList(),
    val areGoalsVisible: Boolean = false,
    val transactions: List<Transaction> = emptyList(),
    val areTransactionsVisible: Boolean = false,
    val incomeText: String = "₺0,00",
    val expensesText: String = "₺0,00",
    val savingsText: String = "₺0,00",
    val remainingText: String = "₺0,00",
    val carryOverAmount: Long = 0L, // ÖNCEKİ 'carryOverText' BU SATIRLA DEĞİŞTİRİLDİ
    val actualRemainingAmountForGoals: Double = 0.0,
    val categorySpendingData: List<PieEntry> = emptyList()
)