package com.example.payday

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Insert
    suspend fun insert(transaction: Transaction)

    @Update
    suspend fun update(transaction: Transaction)

    @Delete
    suspend fun delete(transaction: Transaction)

    @Query("SELECT * FROM transactions ORDER BY date DESC")
    fun getAllTransactions(): Flow<List<Transaction>>

    @Query("SELECT SUM(amount) as totalAmount FROM transactions")
    fun getTotalExpenses(): Flow<Double?>

    @Query("SELECT categoryId, SUM(amount) as totalAmount FROM transactions GROUP BY categoryId")
    fun getSpendingByCategory(): Flow<List<CategorySpending>>
}
