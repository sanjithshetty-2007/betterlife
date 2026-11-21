package com.sanjith.betterlife.feature.tracker

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.sanjith.betterlife.R
import com.sanjith.betterlife.domain.tracker.TrackerKind
import com.sanjith.betterlife.domain.tracker.TrackerRules

/**
 * The words one tracker uses. "Latest" is the newest loggable day (today for protein, last night for
 * sleep); "earlier" is the one before it (yesterday, the night before).
 */
internal data class TrackerTexts(
    @param:StringRes val title: Int,
    @param:StringRes val avatar: Int,
    @param:StringRes val question: Int,
    @param:StringRes val save: Int,
    @param:StringRes val latestHeading: Int,
    @param:StringRes val left: Int,
    @param:StringRes val reached: Int,
    @param:StringRes val logLatest: Int,
    @param:StringRes val editLatest: Int,
    @param:StringRes val addEarlier: Int,
    @param:StringRes val editEarlier: Int,
    @param:StringRes val dialogLatest: Int,
    @param:StringRes val dialogEarlier: Int,
    @param:StringRes val total: Int,
    @param:StringRes val average: Int,
    @param:PluralsRes val goalDays: Int,
    @param:StringRes val targetLabel: Int,
    @param:StringRes val setupTitle: Int,
    @param:StringRes val setupText: Int,
    @param:StringRes val cardSetup: Int,
    @param:StringRes val refusedOld: Int,
    @param:StringRes val progress: Int,
    @param:PluralsRes val streak: Int,
    @param:StringRes val goalAndReminder: Int,
)

private val PROTEIN_TEXTS = TrackerTexts(
    title = R.string.protein_title,
    avatar = R.string.protein_avatar,
    question = R.string.protein_popup_question,
    save = R.string.protein_save_intake,
    latestHeading = R.string.protein_today,
    left = R.string.protein_left,
    reached = R.string.protein_goal_reached,
    logLatest = R.string.protein_log_today,
    editLatest = R.string.protein_edit_today,
    addEarlier = R.string.protein_add_yesterday,
    editEarlier = R.string.protein_edit_yesterday,
    dialogLatest = R.string.protein_dialog_today,
    dialogEarlier = R.string.protein_dialog_yesterday,
    total = R.string.protein_stat_total,
    average = R.string.protein_average,
    goalDays = R.plurals.protein_goal_days,
    targetLabel = R.string.protein_daily_target,
    setupTitle = R.string.protein_setup_title,
    setupText = R.string.protein_setup_text,
    cardSetup = R.string.protein_card_setup,
    refusedOld = R.string.protein_refused_old,
    progress = R.string.protein_progress_description,
    streak = R.plurals.protein_streak_days,
    goalAndReminder = R.string.tracker_goal_and_reminder,
)

private val SLEEP_TEXTS = TrackerTexts(
    title = R.string.sleep_title,
    avatar = R.string.sleep_avatar,
    question = R.string.sleep_popup_question,
    save = R.string.sleep_save,
    latestHeading = R.string.sleep_last_night,
    left = R.string.sleep_short,
    reached = R.string.sleep_reached,
    logLatest = R.string.sleep_log_last_night,
    editLatest = R.string.sleep_edit_last_night,
    addEarlier = R.string.sleep_add_night_before,
    editEarlier = R.string.sleep_edit_night_before,
    dialogLatest = R.string.sleep_dialog_last_night,
    dialogEarlier = R.string.sleep_dialog_night_before,
    total = R.string.sleep_stat_total,
    average = R.string.sleep_average,
    goalDays = R.plurals.sleep_goal_nights,
    targetLabel = R.string.sleep_target,
    setupTitle = R.string.sleep_setup_title,
    setupText = R.string.sleep_setup_text,
    cardSetup = R.string.sleep_card_setup,
    refusedOld = R.string.sleep_refused_old,
    progress = R.string.sleep_progress_description,
    streak = R.plurals.sleep_streak_nights,
    goalAndReminder = R.string.sleep_target_and_reminder,
)

internal fun textsFor(kind: TrackerKind): TrackerTexts = when (kind) {
    TrackerKind.PROTEIN -> PROTEIN_TEXTS
    TrackerKind.SLEEP -> SLEEP_TEXTS
}

/** An amount as the owner reads it: "120 g", or "7 h 30 m" / "8 h" / "45 m" of sleep. */
@Composable
internal fun amountText(kind: TrackerKind, amount: Int): String = when (kind) {
    TrackerKind.PROTEIN -> stringResource(R.string.protein_grams_value, amount)
    TrackerKind.SLEEP -> {
        val hours = amount / TrackerRules.MINUTES_PER_HOUR
        val minutes = amount % TrackerRules.MINUTES_PER_HOUR
        when {
            minutes == 0 -> stringResource(R.string.sleep_hours_value, hours)
            hours == 0 -> stringResource(R.string.sleep_minutes_value, minutes)
            else -> stringResource(R.string.sleep_duration_value, hours, minutes)
        }
    }
}
