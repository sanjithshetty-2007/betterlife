package com.sanjith.betterlife.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.sanjith.betterlife.domain.habit.HabitCategory
import com.sanjith.betterlife.domain.habit.HabitLogStatus
import com.sanjith.betterlife.domain.habit.PauseReason
import java.time.LocalDate
import java.time.LocalTime

// Habit tables (Prompt 14, database version 5). habits, habit_logs and pause_ranges are synced in S3,
// so they carry syncState and serverUpdatedAt now. The milestone ledger and celebration messages
// stay on the phone (context.md 11a).

@Entity(
    tableName = "habits",
    indices = [Index("deletedAt"), Index("syncState")],
)
public data class HabitEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** The habit's own colour as #RRGGBB text, chosen by the owner. */
    val colorHex: String,
    /** An emoji, or null for the default icon. */
    val avatar: String?,
    val category: HabitCategory,
    val essential: Boolean,
    val archived: Boolean,
    /** Optional daily reminder time (wall clock), stored as text such as 07:30. */
    val reminderTime: LocalTime?,
    val sortOrder: Int,
    /** The first habit day; days before it are greyed and left out of consistency. */
    val startDate: LocalDate,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    @ColumnInfo(defaultValue = "DIRTY") val syncState: SyncState,
    val serverUpdatedAt: Long?,
)

/**
 * One habit on one habit day. At most one row per (habit, day): an untick soft-deletes the row and
 * a new tick brings the same row back, so sync sees one logical row (S3).
 */
@Entity(
    tableName = "habit_logs",
    indices = [Index(value = ["habitId", "localDate"], unique = true), Index("localDate"), Index("syncState")],
)
public data class HabitLogEntity(
    @PrimaryKey val id: String,
    val habitId: String,
    val localDate: LocalDate,
    val status: HabitLogStatus,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    @ColumnInfo(defaultValue = "DIRTY") val syncState: SyncState,
    val serverUpdatedAt: Long?,
)

/** Which milestones each streak has already reached, so each is celebrated once. Local only. */
@Entity(
    tableName = "milestone_ledger",
    indices = [Index(value = ["habitId", "streakStartDate", "milestone"], unique = true)],
)
public data class MilestoneLedgerEntity(
    @PrimaryKey val id: String,
    val habitId: String,
    val streakStartDate: LocalDate,
    val milestone: Int,
    /** True when it was a banner only (backfill, or passed along with a higher one). */
    val quiet: Boolean,
    val celebratedAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)

/** Pause / vacation / sick mode for all habits, both dates included. [endDate] null = until ended. */
@Entity(
    tableName = "pause_ranges",
    indices = [Index("deletedAt"), Index("syncState")],
)
public data class PauseRangeEntity(
    @PrimaryKey val id: String,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val reason: PauseReason,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    @ColumnInfo(defaultValue = "DIRTY") val syncState: SyncState,
    val serverUpdatedAt: Long?,
)

/** The owner's own celebration lines. [milestone] null = used for any milestone. Local only. */
@Entity(tableName = "celebration_messages", indices = [Index("deletedAt")])
public data class CelebrationMessageEntity(
    @PrimaryKey val id: String,
    val text: String,
    val milestone: Int?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)
