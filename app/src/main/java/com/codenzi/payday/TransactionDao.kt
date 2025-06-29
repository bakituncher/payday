// Konum: app/src/main/java/com/codenzi/payday/TransactionDao.kt

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

    @Query("SELECT SUM(amount) FROM transactions WHERE date BETWEEN :startDate AND :endDate")
    fun getTotalExpensesBetweenDates(startDate: Date, endDate: Date): Flow<Double?>

    @Query("SELECT categoryId, SUM(amount) as totalAmount FROM transactions WHERE date BETWEEN :startDate AND :endDate GROUP BY categoryId")
    fun getSpendingByCategoryBetweenDates(startDate: Date, endDate: Date): Flow<List<CategorySpending>>

    @Query("SELECT * FROM transactions WHERE isRecurringTemplate = 1")
    fun getRecurringTransactionTemplates(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions")
    suspend fun getAllTransactions(): List<Transaction>

    @Query("DELETE FROM transactions")
    suspend fun deleteAllTransactions()

    // --- YENİ EKLENEN FONKSİYONLAR ---
    @Query("SELECT strftime('%Y-%m-%d', date / 1000, 'unixepoch') as day, SUM(amount) as totalAmount FROM transactions WHERE date BETWEEN :startDate AND :endDate GROUP BY day ORDER BY day ASC")
    fun getDailySpendingForChart(startDate: Date, endDate: Date): Flow<List<DailySpending>>

    @Query("SELECT strftime('%Y-%m', date / 1000, 'unixepoch') as month, SUM(amount) as totalAmount FROM transactions WHERE categoryId = :categoryId GROUP BY month ORDER BY month ASC")
    fun getMonthlySpendingForCategory(categoryId: Int): Flow<List<MonthlyCategorySpending>>
}

// --- YENİ EKLENEN VERİ SINIFLARI ---
data class DailySpending(val day: String, val totalAmount: Double)
data class MonthlyCategorySpending(val month: String, val totalAmount: Double)