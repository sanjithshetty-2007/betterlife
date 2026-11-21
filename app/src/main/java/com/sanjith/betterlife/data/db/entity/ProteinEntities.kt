package com.sanjith.betterlife.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * One day of protein (owner's feature, database version 6). At most one row per [localDate]: saving
 * again updates the row. [targetGrams] is the target that applied that day, so changing the target
 * later never rewrites the past. Local only: never synced (like body data, context.md 11a).
 */
@Entity(tableName = "protein_days", indices = [Index(value = ["localDate"], unique = true)])
public data class ProteinDayEntity(
    @PrimaryKey val id: String,
    val localDate: LocalDate,
    val grams: Int,
    val targetGrams: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)
