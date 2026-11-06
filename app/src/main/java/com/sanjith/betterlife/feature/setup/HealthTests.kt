package com.sanjith.betterlife.feature.setup

import com.sanjith.betterlife.data.repository.ReminderDraft
import com.sanjith.betterlife.data.repository.StandaloneDraft
import com.sanjith.betterlife.data.repository.StandaloneReminderRepository
import com.sanjith.betterlife.data.setup.HealthStore
import com.sanjith.betterlife.data.setup.HealthTest
import com.sanjith.betterlife.domain.model.Importance
import com.sanjith.betterlife.domain.time.Clock
import com.sanjith.betterlife.feature.events.EventService
import java.time.Duration

/**
 * The two Reminder Health tests (Prompt 11). Each is a real "Remind me" item with one FULL_SCREEN
 * reminder at its time, so it goes through the real scheduler, alarm, engine, pipeline and pop-up.
 * Starting a test removes the previous one of the same kind (and its alarm). [realClock] is the real
 * phone time: a test must never follow the Time Machine.
 */
public class HealthTests(
    private val service: EventService,
    private val standalone: StandaloneReminderRepository,
    private val store: HealthStore,
    private val realClock: Clock,
) {

    /** "Test reminder in 10 seconds". */
    public suspend fun startQuick(title: String): HealthTest {
        val test = start(title, QUICK_DELAY, store.quickTest())
        store.saveQuickTest(test)
        return test
    }

    /** "Test after closing the app": 2 minutes ahead, so there is time to swipe the app away. */
    public suspend fun startClosedApp(title: String): HealthTest {
        val test = start(title, CLOSED_APP_DELAY, store.closedAppTestNow())
        store.saveClosedAppTest(test)
        return test
    }

    private suspend fun start(title: String, delay: Duration, previous: HealthTest?): HealthTest {
        previous?.let { service.deleteReminder(it.itemId) }
        val due = realClock.now().plus(delay)
        val itemId = service.createReminder(
            StandaloneDraft(title, due.toEpochMilli(), realClock.zone),
            listOf(ReminderDraft(0, Importance.FULL_SCREEN)),
        )
        return HealthTest(itemId, standalone.activeReminderIds(itemId).single(), due.toEpochMilli())
    }

    public companion object {
        public val QUICK_DELAY: Duration = Duration.ofSeconds(10)
        public val CLOSED_APP_DELAY: Duration = Duration.ofMinutes(2)
    }
}
