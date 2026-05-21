package com.financeauto.data.repository

import com.financeauto.data.local.BookDao
import com.financeauto.data.local.entity.BookEntity
import kotlinx.coroutines.flow.Flow

class BookRepository(private val dao: BookDao) {

    val allBooks: Flow<List<BookEntity>> = dao.getAllFlow()

    fun getByCategory(category: String): Flow<List<BookEntity>> = dao.getByCategoryFlow(category)

    val allCategories: Flow<List<String>> = dao.getAllCategoriesFlow()

    suspend fun getById(id: Long): BookEntity? = dao.getById(id)

    suspend fun insert(book: BookEntity): Long = dao.insert(book)

    suspend fun update(book: BookEntity) = dao.update(book)

    suspend fun updateProgress(id: Long, page: Int) = dao.updateProgress(id, page)

    suspend fun toggleFavorite(id: Long, fav: Boolean) = dao.toggleFavorite(id, fav)

    fun getFavorites(): Flow<List<BookEntity>> = dao.getFavoritesFlow()

    suspend fun delete(id: Long) = dao.delete(id)
}
