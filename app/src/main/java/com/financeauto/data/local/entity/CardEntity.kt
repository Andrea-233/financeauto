package com.financeauto.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cards")
data class CardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardType: String,         // DECISION / INSIGHT / CHECKIN
    val title: String,
    val description: String = "",
    val sourceMessage: String = "", // Original user message that triggered this card
    val status: String = "ACTIVE",  // ACTIVE / COMPLETED / DISMISSED
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val jsonData: String = ""       // Type-specific fields as JSON
)
