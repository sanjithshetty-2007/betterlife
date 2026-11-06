package com.sanjith.betterlife.data.repository

import com.sanjith.betterlife.data.db.BetterLifeDatabase
import com.sanjith.betterlife.data.db.entity.CelebrationMessageEntity
import com.sanjith.betterlife.domain.mascot.CelebrationLine
import kotlinx.coroutines.flow.Flow

/** The owner's own celebration lines (Prompt 16). Local only, never synced. */
public class CelebrationMessageRepository(
    private val db: BetterLifeDatabase,
    private val nowMillis: () -> Long,
    private val newId: () -> String,
) {
    private val dao = db.celebrationMessageDao()

    public fun observeAll(): Flow<List<CelebrationMessageEntity>> = dao.observeActive()

    public suspend fun lines(): List<CelebrationLine> = dao.getActive().map { CelebrationLine(it.text, it.milestone) }

    public suspend fun add(text: String, milestone: Int?) {
        val now = nowMillis()
        dao.insert(CelebrationMessageEntity(newId(), text.trim(), milestone, now, now, null))
    }

    public suspend fun update(id: String, text: String, milestone: Int?) {
        dao.update(id, text.trim(), milestone, nowMillis())
    }

    public suspend fun delete(id: String) {
        dao.softDelete(id, nowMillis())
    }
}
