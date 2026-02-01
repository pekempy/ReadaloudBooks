package com.pekempy.ReadAloudbooks.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reading_goals")
data class ReadingGoal(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val goalType: GoalType,
    val targetValue: Int, // minutes for TIME, pages for PAGES, books for BOOKS
    val periodType: PeriodType, // DAILY, WEEKLY, MONTHLY, YEARLY
    val startDate: Long,
    val endDate: Long? = null,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

enum class GoalType {
    READING_TIME, // Minutes of reading
    PAGES_READ,   // Number of pages
    BOOKS_COMPLETED // Number of books finished
}

enum class PeriodType {
    DAILY,
    WEEKLY,
    MONTHLY,
    YEARLY
}
