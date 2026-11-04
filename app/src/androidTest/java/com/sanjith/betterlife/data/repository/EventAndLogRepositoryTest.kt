package com.sanjith.betterlife.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sanjith.betterlife.data.db.BetterLifeDatabase
import com.sanjith.betterlife.domain.model.EventType
import com.sanjith.betterlife.domain.model.Importance
import com.sanjith.betterlife.domain.model.ReasonCode
import com.sanjith.betterlife.domain.model.ReminderItemType
import com.sanjith.betterlife.domain.model.ReminderLogStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
public class EventAndLogRepositoryTest {

    private lateinit var db: BetterLifeDatabase
    private var now = 1_800_000_000_000L
    private var idCounter = 0
    private val nextId: () -> String = { "id-${++idCounter}" }

    @Before
    public fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), BetterLifeDatabase::class.java)
            .build()
    }

    @After
    public fun tearDown() {
        db.close()
    }

    @Test
    public fun birthdayKeepsItsCalendarDateAndUnknownYear() = runTest {
        val events = EventRepository(db, { now }, nextId)
        val id = events.create(
            EventDraft(title = "Asha", type = EventType.BIRTHDAY, allDay = true, startDate = LocalDate.of(2028, 2, 29)),
            listOf(ReminderDraft(1_440), ReminderDraft(0)),
        )

        val stored = events.observeById(id).first()!!
        assertEquals(LocalDate.of(2028, 2, 29), stored.startDate)
        assertNull(stored.birthYear)
        assertNull(stored.zoneId)
        assertEquals(2, db.reminderDao().observeForItem(ReminderItemType.EVENT, id).first().size)
    }

    @Test
    public fun eventSoftDeleteAndRestoreIncludeReminders() = runTest {
        val events = EventRepository(db, { now }, nextId)
        val id = events.create(
            EventDraft(title = "Asha", type = EventType.BIRTHDAY, allDay = true, startDate = LocalDate.of(2026, 3, 1)),
            listOf(ReminderDraft(0)),
        )
        now += 1_000
        events.softDelete(id)
        assertTrue(events.observeActive().first().isEmpty())

        now += 1_000
        events.restore(id)
        assertEquals(1, events.observeActive().first().size)
        assertEquals(1, db.reminderDao().observeForItem(ReminderItemType.EVENT, id).first().size)
    }

    @Test
    public fun reminderLogKeepsEveryEntryNewestFirst() = runTest {
        val log = ReminderLogRepository(db, { now }, nextId)
        log.append("r1", ReminderLogStatus.FIRED, Importance.FULL_SCREEN, firedAt = now)
        log.append("r1", ReminderLogStatus.HELD, Importance.FULL_SCREEN, ReasonCode.QUIET_HOURS, firedAt = now + 60_000)

        val entries = log.observeRecent(limit = 10).first()
        assertEquals(listOf(ReminderLogStatus.HELD, ReminderLogStatus.FIRED), entries.map { it.status })
        assertEquals(ReasonCode.QUIET_HOURS, entries.first().reasonCode)
    }
}
