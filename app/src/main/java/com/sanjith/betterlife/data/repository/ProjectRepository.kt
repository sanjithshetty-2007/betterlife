package com.sanjith.betterlife.data.repository

import com.sanjith.betterlife.data.db.BetterLifeDatabase
import com.sanjith.betterlife.data.db.dao.ProjectWithProgress
import com.sanjith.betterlife.data.db.entity.ProjectEntity
import com.sanjith.betterlife.data.db.entity.SyncState
import kotlinx.coroutines.flow.Flow

public class ProjectRepository(
    db: BetterLifeDatabase,
    private val nowMillis: () -> Long,
    private val newId: () -> String,
) {
    private val dao = db.projectDao()

    public fun observeActive(): Flow<List<ProjectEntity>> = dao.observeActive()

    public fun observeWithProgress(): Flow<List<ProjectWithProgress>> = dao.observeWithProgress()

    public suspend fun create(name: String): String {
        require(name.isNotBlank()) { "A project needs a name" }
        val now = nowMillis()
        val id = newId()
        dao.insert(
            ProjectEntity(
                id = id,
                name = name.trim(),
                colorHex = null,
                archived = false,
                sortOrder = 0,
                createdAt = now,
                updatedAt = now,
                deletedAt = null,
                syncState = SyncState.DIRTY,
                serverUpdatedAt = null,
            ),
        )
        return id
    }

    /** Quick-add's "@name": the existing project with that name (any letter case), or a new one. */
    public suspend fun findOrCreate(name: String): String = dao.findActiveByName(name.trim())?.id ?: create(name)
}
