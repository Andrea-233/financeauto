package com.financeauto.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val author: String = "",
    val category: String = "未分类",     // 投资理财 / 经济学 / 心理学 / 小说 / 传记 / 其他
    val filePath: String,                // local file path
    val fileName: String = "",           // original filename
    val fileType: String = "pdf",        // pdf / txt / epub
    val fileSize: Long = 0,              // bytes
    val coverPath: String = "",          // optional cover image path
    val currentPage: Int = 0,            // reading progress
    val totalPages: Int = 0,
    val isFavorite: Boolean = false,
    val addedAt: Long = System.currentTimeMillis(),
    val lastReadAt: Long = 0
)
