package com.sanjith.betterlife.domain.habit

/**
 * The four habits every install starts with (context.md 5.4), matching docs/design. Their names
 * come from string resources (strings_habits.xml).
 *
 * The ids are FIXED so a second install seeds the very same rows and habit sync (S3) merges them
 * instead of making duplicates. [colorHex] is stored data (the owner can recolour); the Prompt 15
 * palette in ui/theme must include these values.
 */
public data class StarterHabit(
    val id: String,
    val key: Key,
    val avatar: String,
    val colorHex: String,
    val category: HabitCategory,
    val essential: Boolean,
) {
    public enum class Key { CREATINE, MEDITATION, SKINCARE, HAIRCARE }
}

public object StarterHabits {
    public val ALL: List<StarterHabit> = listOf(
        StarterHabit("5a1f0c3e-0b1a-4c11-8000-000000000001", StarterHabit.Key.CREATINE, "💊", "#FF6B5E", HabitCategory.HEALTH, essential = true),
        StarterHabit("5a1f0c3e-0b1a-4c11-8000-000000000002", StarterHabit.Key.MEDITATION, "🧘", "#7E8CFF", HabitCategory.SELF_CARE, essential = false),
        StarterHabit("5a1f0c3e-0b1a-4c11-8000-000000000003", StarterHabit.Key.SKINCARE, "🧴", "#FF8FB8", HabitCategory.SELF_CARE, essential = false),
        StarterHabit("5a1f0c3e-0b1a-4c11-8000-000000000004", StarterHabit.Key.HAIRCARE, "💇", "#5EC4D6", HabitCategory.SELF_CARE, essential = false),
    )
}
