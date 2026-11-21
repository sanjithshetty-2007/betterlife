package com.sanjith.betterlife.data.silent

import com.sanjith.betterlife.data.db.BetterLifeDatabase
import com.sanjith.betterlife.data.db.entity.SilentReminderEntity
import com.sanjith.betterlife.domain.silent.SilentReminder
import com.sanjith.betterlife.domain.silent.SilentRules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Silent reminders in Room (owner's feature, 2026-11-21). Local only. Every change re-plans the one
 * silent alarm through [replan], so an edit, a switch or a delete takes effect at once.
 */
public class SilentReminderRepository(
    private val db: BetterLifeDatabase,
    private val nowMillis: () -> Long,
    private val newId: () -> String,
    private val replan: suspend () -> Unit,
) {
    private val dao get() = db.silentReminderDao()

    public fun observeAll(): Flow<List<SilentReminder>> = dao.observeActive().map { rows -> rows.map { it.toModel() } }

    public suspend fun active(): List<SilentReminder> = dao.getActive().map { it.toModel() }

    public suspend fun get(id: String): SilentReminder? = dao.getAnyById(id)?.takeIf { it.deletedAt == null }?.toModel()

    /** Creates ([SilentReminder.id] blank) or updates a reminder; returns its id. */
    public suspend fun save(reminder: SilentReminder): String {
        val now = nowMillis()
        val existing = reminder.id.takeIf { it.isNotBlank() }?.let { dao.getAnyById(it) }
        val id = if (existing == null) {
            val id = newId()
            dao.insert(reminder.toEntity(id, createdAt = now, updatedAt = now))
            id
        } else {
            dao.update(reminder.toEntity(existing.id, createdAt = existing.createdAt, updatedAt = now))
            existing.id
        }
        replan()
        return id
    }

    public suspend fun setEnabled(id: String, enabled: Boolean) {
        dao.setEnabled(id, enabled, nowMillis())
        replan()
    }

    public suspend fun delete(id: String) {
        dao.softDelete(id, nowMillis())
        replan()
    }

    /** Undo of a delete. */
    public suspend fun restore(id: String) {
        dao.restore(id, nowMillis())
        replan()
    }

    private fun SilentReminderEntity.toModel() = SilentReminder(id, name, SilentRules.textToTimes(times), pattern, seconds, enabled)

    private fun SilentReminder.toEntity(id: String, createdAt: Long, updatedAt: Long) = SilentReminderEntity(
        id = id,
        name = name.trim(),
        times = SilentRules.timesToText(times),
        pattern = pattern,
        seconds = seconds,
        enabled = enabled,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = null,
    )
}
