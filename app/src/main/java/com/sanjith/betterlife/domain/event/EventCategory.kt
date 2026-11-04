package com.sanjith.betterlife.domain.event

/**
 * One of the owner's colour categories for events (context.md 5.2). Events store the [name];
 * [colorIndex] picks a swatch from the theme's category palette.
 */
public data class EventCategory(val name: String, val colorIndex: Int) {
    init {
        require(name.isNotBlank()) { "A category needs a name" }
        require(colorIndex >= 0) { "colorIndex cannot be negative" }
    }

    public companion object {
        private const val LINE = "\n"
        private const val PART = "|"

        /** One "index|name" line per category; names are single-line text fields. */
        public fun encode(categories: List<EventCategory>): String =
            categories.joinToString(LINE) { "${it.colorIndex}$PART${it.name.replace(LINE, " ")}" }

        /** Bad lines are skipped; a repeated name keeps its first colour. */
        public fun decode(text: String?): List<EventCategory> =
            text.orEmpty().split(LINE).mapNotNull { line ->
                val index = line.substringBefore(PART, "").toIntOrNull()?.takeIf { it >= 0 } ?: return@mapNotNull null
                val name = line.substringAfter(PART).trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                EventCategory(name, index)
            }.distinctBy { it.name.lowercase() }

        /** Adds [category], or recolours an existing one with the same name (ignoring case). */
        public fun upsert(categories: List<EventCategory>, category: EventCategory): List<EventCategory> {
            val clean = category.copy(name = category.name.trim())
            val at = categories.indexOfFirst { it.name.equals(clean.name, ignoreCase = true) }
            return if (at < 0) categories + clean else categories.toMutableList().also { it[at] = clean }
        }
    }
}
