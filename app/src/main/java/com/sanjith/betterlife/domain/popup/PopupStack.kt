package com.sanjith.betterlife.domain.popup

import com.sanjith.betterlife.domain.model.Importance
import com.sanjith.betterlife.domain.model.ReminderItemType
import java.time.Instant

/**
 * One reminder card in the pop-up. [key] identifies it in the stack (the reminder id, or a test key
 * for debug pop-ups with no [reminderId]). Times are instants; the UI formats them.
 */
public data class PopupCard(
    val key: String,
    val reminderId: String?,
    val title: String,
    val notesPreview: String?,
    val importance: Importance,
    val itemType: ReminderItemType?,
    val itemId: String?,
    val itemTime: Instant?,
    val itemAllDay: Boolean,
    val offsetMinutes: Int,
    val eventEnd: Instant?,
    val snoozeCount: Int,
    val shownAt: Instant,
)

/** All cards showing in the one pop-up window, oldest first. */
public data class PopupStackState(val cards: List<PopupCard> = emptyList()) {
    val isEmpty: Boolean get() = cards.isEmpty()

    /** The loudest level showing decides the sound. */
    val loudest: Importance? get() = cards.maxOfOrNull { it.importance }
}

public sealed interface PopupStackEvent {
    public data class Added(val card: PopupCard) : PopupStackEvent
    public data class Removed(val key: String) : PopupStackEvent
    public data class Updated(val key: String, val title: String) : PopupStackEvent
}

/**
 * Stack reducer. Several reminders at once share ONE window (context.md 5.1), so the same reminder
 * arriving again replaces its card instead of adding a second one.
 */
public fun reducePopupStack(state: PopupStackState, event: PopupStackEvent): PopupStackState = when (event) {
    is PopupStackEvent.Added -> {
        val index = state.cards.indexOfFirst { it.key == event.card.key }
        if (index < 0) {
            state.copy(cards = state.cards + event.card)
        } else {
            state.copy(cards = state.cards.toMutableList().also { it[index] = event.card })
        }
    }
    is PopupStackEvent.Removed -> state.copy(cards = state.cards.filterNot { it.key == event.key })
    is PopupStackEvent.Updated -> state.copy(
        cards = state.cards.map { if (it.key == event.key) it.copy(title = event.title) else it },
    )
}
