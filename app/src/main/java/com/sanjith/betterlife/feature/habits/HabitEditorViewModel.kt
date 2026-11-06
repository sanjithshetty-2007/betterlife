package com.sanjith.betterlife.feature.habits

import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sanjith.betterlife.BetterLifeApp
import com.sanjith.betterlife.data.repository.HabitDraft
import com.sanjith.betterlife.data.repository.HabitRepository
import com.sanjith.betterlife.domain.habit.HabitCategory
import com.sanjith.betterlife.ui.theme.BetterLifeColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalTime

/** The emoji a habit can wear (docs/design habit editor), the default one first. */
public object HabitAvatars {
    public const val DEFAULT: String = "⭐"
    public val ALL: List<String> = listOf(
        "📖", "💊", "🧘", "🧴", "💇", "🏃",
        "💧", "🎸", "🥗", "😴", "✍️", DEFAULT,
    )
}

/** Everything the habit editor shows. [id] null = a new habit. */
public data class HabitEditorState(
    val id: String? = null,
    val name: String = "",
    val colorHex: String = paletteHex(0),
    val avatar: String = HabitAvatars.ALL.first(),
    val category: HabitCategory = HabitCategory.OTHER,
    val essential: Boolean = false,
    val reminderTime: LocalTime? = null,
    val archived: Boolean = false,
) {
    val canSave: Boolean get() = name.isNotBlank()
}

/** #RRGGBB of palette swatch [index] (the habit colours are the shared palette, Prompt 15). */
internal fun paletteHex(index: Int): String = "#%06X".format(BetterLifeColors.CategoryPalette[index].toArgb() and RGB_MASK)

private const val RGB_MASK = 0xFFFFFF

/** Create or edit a habit: name, colour, icon, category, Essential, daily reminder, archive, delete. */
public class HabitEditorViewModel(
    private val habitId: String?,
    private val habits: HabitRepository,
    private val service: HabitService,
) : ViewModel() {

    private val _state = MutableStateFlow(HabitEditorState())
    public val state: StateFlow<HabitEditorState> = _state
    public val finished: MutableStateFlow<Boolean> = MutableStateFlow(false)

    init {
        if (habitId != null) {
            viewModelScope.launch {
                val habit = habits.getAnyById(habitId) ?: return@launch
                _state.value = HabitEditorState(
                    id = habit.id, name = habit.name, colorHex = habit.colorHex, avatar = habit.avatar ?: HabitAvatars.DEFAULT,
                    category = habit.category, essential = habit.essential, reminderTime = habit.reminderTime, archived = habit.archived,
                )
            }
        }
    }

    public fun edit(change: (HabitEditorState) -> HabitEditorState) {
        _state.value = change(_state.value)
    }

    public fun save() {
        val s = _state.value
        if (!s.canSave) return
        viewModelScope.launch {
            if (s.id == null) {
                service.create(HabitDraft(s.name, s.colorHex, s.avatar, s.category, s.essential, s.reminderTime))
            } else {
                val habit = habits.getAnyById(s.id) ?: return@launch
                service.update(
                    habit.copy(
                        name = s.name, colorHex = s.colorHex, avatar = s.avatar, category = s.category,
                        essential = s.essential, reminderTime = s.reminderTime,
                    ),
                )
            }
            finished.value = true
        }
    }

    /** Archive hides the habit from the list and stops its reminder; Unarchive brings both back. */
    public fun toggleArchived() {
        val id = _state.value.id ?: return
        viewModelScope.launch {
            service.setArchived(id, !_state.value.archived)
            finished.value = true
        }
    }

    /** To Trash (soft delete) with its reminder. */
    public fun delete() {
        val id = _state.value.id ?: return
        viewModelScope.launch {
            service.delete(id)
            finished.value = true
        }
    }

    public companion object {
        public const val ARG_ID: String = "habitId"
        public const val NEW: String = "new"

        public val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BetterLifeApp).container
                val id = createSavedStateHandle().get<String>(ARG_ID)?.takeIf { it != NEW }
                HabitEditorViewModel(id, c.habitRepository, c.habitService)
            }
        }
    }
}
