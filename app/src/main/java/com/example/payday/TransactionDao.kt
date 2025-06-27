// Konum: app/src/main/java/com/example/payday/TransactionDao.kt

package com.example.payday

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
}