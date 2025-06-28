package com.codenzi.payday

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.Date

// *** DEĞİŞİKLİK: Versiyon 3'e yükseltildi ***
@Database(entities = [Transaction::class], version = 3, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao

    companion object {
        // Eski migrasyon planı (v1 -> v2)
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE transactions ADD COLUMN categoryId INTEGER NOT NULL DEFAULT ${ExpenseCategory.OTHER.ordinal}")
            }
        }

        // *** YENİ MİGRASYON PLANI BURADA EKLENDİ (v2 -> v3) ***
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 'transactions' tablosuna 'isRecurringTemplate' adında yeni bir sütun ekle.
                // Bu sütun boolean (1 veya 0) değer alacak ve varsayılan değeri 0 (false) olacak.
                database.execSQL("ALTER TABLE transactions ADD COLUMN isRecurringTemplate INTEGER NOT NULL DEFAULT 0")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "payday_database"
                )
                    // *** DEĞİŞİKLİK: Yeni migrasyon planı eklendi ***
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class Converters {
    @androidx.room.TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @androidx.room.TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
}