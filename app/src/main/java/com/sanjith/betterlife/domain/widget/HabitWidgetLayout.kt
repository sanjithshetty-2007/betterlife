package com.sanjith.betterlife.domain.widget

/**
 * How the habit widget arranges its boxes in the space the launcher actually gives it (Prompt 19).
 * Launchers size a "2x2" or "4x2" differently, so the widget measures instead of guessing.
 *
 * [columns] x [rows] boxes; [labels] adds the names (or "Habit N") under one row of boxes and the
 * done line at the bottom; [roomy] keeps the normal padding, a small widget uses the small one.
 */
public data class HabitWidgetLayout(val columns: Int, val rows: Int, val labels: Boolean, val roomy: Boolean) {
    /** How many boxes fit, a "+N more" box included. */
    val capacity: Int get() = (columns * rows).coerceAtMost(HabitWidgetBuilder.WIDE_CAP)

    public companion object {
        /** From this many columns the boxes go in one row with names, like the design's wide card. */
        private const val ONE_ROW_FROM_COLUMNS = 4
        private const val MAX_ROWS = 2

        /**
         * All sizes in dp. [cell] is one box's touch target, [header] the header line with its gap,
         * [labelAndFooter] a name under the boxes plus the done line, [padding] / [smallPadding] the
         * card's inner padding, [roomyFrom] the height from which the normal padding is used.
         *
         * Example (cell 48, header 28, labelAndFooter 36, padding 16 / 8, roomyFrom 150): 170 x 170
         * -> 2 x 2 boxes; 330 x 170 -> one row of 6 with names; 110 x 110 -> 1 box.
         */
        public fun fit(
            width: Float,
            height: Float,
            cell: Float,
            header: Float,
            labelAndFooter: Float,
            padding: Float,
            smallPadding: Float,
            roomyFrom: Float,
        ): HabitWidgetLayout {
            val roomy = height >= roomyFrom
            val pad = if (roomy) padding else smallPadding
            val usableWidth = width - 2 * pad
            val usableHeight = height - 2 * pad - header
            val columns = (usableWidth / cell).toInt().coerceIn(1, HabitWidgetBuilder.WIDE_CAP)
            val rows = if (columns >= ONE_ROW_FROM_COLUMNS) 1 else (usableHeight / cell).toInt().coerceIn(1, MAX_ROWS)
            val labels = rows == 1 && usableHeight >= cell + labelAndFooter
            return HabitWidgetLayout(columns, rows, labels, roomy)
        }
    }
}
