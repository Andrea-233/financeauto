package com.financeauto.data.repository

import com.financeauto.data.local.CardDao
import com.financeauto.data.local.entity.CardEntity
import kotlinx.coroutines.flow.Flow

class CardRepository(private val dao: CardDao) {

    val allActiveCards: Flow<List<CardEntity>> = dao.getAllActiveFlow()

    suspend fun insert(card: CardEntity): Long = dao.insert(card)

    suspend fun insertAll(cards: List<CardEntity>): List<Long> = dao.insertAll(cards)

    suspend fun update(card: CardEntity) = dao.update(card)

    suspend fun updateStatus(id: Long, status: String) = dao.updateStatus(id, status)

    suspend fun getById(id: Long): CardEntity? = dao.getById(id)

    fun getByType(type: String): Flow<List<CardEntity>> = dao.getByTypeFlow(type)

    fun getByStatus(status: String): Flow<List<CardEntity>> = dao.getByStatusFlow(status)
}
