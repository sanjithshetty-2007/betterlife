package com.sanjith.betterlife.domain.widget

/**
 * How the habit widget arranges its boxes in the space the launcher actually gives it (Prompt 19).
 * Launchers size a "2x2" or "4x2" differently, so the widget measures instead of guessing, and it
 * uses only the rows the owner's habits need, so a few habits are not lost in an empty card.
 *
 * [columns] x [rows] boxes; [labels] puts the names (or "Habit N") under the boxes; [footer] shows
 * the "n of m done" line; [roomy] keeps the normal padding, a small widget uses the small one.
 */
public data class HabitWidgetLayout(val columns: Int, val rows: Int, val labels: Boolean, val footer: Boolean, val roomy: Boolean) {
    /** How many boxes fit, a "+N more" box included. */
    val capacity: Int get() = (columns * rows).coerceAtMost(HabitWidgetBuilder.WIDE_CAP)

    public companion object {
        private const val MAX_ROWS = 2

        /**
         * All sizes in dp: [cell] one box's touch target, [header] the header line with its gap,
         * [label] a name under a box, [footerHeight] the done line, [padding] / [smallPadding] the
         * card's inner padding, [roomyFrom] the height from which the normal padding is used.
         * [habits] is how many habits there are (boxes beyond the capacity become "+N more").
         *
         * Example (cell 48, header 28, label 16, footer 20, padding 16 / 8, roomyFrom 150): one habit
         * in 150 x 180 -> one box with its name and the done line; six habits in 330 x 170 -> one row of
         * six with names; five in 170 x 220 -> two rows of two (+N more).
         */
        public fun fit(
            width: Float,
            height: Float,
            habits: Int,
            cell: Float,
            header: Float,
            label: Float,
            footerHeight: Float,
            padding: Float,
            smallPadding: Float,
            roomyFrom: Float,
        ): HabitWidgetLayout {
            val roomy = height >= roomyFrom
            val pad = if (roomy) padding else smallPadding
            val usableWidth = width - 2 * pad
            val usableHeight = height - 2 * pad - header
            val columns = (usableWidth / cell).toInt().coerceIn(1, HabitWidgetBuilder.WIDE_CAP)
            val footer = usableHeight >= cell + footerHeight
            val forBoxes = usableHeight - if (footer) footerHeight else 0f
            val rowsThatFit = (forBoxes / cell).toInt().coerceIn(1, MAX_ROWS)
            val wanted = habits.coerceIn(1, HabitWidgetBuilder.WIDE_CAP)
            val rowsNeeded = (wanted + columns - 1) / columns
            val rows = minOf(rowsThatFit, rowsNeeded).coerceAtLeast(1)
            val labels = forBoxes >= rows * (cell + label)
            return HabitWidgetLayout(columns, rows, labels, footer, roomy)
        }
    }
}
