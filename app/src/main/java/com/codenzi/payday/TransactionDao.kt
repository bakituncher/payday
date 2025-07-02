package com.codenzi.payday

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.util.Date

@Dao
interface TransactionDao {
    @Insert
    suspend fun insert(transaction: Transaction)

    @Update
    suspend fun update(transaction: Transaction)

    @Delete
    suspend fun delete(transaction: Transaction)

    @Query("SELECT * FROM transactions WHERE date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    fun getTransactionsBetweenDates(startDate: Date, endDate: Date): Flow<List<Transaction>>

    // DÜZELTME: Bu fonksiyonlar artık kullanılmıyor, yerlerine suspend versiyonları geldi.
    // fun getTotalExpensesBetweenDates(startDate: Date, endDate: Date, savingsCategoryId: Int): Flow<Double?>
    // fun getTotalSavingsBetweenDates(startDate: Date, endDate: Date, savingsCategoryId: Int): Flow<Double?>

    // *** YENİ: Maaş döngüsü sonu hesaplamaları için anlık veri çeken suspend fonksiyonlar ***
    @Query("SELECT SUM(amount) FROM transactions WHERE date BETWEEN :startDate AND :endDate AND categoryId != :savingsCategoryId")
    suspend fun getTotalExpensesBetweenDatesSuspend(startDate: Date, endDate: Date, savingsCategoryId: Int): Double?

    @Query("SELECT SUM(amount) FROM transactions WHERE date BETWEEN :startDate AND :endDate AND categoryId = :savingsCategoryId")
    suspend fun getTotalSavingsBetweenDatesSuspend(startDate: Date, endDate: Date, savingsCategoryId: Int): Double?


    @Query("SELECT categoryId, SUM(amount) as totalAmount FROM transactions WHERE date BETWEEN :startDate AND :endDate AND categoryId != :savingsCategoryId GROUP BY categoryId")
    fun getSpendingByCategoryBetweenDates(startDate: Date, endDate: Date, savingsCategoryId: Int): Flow<List<CategorySpending>>

    @Query("SELECT * FROM transactions WHERE isRecurringTemplate = 1 ORDER BY name ASC")
    fun getRecurringTransactionTemplates(): Flow<List<Transaction>>

    // *** YENİ: ID ile tek bir işlemi (normal veya şablon) getiren fonksiyon ***
    @Query("SELECT * FROM transactions WHERE id = :id")
    fun getTransactionById(id: Int): Flow<Transaction?>

    @Query("SELECT * FROM transactions")
    suspend fun getAllTransactions(): List<Transaction>

    @Query("DELETE FROM transactions")
    suspend fun deleteAllTransactions()

    @Query("SELECT strftime('%Y-%m-%d', date / 1000, 'unixepoch') as day, SUM(amount) as totalAmount FROM transactions WHERE date BETWEEN :startDate AND :endDate GROUP BY day ORDER BY day ASC")
    fun getDailySpendingForChart(startDate: Date, endDate: Date): Flow<List<DailySpending>>

    @Query("SELECT strftime('%Y-%m', date / 1000, 'unixepoch') as month, SUM(amount) as totalAmount FROM transactions WHERE categoryId = :categoryId GROUP BY month ORDER BY month ASC")
    fun getMonthlySpendingForCategory(categoryId: Int): Flow<List<MonthlyCategorySpending>>

    @Query("SELECT * FROM transactions")
    fun getAllTransactionsFlow(): Flow<List<Transaction>>
}

data class DailySpending(val day: String, val totalAmount: Double)
data class MonthlyCategorySpending(val month: String, val totalAmount: Double)
