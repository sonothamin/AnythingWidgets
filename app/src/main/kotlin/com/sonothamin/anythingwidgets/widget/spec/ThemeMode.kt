package com.sonothamin.anythingwidgets.widget.spec

/** How a widget picks between its light and dark look. */
enum class ThemeMode(val id: String, val label: String) {
    /** Follow the phone's light/dark setting. The default. */
    System("system", "System"),
    Dark("dark", "Dark"),
    Light("light", "Light");

    /** true = dark, false = light, null = follow the system. */
    val forcedDark: Boolean? get() = when (this) { System -> null; Dark -> true; Light -> false }

    companion object {
        val ids: Set<String> = entries.map { it.id }.toSet()

        /** Unknown or missing values mean [System]. */
        fun of(id: String?): ThemeMode = entries.firstOrNull { it.id == id } ?: System
    }
}
