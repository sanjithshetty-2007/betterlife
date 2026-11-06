package com.sanjith.betterlife.reminders.engine

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sanjith.betterlife.data.db.BetterLifeDatabase
import com.sanjith.betterlife.data.repository.HabitDraft
import com.sanjith.betterlife.data.repository.HabitRepository
import com.sanjith.betterlife.domain.delivery.ItemStatus
import com.sanjith.betterlife.domain.model.ReminderItemType
import com.sanjith.betterlife.domain.recurrence.RecurrenceRule
import com.sanjith.betterlife.domain.time.FixedClock
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** A habit's daily reminder through the existing engine store (Prompt 15). */
@RunWith(AndroidJUnit4::class)
public class HabitReminderStoreTest {

    private lateinit var db: BetterLifeDatabase
    private lateinit var habits: HabitRepository
    private val clock = FixedClock.of("2026-11-20T18:00", "Asia/Kolkata")
    private var settled = false
    private val ticked = mutableListOf<String>()
    private var idCounter = 0

    private val hooks = object : HabitReminderHooks {
        override suspend fun settled(habitId: String, at: Instant): Boolean = settled
        override suspend fun tickFromReminder(habitId: String) {
            ticked += habitId
        }
    }

    @Before
    public fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), BetterLifeDatabase::class.java).build()
        habits = HabitRepository(db, { 1_000L }, { "00000000-0000-4000-8000-%012d".format(++idCounter) })
    }

    @After
    public fun tearDown() {
        db.close()
    }

    @Test
    public fun aReminderTimeMakesOneDailyReminderAndChangingItReplacesIt() = runTest {
        val store = RoomReminderStore(db, clock, habits = hooks)
        val id = habits.create(HabitDraft("Creatine", "#FF6B5E", reminderTime = LocalTime.of(21, 30)), LocalDate.of(2026, 11, 1))

        val target = store.loadTargets().single()
        assertEquals(RecurrenceRule.Daily(), target.recurrence)
        assertEquals(0, target.offsetMinutes)

        habits.update(habits.getAnyById(id)!!.copy(reminderTime = null))
        assertTrue(store.loadTargets().isEmpty())
        habits.update(habits.getAnyById(id)!!.copy(reminderTime = LocalTime.of(8, 0)))
        assertEquals(1, store.loadTargets().size)
        // Renaming alone keeps the same reminder row (a snooze in progress survives).
        val reminderId = store.loadTargets().single().reminderId
        habits.update(habits.getAnyById(id)!!.copy(name = "Creatine 5 g"))
        assertEquals(reminderId, store.loadTargets().single().reminderId)
    }

    @Test
    public fun aSettledDayIsDroppedAndDoneOnThePopUpTicksTheHabit() = runTest {
        val store = RoomReminderStore(db, clock, habits = hooks)
        val id = habits.create(HabitDraft("Meditation", "#7E8CFF", reminderTime = LocalTime.of(21, 30)), LocalDate.of(2026, 11, 1))
        val reminderId = store.loadTargets().single().reminderId

        assertEquals(ItemStatus.ACTIVE, store.fireContext(reminderId)!!.itemStatus)
        assertEquals(ReminderItemType.HABIT, store.fireContext(reminderId)!!.itemType)
        settled = true
        assertEquals(ItemStatus.COMPLETED, store.fireContext(reminderId)!!.itemStatus)

        store.completeTask(reminderId)
        assertEquals(listOf(id), ticked)
    }

    @Test
    public fun archivedOrDeletedHabitsStopReminding() = runTest {
        val store = RoomReminderStore(db, clock, habits = hooks)
        val id = habits.create(HabitDraft("Skincare", "#FF8FB8", reminderTime = LocalTime.of(22, 0)), LocalDate.of(2026, 11, 1))
        habits.setArchived(id, true)
        assertTrue(store.loadTargets().single().itemDone)
        habits.setArchived(id, false)
        habits.softDelete(id)
        assertTrue(store.loadTargets().isEmpty())
        habits.restore(id)
        assertEquals(1, store.loadTargets().size)
    }
}
