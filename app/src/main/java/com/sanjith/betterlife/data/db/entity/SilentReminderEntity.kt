package com.sanjith.betterlife.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.sanjith.betterlife.domain.silent.VibrationPattern

/**
 * A silent reminder (owner's feature, database version 8): vibration only, every day at each of
 * [times] ("07:30,18:00", sorted), for [seconds]. Local only: never synced, never in the Reminder
 * Center (it is not part of the reminder engine).
 */
@Entity(tableName = "silent_reminders", indices = [Index("deletedAt")])
public data class SilentReminderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val times: String,
    val pattern: VibrationPattern,
    val seconds: Int,
    val enabled: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)
