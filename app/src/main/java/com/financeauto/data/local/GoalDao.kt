package com.financeauto.data.local

import androidx.room.*
import com.financeauto.data.local.entity.GoalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalDao {
    @Insert
    suspend fun insert(goal: GoalEntity): Long

    @Update
    suspend fun update(goal: GoalEntity)

    @Query("UPDATE goals SET currentAmount = :amount WHERE id = :id")
    suspend fun updateProgress(id: Long, amount: Double)

    @Query("SELECT * FROM goals ORDER BY startDate DESC")
    fun getAllFlow(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals WHERE category = :category ORDER BY startDate DESC")
    suspend fun getByCategory(category: String): List<GoalEntity>
}
