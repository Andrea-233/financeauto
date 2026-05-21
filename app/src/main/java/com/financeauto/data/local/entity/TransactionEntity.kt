package com.financeauto.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amount: Double,
    val type: String,
    val category: String,
    val note: String,
    val fingerprint: String = "",
    val time: Long = System.currentTimeMillis()
)
