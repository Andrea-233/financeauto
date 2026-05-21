package com.financeauto.ui.cards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financeauto.data.local.entity.CardEntity
import com.financeauto.data.repository.CardRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CardsViewModel(
    private val repository: CardRepository
) : ViewModel() {

    val cards: StateFlow<List<CardEntity>> = repository.allActiveCards
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addCard(card: CardEntity) {
        viewModelScope.launch { repository.insert(card) }
    }

    fun addCards(cards: List<CardEntity>) {
        viewModelScope.launch { repository.insertAll(cards) }
    }

    fun completeCard(id: Long) {
        viewModelScope.launch { repository.updateStatus(id, "COMPLETED") }
    }

    fun dismissCard(id: Long) {
        viewModelScope.launch { repository.updateStatus(id, "DISMISSED") }
    }

    fun deleteCard(id: Long) {
        viewModelScope.launch { repository.updateStatus(id, "DELETED") }
    }
}
