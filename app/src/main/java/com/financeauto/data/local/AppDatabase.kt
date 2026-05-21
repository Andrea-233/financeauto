package com.financeauto.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.financeauto.data.local.entity.BookEntity
import com.financeauto.data.local.entity.CardEntity
import com.financeauto.data.local.entity.GoalEntity
import com.financeauto.data.local.entity.TransactionEntity

@Database(
    entities = [TransactionEntity::class, CardEntity::class, GoalEntity::class, BookEntity::class],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao
    abstract fun cardDao(): CardDao
    abstract fun goalDao(): GoalDao
    abstract fun bookDao(): BookDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "finance_auto_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
