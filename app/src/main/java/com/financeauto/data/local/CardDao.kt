package com.financeauto.data.local

import androidx.room.*
import com.financeauto.data.local.entity.CardEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CardDao {
    @Insert
    suspend fun insert(card: CardEntity): Long

    @Insert
    suspend fun insertAll(cards: List<CardEntity>): List<Long>

    @Update
    suspend fun update(card: CardEntity)

    @Query("UPDATE cards SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT * FROM cards WHERE status != 'DELETED' ORDER BY createdAt DESC")
    fun getAllActiveFlow(): Flow<List<CardEntity>>

    @Query("SELECT * FROM cards WHERE status != 'DELETED' ORDER BY createdAt DESC")
    suspend fun getAllActive(): List<CardEntity>

    @Query("SELECT * FROM cards WHERE cardType = :cardType AND status != 'DELETED' ORDER BY createdAt DESC")
    fun getByTypeFlow(cardType: String): Flow<List<CardEntity>>

    @Query("SELECT * FROM cards WHERE status = :status ORDER BY createdAt DESC")
    fun getByStatusFlow(status: String): Flow<List<CardEntity>>

    @Query("SELECT * FROM cards WHERE id = :id")
    suspend fun getById(id: Long): CardEntity?

    @Query("UPDATE cards SET jsonData = :jsonData, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateJsonData(id: Long, jsonData: String, updatedAt: Long = System.currentTimeMillis())
}
