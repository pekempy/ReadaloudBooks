package com.pekempy.ReadAloudbooks.data.repository

import com.pekempy.ReadAloudbooks.data.local.dao.BookCollectionDao
import com.pekempy.ReadAloudbooks.data.local.entities.BookCollection
import com.pekempy.ReadAloudbooks.data.local.entities.BookCollectionBook
import kotlinx.coroutines.flow.Flow

class CollectionRepository(private val collectionDao: BookCollectionDao) {

    fun getAllCollections(): Flow<List<BookCollection>> {
        return collectionDao.getAllBookCollections()
    }

    fun getCollectionById(id: Long): Flow<BookCollection?> {
        return collectionDao.getBookCollectionByIdFlow(id)
    }

    suspend fun createCollection(collection: BookCollection): Long {
        return collectionDao.insertBookCollection(collection)
    }

    suspend fun updateCollection(collection: BookCollection) {
        collectionDao.updateBookCollection(collection)
    }

    suspend fun deleteCollection(collection: BookCollection) {
        collectionDao.deleteBookCollection(collection)
    }

    suspend fun addBookToCollection(collectionId: Long, bookId: String) {
        val collectionBook = BookCollectionBook(collectionId, bookId)
        collectionDao.insertBookCollectionBook(collectionBook)
    }

    suspend fun removeBookFromCollection(collectionId: Long, bookId: String) {
        val collectionBook = BookCollectionBook(collectionId, bookId)
        collectionDao.deleteBookCollectionBook(collectionBook)
    }

    fun getBooksInCollection(collectionId: Long): Flow<List<String>> {
        return collectionDao.getBooksInBookCollection(collectionId)
    }

    fun getCollectionsForBook(bookId: String): Flow<List<Long>> {
        return collectionDao.getBookCollectionsForBook(bookId)
    }

    suspend fun isBookInCollection(collectionId: Long, bookId: String): Boolean {
        return collectionDao.isBookInBookCollection(collectionId, bookId)
    }

    suspend fun getBookCountInCollection(collectionId: Long): Int {
        return collectionDao.getBookCountInBookCollection(collectionId)
    }
}
