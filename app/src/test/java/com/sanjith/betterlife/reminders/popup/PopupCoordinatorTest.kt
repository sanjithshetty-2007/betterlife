package com.sanjith.betterlife.reminders.popup

import com.sanjith.betterlife.domain.model.Importance
import com.sanjith.betterlife.domain.model.ReminderItemType
import com.sanjith.betterlife.domain.popup.PopupCard
import com.sanjith.betterlife.domain.time.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
public class PopupCoordinatorTest {

    private val start = Instant.parse("2026-10-08T12:30:00Z")
    private val minute = 60_000L

    /** A clock that follows the test's virtual time. */
    private class VirtualClock(private val scope: TestScope, private val start: Instant) : Clock {
        override fun now(): Instant = start.plusMillis(scope.testScheduler.currentTime)
        override val zone: ZoneId = ZoneId.of("Asia/Kolkata")
    }

    private class RecordingActions : PopupActions {
        val calls = mutableListOf<String>()
        override suspend fun done(reminderId: String) { calls += "done:$reminderId" }
        override suspend fun snooze(reminderId: String, until: Instant) { calls += "snooze:$reminderId" }
        override suspend fun dismiss(reminderId: String) { calls += "dismiss:$reminderId" }
        override suspend fun reschedule(card: PopupCard) { calls += "reschedule:${card.reminderId}" }
        override suspend fun timedOut(reminderId: String, timeoutMinutes: Int) { calls += "timeout:$reminderId:$timeoutMinutes" }
    }

    private class RecordingSound(private val scope: TestScope) : SoundPlayer {
        val events = mutableListOf<String>()
        override fun play(level: Importance, volume: Float, loop: Boolean) {
            events += "${scope.testScheduler.currentTime / 1000}s play $level ${"%.1f".format(volume)}${if (loop) " loop" else ""}"
        }
        override fun stop() {
            events += "${scope.testScheduler.currentTime / 1000}s stop"
        }
        fun plays() = events.filter { "play" in it }
    }

    /** Items the watcher reports; set an entry to null to "complete" it elsewhere. */
    private val items = mutableMapOf<String, MutableStateFlow<WatchedItem?>>()

    private fun TestScope.coordinator(actions: PopupActions, sound: SoundPlayer) = PopupCoordinator(
        scope = backgroundScope,
        actions = actions,
        sound = sound,
        watcher = { card -> items.getOrPut(card.key) { MutableStateFlow(WatchedItem(card.title)) } },
        clock = VirtualClock(this, start),
        timeoutMinutes = { 5 },
    )

    private fun TestScope.card(key: String, importance: Importance = Importance.FULL_SCREEN) = PopupCard(
        key, key, "Task $key", null, importance, ReminderItemType.TASK, "item-$key", null, false, 0, null, 0,
        start.plusMillis(testScheduler.currentTime),
    )

    @Test
    public fun cardStaysUntilAnsweredAndDoneGoesToTheEngine() = runTest {
        val actions = RecordingActions()
        val c = coordinator(actions, RecordingSound(this))
        c.show(card("a"))
        runCurrent()

        c.act("a", CardAction.Done)
        runCurrent()

        assertTrue(c.state.value.isEmpty)
        assertEquals(listOf("done:a"), actions.calls)
    }

    @Test
    public fun unansweredCardTimesOutAfterFiveMinutes() = runTest {
        val actions = RecordingActions()
        val c = coordinator(actions, RecordingSound(this))
        c.show(card("a"))
        runCurrent()

        advanceTimeBy(5 * minute - 1)
        assertEquals(1, c.state.value.cards.size)
        advanceTimeBy(2)

        assertTrue(c.state.value.isEmpty)
        assertEquals(listOf("timeout:a:5"), actions.calls)
    }

    @Test
    public fun fullScreenSoundEscalatesEveryMinuteThenStops() = runTest {
        val sound = RecordingSound(this)
        val c = coordinator(RecordingActions(), sound)
        c.show(card("a"))
        runCurrent()

        advanceTimeBy(5 * minute + 1)

        assertEquals(
            listOf(
                "0s play FULL_SCREEN 0.4", "60s play FULL_SCREEN 0.6", "120s play FULL_SCREEN 0.8",
                "180s play FULL_SCREEN 1.0", "240s play FULL_SCREEN 1.0",
            ),
            sound.plays(),
        )
        assertEquals("300s stop", sound.events.last())
    }

    @Test
    public fun anyAnswerStopsTheSoundAtOnce() = runTest {
        val sound = RecordingSound(this)
        val c = coordinator(RecordingActions(), sound)
        c.show(card("a"))
        advanceTimeBy(90_000)

        c.act("a", CardAction.Snooze(start))
        runCurrent()
        advanceTimeBy(5 * minute)

        assertEquals("90s stop", sound.events.last())
        assertEquals(2, sound.plays().size)
    }

    @Test
    public fun alarmRingsInALoopAndKeepsRingingForTheAlarmLeft() = runTest {
        val sound = RecordingSound(this)
        val c = coordinator(RecordingActions(), sound)
        c.show(card("a", Importance.ALARM))
        c.show(card("b", Importance.ALARM))
        runCurrent()

        c.act("a", CardAction.Done)
        runCurrent()

        assertEquals("0s play ALARM 1.0 loop", sound.plays().last())
        assertEquals(1, c.state.value.cards.size)
    }

    @Test
    public fun severalRemindersShareOneStack() = runTest {
        val c = coordinator(RecordingActions(), RecordingSound(this))
        c.show(card("a"))
        c.show(card("b"))
        c.show(card("a")) // the same reminder again replaces, never duplicates
        runCurrent()

        assertEquals(listOf("a", "b"), c.state.value.cards.map { it.key })
    }

    @Test
    public fun itemCompletedElsewhereClosesItsCardWithoutAnAnswer() = runTest {
        val actions = RecordingActions()
        val c = coordinator(actions, RecordingSound(this))
        c.show(card("a"))
        runCurrent()

        items.getValue("a").value = null
        runCurrent()

        assertTrue(c.state.value.isEmpty)
        assertTrue(actions.calls.isEmpty())
    }

    @Test
    public fun itemRenamedElsewhereUpdatesTheCard() = runTest {
        val c = coordinator(RecordingActions(), RecordingSound(this))
        c.show(card("a"))
        runCurrent()

        items.getValue("a").value = WatchedItem("Call mom tonight")
        runCurrent()

        assertEquals("Call mom tonight", c.state.value.cards.single().title)
    }

    @Test
    public fun testCardsWithoutAReminderJustClose() = runTest {
        val actions = RecordingActions()
        val c = coordinator(actions, RecordingSound(this))
        c.show(card("t").copy(reminderId = null))
        runCurrent()

        c.act("t", CardAction.Done)
        runCurrent()

        assertTrue(c.state.value.isEmpty)
        assertTrue(actions.calls.isEmpty())
    }

    @Test
    public fun drainRemovesEverythingWithoutAnswers() = runTest {
        val actions = RecordingActions()
        val c = coordinator(actions, RecordingSound(this))
        c.show(card("a"))
        c.show(card("b"))
        runCurrent()

        assertEquals(2, c.drain().size)
        assertTrue(c.state.value.isEmpty)
        assertTrue(actions.calls.isEmpty())
    }
}
