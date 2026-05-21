package com.financeauto.data.model

data class Transaction(
    val id: Long = 0,
    val amount: Double,
    val type: String,
    val category: String,
    val note: String = "",
    val fingerprint: String = "",
    val time: Long = System.currentTimeMillis()
)
