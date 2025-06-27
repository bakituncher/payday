// app/src/main/java/com/example/payday/TransactionDao.kt

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

    // Sadece belirli bir tarih aralığındaki harcamaları getirir.
    @Query("SELECT * FROM transactions WHERE date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    fun getTransactionsBetweenDates(startDate: Date, endDate: Date): Flow<List<Transaction>>

    // Belirli bir tarih aralığındaki toplam gideri hesaplar.
    @Query("SELECT SUM(amount) FROM transactions WHERE date BETWEEN :startDate AND :endDate")
    fun getTotalExpensesBetweenDates(startDate: Date, endDate: Date): Flow<Double?>

    // Kategoriye göre harcamaları belirli bir tarih aralığı için gruplar.
    @Query("SELECT categoryId, SUM(amount) as totalAmount FROM transactions WHERE date BETWEEN :startDate AND :endDate GROUP BY categoryId")
    fun getSpendingByCategoryBetweenDates(startDate: Date, endDate: Date): Flow<List<CategorySpending>>

    // Tekrarlanan harcama şablonlarını getirir.
    @Query("SELECT * FROM transactions WHERE isRecurringTemplate = 1")
    fun getRecurringTransactionTemplates(): Flow<List<Transaction>>

    // ----- ESKİ FONKSİYONLARI SİLİYORUZ -----
    // @Query("SELECT * FROM transactions ORDER BY date DESC")
    // fun getAllTransactions(): Flow<List<Transaction>>
    //
    // @Query("SELECT SUM(amount) as totalAmount FROM transactions")
    // fun getTotalExpenses(): Flow<Double?>
    //
    // @Query("SELECT categoryId, SUM(amount) as totalAmount FROM transactions GROUP BY categoryId")
    // fun getSpendingByCategory(): Flow<List<CategorySpending>>
}