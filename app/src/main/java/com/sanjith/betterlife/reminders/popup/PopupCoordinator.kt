package com.sanjith.betterlife.reminders.popup

import com.sanjith.betterlife.domain.model.Importance
import com.sanjith.betterlife.domain.popup.Escalation
import com.sanjith.betterlife.domain.popup.PopupCard
import com.sanjith.betterlife.domain.popup.PopupStackEvent
import com.sanjith.betterlife.domain.popup.PopupStackState
import com.sanjith.betterlife.domain.popup.reducePopupStack
import com.sanjith.betterlife.domain.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

/** What the owner did on a card. */
public sealed interface CardAction {
    public data object Done : CardAction
    public data class Snooze(val until: Instant) : CardAction
    public data object Reschedule : CardAction
    public data object Dismiss : CardAction
}

/** Where card answers go (the reminder engine, in the app). */
public interface PopupActions {
    public suspend fun done(reminderId: String)
    public suspend fun snooze(reminderId: String, until: Instant)
    public suspend fun dismiss(reminderId: String)
    public suspend fun reschedule(card: PopupCard)
    public suspend fun timedOut(reminderId: String, timeoutMinutes: Int)
}

public interface SoundPlayer {
    public fun play(level: Importance, volume: Float, loop: Boolean)
    public fun stop()
}

/** The live state of a card's item; null once it is completed or deleted elsewhere. */
public data class WatchedItem(val title: String)

public fun interface ItemWatcher {
    public fun watch(card: PopupCard): Flow<WatchedItem?>
}

/**
 * Owns the one pop-up stack for the whole app: cards, their timeouts, the escalating sound, and
 * closing a card when its item is completed or deleted somewhere else. The overlay window and the
 * lock-screen activity only draw [state] and send [act]. No Android code, so it is tested on the PC.
 * [clock] must be the real clock.
 */
public class PopupCoordinator(
    private val scope: CoroutineScope,
    private val actions: PopupActions,
    private val sound: SoundPlayer,
    private val watcher: ItemWatcher,
    private val clock: Clock,
    private val timeoutMinutes: suspend () -> Int,
) {
    private val _state = MutableStateFlow(PopupStackState())
    public val state: StateFlow<PopupStackState> = _state

    private val cardJobs = mutableMapOf<String, Job>()
    private var soundJob: Job? = null

    // Every public call hops onto [scope] (a single thread in the app), so the job map and the stack
    // never change from two threads at once.

    /** Adds (or refreshes) a card and starts its timeout, item watch and sound. */
    public fun show(card: PopupCard) {
        scope.launch { showNow(card) }
    }

    /** A button or gesture on a card. Sound stops at once (context.md 5.1). */
    public fun act(key: String, action: CardAction) {
        scope.launch { actNow(key, action) }
    }

    /** Removes every card without answering any (nothing can be shown); returns what was showing. */
    public suspend fun drain(): List<PopupCard> {
        val cards = _state.value.cards
        cards.forEach { remove(it.key) }
        return cards
    }

    private fun showNow(card: PopupCard) {
        cardJobs.remove(card.key)?.cancel()
        _state.update { reducePopupStack(it, PopupStackEvent.Added(card)) }
        cardJobs[card.key] = scope.launch {
            launch { runTimeout(card) }
            launch { watchItem(card) }
        }
        restartSound(skipPastSteps = false)
    }

    private suspend fun actNow(key: String, action: CardAction) {
        val card = _state.value.cards.firstOrNull { it.key == key } ?: return
        remove(key)
        val reminderId = card.reminderId ?: return // debug test cards have nothing to update
        when (action) {
            CardAction.Done -> actions.done(reminderId)
            is CardAction.Snooze -> actions.snooze(reminderId, action.until)
            CardAction.Reschedule -> actions.reschedule(card)
            CardAction.Dismiss -> actions.dismiss(reminderId)
        }
    }

    private suspend fun runTimeout(card: PopupCard) {
        val minutes = timeoutMinutes()
        delay(Duration.ofMinutes(minutes.toLong()).toMillis())
        remove(card.key)
        card.reminderId?.let { actions.timedOut(it, minutes) }
    }

    private suspend fun watchItem(card: PopupCard) {
        watcher.watch(card).collect { item ->
            when {
                item == null -> remove(card.key)
                item.title != card.title -> _state.update { reducePopupStack(it, PopupStackEvent.Updated(card.key, item.title)) }
            }
        }
    }

    private fun remove(key: String) {
        _state.update { reducePopupStack(it, PopupStackEvent.Removed(key)) }
        cardJobs.remove(key)?.cancel()
        restartSound(skipPastSteps = true)
    }

    /**
     * Plays the escalation of the loudest card still showing, timed from when that card appeared.
     * A newly shown card sounds at once (the current step). After an answer the sound stops at once
     * and comes back only at the next step, except a looping ALARM ring, which resumes immediately
     * for the alarm cards that are left.
     */
    private fun restartSound(skipPastSteps: Boolean) {
        soundJob?.cancel()
        sound.stop()
        val current = _state.value
        val level = current.loudest ?: return
        val shownAt = current.cards.filter { it.importance == level }.minOf { it.shownAt }
        soundJob = scope.launch {
            val steps = Escalation.steps(level, Duration.ofMinutes(timeoutMinutes().toLong()))
            val elapsed = Duration.between(shownAt, clock.now())
            val currentStep = steps.lastOrNull { it.at <= elapsed }
            val upcoming = steps.filter { it.at > elapsed }
            val playNow = currentStep?.takeIf { !skipPastSteps || it.loop }
            for (step in listOfNotNull(playNow) + upcoming) {
                val wait = step.at.minus(Duration.between(shownAt, clock.now()))
                if (!wait.isNegative && !wait.isZero) delay(wait.toMillis())
                sound.play(level, step.volume, step.loop)
            }
        }
    }
}
