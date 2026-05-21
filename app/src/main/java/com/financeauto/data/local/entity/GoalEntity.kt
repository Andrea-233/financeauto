package com.financeauto.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String? = null,
    val targetAmount: Double? = null,
    val currentAmount: Double = 0.0,
    val frequency: String? = null,   // DAILY / WEEKLY / MONTHLY
    val startDate: Long = System.currentTimeMillis(),
    val endDate: Long? = null
)
