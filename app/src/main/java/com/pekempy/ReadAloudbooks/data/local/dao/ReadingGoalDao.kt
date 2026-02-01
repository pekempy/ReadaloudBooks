package com.pekempy.ReadAloudbooks.data.local.dao

import androidx.room.*
import com.pekempy.ReadAloudbooks.data.local.entities.ReadingGoal
import com.pekempy.ReadAloudbooks.data.local.entities.GoalType
import com.pekempy.ReadAloudbooks.data.local.entities.PeriodType
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingGoalDao {
    @Query("SELECT * FROM reading_goals WHERE isActive = 1 ORDER BY createdAt DESC")
    fun getActiveGoals(): Flow<List<ReadingGoal>>

    @Query("SELECT * FROM reading_goals WHERE id = :id")
    suspend fun getGoalById(id: Long): ReadingGoal?

    @Query("SELECT * FROM reading_goals WHERE goalType = :goalType AND periodType = :periodType AND isActive = 1 LIMIT 1")
    suspend fun getGoalByTypeAndPeriod(goalType: GoalType, periodType: PeriodType): ReadingGoal?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoal(goal: ReadingGoal): Long

    @Update
    suspend fun updateGoal(goal: ReadingGoal)

    @Delete
    suspend fun deleteGoal(goal: ReadingGoal)

    @Query("UPDATE reading_goals SET isActive = 0 WHERE id = :id")
    suspend fun deactivateGoal(id: Long)

    @Query("SELECT * FROM reading_goals ORDER BY createdAt DESC")
    fun getAllGoals(): Flow<List<ReadingGoal>>
}
