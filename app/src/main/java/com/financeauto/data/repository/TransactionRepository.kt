package com.financeauto.data.repository

import com.financeauto.data.local.TransactionDao
import com.financeauto.data.local.entity.TransactionEntity
import com.financeauto.data.model.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TransactionRepository(
    private val dao: TransactionDao
) {
    fun getAll(): Flow<List<Transaction>> {
        return dao.getAll().map { list ->
            list.map { entity ->
                Transaction(
                    id = entity.id,
                    amount = entity.amount,
                    type = entity.type,
                    category = entity.category,
                    note = entity.note,
                    fingerprint = entity.fingerprint,
                    time = entity.time
                )
            }
        }
    }

    suspend fun insert(tx: Transaction) {
        dao.insert(tx.toEntity())
    }

    suspend fun insertAll(transactions: List<Transaction>) {
        val existingEntities = dao.getAllOnce().toMutableList()
        val existingFingerprints = existingEntities.map { it.fingerprint }.filter { it.isNotBlank() }.toMutableSet()
        val toDelete = mutableListOf<TransactionEntity>()
        val toInsert = mutableListOf<TransactionEntity>()

        for (tx in transactions) {
            var entity = tx.toEntity()
            val fingerprint = entity.fingerprint.ifBlank { entity.fallbackFingerprint() }
            entity = entity.copy(fingerprint = fingerprint)

            // Exact fingerprint match -> skip
            if (fingerprint in existingFingerprints) continue

            // Cross-channel heuristic match
            val matchIdx = existingEntities.indexOfFirst { it.isLikelySameTransaction(entity) }
            if (matchIdx >= 0) {
                val match = existingEntities[matchIdx]
                val newIsPlatform = entity.note.contains("支付宝") || entity.note.contains("微信")
                val existingIsBank = match.note.contains("银行卡")

                if (newIsPlatform && existingIsBank) {
                    // Replace: keep Alipay/WeChat, remove bank card
                    toDelete.add(match)
                    existingEntities.removeAt(matchIdx)
                    existingFingerprints.remove(match.fingerprint)
                    toInsert.add(entity)
                    existingFingerprints.add(fingerprint)
                    existingEntities.add(entity)
                }
                // If new is bank and existing is platform, skip (keep existing platform record)
                continue
            }

            toInsert.add(entity)
            existingFingerprints.add(fingerprint)
            existingEntities.add(entity)
        }

        for (e in toDelete) {
            dao.delete(e)
        }
        if (toInsert.isNotEmpty()) {
            dao.insertAll(toInsert)
        }
    }

    suspend fun update(tx: Transaction) {
        dao.update(tx.toEntity())
    }

    suspend fun delete(tx: Transaction) {
        dao.delete(tx.toEntity())
    }

    suspend fun deleteById(id: Long) {
        dao.deleteById(id)
    }

    suspend fun clear() = dao.clear()

    private fun Transaction.toEntity(): TransactionEntity {
        return TransactionEntity(
            id = id,
            amount = amount,
            type = type,
            category = category,
            note = note,
            fingerprint = fingerprint.ifBlank { fallbackFingerprint() },
            time = time
        )
    }

    private fun Transaction.fallbackFingerprint(): String {
        val minuteTime = time / 60000L
        val amountKey = "%.2f".format(java.util.Locale.US, amount)
        val noteKey = note.trim().lowercase().replace(Regex("\\s+"), "")
        return listOf(type, amountKey, minuteTime.toString(), category, noteKey).joinToString("|")
    }

    private fun TransactionEntity.fallbackFingerprint(): String {
        val minuteTime = time / 60000L
        val amountKey = "%.2f".format(java.util.Locale.US, amount)
        val noteKey = note.trim().lowercase().replace(Regex("\\s+"), "")
        return listOf(type, amountKey, minuteTime.toString(), category, noteKey).joinToString("|")
    }

    private fun TransactionEntity.isLikelySameTransaction(other: TransactionEntity): Boolean {
        val sameAmount = kotlin.math.abs(amount - other.amount) < 0.01
        val sameType = type == other.type
        val withinOneDay = kotlin.math.abs(time - other.time) <= DAY_MS
        val crossChannel = isCrossChannel(note, other.note)
        val realIncome = category in INCOME_CATEGORIES
        return sameAmount && sameType && withinOneDay && crossChannel && !realIncome
    }

    private fun isCrossChannel(note1: String, note2: String): Boolean {
        val bank1 = note1.contains("银行卡")
        val bank2 = note2.contains("银行卡")
        val platform1 = note1.contains("支付宝") || note1.contains("微信")
        val platform2 = note2.contains("支付宝") || note2.contains("微信")
        return (bank1 && platform2) || (bank2 && platform1)
    }

    private companion object {
        const val DAY_MS = 24 * 60 * 60 * 1000L
        val INCOME_CATEGORIES = setOf("退款", "转账", "工资收入", "投资收益", "收入")
    }
}
