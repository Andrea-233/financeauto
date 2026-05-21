package com.financeauto.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financeauto.data.model.Transaction
import com.financeauto.data.repository.TransactionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TransactionViewModel(
    private val repository: TransactionRepository
) : ViewModel() {

    val transactions: StateFlow<List<Transaction>> = repository.getAll()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

    fun add(tx: Transaction) {
        viewModelScope.launch {
            repository.insert(tx)
        }
    }

    fun addAll(transactions: List<Transaction>) {
        viewModelScope.launch {
            repository.insertAll(transactions)
        }
    }

    fun update(tx: Transaction) {
        viewModelScope.launch {
            repository.update(tx)
        }
    }

    fun delete(tx: Transaction) {
        viewModelScope.launch {
            repository.delete(tx)
        }
    }

    fun deleteById(id: Long) {
        viewModelScope.launch {
            repository.deleteById(id)
        }
    }

    fun clear() {
        viewModelScope.launch {
            repository.clear()
        }
    }
}
