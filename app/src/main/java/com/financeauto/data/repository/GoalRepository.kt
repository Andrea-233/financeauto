package com.financeauto.data.repository

import com.financeauto.data.local.GoalDao
import com.financeauto.data.local.entity.GoalEntity
import kotlinx.coroutines.flow.Flow

class GoalRepository(private val dao: GoalDao) {

    val allGoals: Flow<List<GoalEntity>> = dao.getAllFlow()

    suspend fun insert(goal: GoalEntity): Long = dao.insert(goal)

    suspend fun update(goal: GoalEntity) = dao.update(goal)

    suspend fun updateProgress(id: Long, amount: Double) = dao.updateProgress(id, amount)

    suspend fun getByCategory(category: String): List<GoalEntity> = dao.getByCategory(category)
}
