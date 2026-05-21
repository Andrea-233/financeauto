package com.financeauto.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.financeauto.data.local.entity.BookEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY lastReadAt DESC, addedAt DESC")
    fun getAllFlow(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE category = :category ORDER BY lastReadAt DESC, addedAt DESC")
    fun getByCategoryFlow(category: String): Flow<List<BookEntity>>

    @Query("SELECT DISTINCT category FROM books ORDER BY category")
    fun getAllCategoriesFlow(): Flow<List<String>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getById(id: Long): BookEntity?

    @Insert
    suspend fun insert(book: BookEntity): Long

    @Update
    suspend fun update(book: BookEntity)

    @Query("UPDATE books SET currentPage = :page, lastReadAt = :time WHERE id = :id")
    suspend fun updateProgress(id: Long, page: Int, time: Long = System.currentTimeMillis())

    @Query("UPDATE books SET isFavorite = :fav WHERE id = :id")
    suspend fun toggleFavorite(id: Long, fav: Boolean)

    @Query("SELECT * FROM books WHERE isFavorite = 1 ORDER BY lastReadAt DESC")
    fun getFavoritesFlow(): Flow<List<BookEntity>>

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun delete(id: Long)
}
