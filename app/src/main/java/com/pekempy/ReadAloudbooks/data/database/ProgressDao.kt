package com.pekempy.ReadAloudbooks.data.database

import androidx.room.*

@Entity(tableName = "progress")
data class ProgressEntity(
    @PrimaryKey val bookId: String,
    val progressJson: String,
    val lastUpdated: Long = System.currentTimeMillis()
)

@Dao
interface ProgressDao {
    @Query("SELECT * FROM progress")
    suspend fun getAllProgress(): List<ProgressEntity>

    @Query("SELECT * FROM progress WHERE bookId = :bookId")
    suspend fun getProgressForBook(bookId: String): ProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProgress(progress: ProgressEntity)

    @Update
    suspend fun updateProgress(progress: ProgressEntity)

    @Query("DELETE FROM progress WHERE bookId = :bookId")
    suspend fun deleteProgress(bookId: String)
}
