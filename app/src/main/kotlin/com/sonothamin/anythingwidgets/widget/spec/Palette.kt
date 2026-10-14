package com.sonothamin.anythingwidgets.widget.spec

/**
 * Nothing-style colors a spec can use by name, plus "#RRGGBB" / "#AARRGGBB" hex. Material theme tokens
 * are resolved by the renderers themselves.
 *
 * Two kinds of names:
 *  - Fixed colors (n.red, n.white, ...) never change.
 *  - Adaptive colors (n.card, n.ink, ...) have a light and a dark value and follow the system's
 *    light/dark mode. Use these for cards, text and unlit dots so a widget has a proper dark theme.
 */
object NothingPalette {
    private val tokens = mapOf(
        "n.red" to 0xFFD71921L,
        "n.white" to 0xFFEFEFEFL,
        "n.black" to 0xFF000000L,
        "n.dark" to 0xFF1C1C1CL,
        /** Muted text on dark cards. */
        "n.ash" to 0xFF9A9A9AL,
        /** Muted text on light cards. */
        "n.graphite" to 0xFF5C5C5CL,
        /** Unlit dots on dark cards. */
        "n.dot" to 0xFF3C3C3CL,
        /** Unlit dots on light cards. */
        "n.mist" to 0xFFCBCBCBL,
    )

    /** Light value, dark value. */
    private val adaptiveTokens = mapOf(
        /** Card background: paper in light mode, near-black in dark mode. */
        "n.card" to (0xFFEFEFEFL to 0xFF1C1C1CL),
        /** Main text and lit dots on a card. */
        "n.ink" to (0xFF000000L to 0xFFEFEFEFL),
        /** Muted text on a card. */
        "n.muted" to (0xFF5C5C5CL to 0xFF9A9A9AL),
        /** Unlit dots and tracks on a card. */
        "n.faint" to (0xFFCBCBCBL to 0xFF3C3C3CL),
        /** Ink at 6%, 18% and 60% strength: soft dot art and secondary marks. */
        "n.ink06" to (0x0F000000L to 0x0FEFEFEFL),
        "n.ink18" to (0x2E000000L to 0x2EEFEFEFL),
        "n.ink60" to (0x99000000L to 0x99EFEFEFL),
    )

    /** Every color name a spec may use. */
    val names: Set<String> = tokens.keys + adaptiveTokens.keys

    /** Names that never change with the theme. */
    val fixedNames: List<String> get() = tokens.keys.sorted()

    /** Names that switch between a light and a dark value. */
    val adaptiveNames: List<String> get() = adaptiveTokens.keys.sorted()

    /** (light, dark) ARGB for an adaptive name, or null if [ref] is not one. */
    fun adaptive(ref: String): Pair<Int, Int>? =
        adaptiveTokens[ref]?.let { it.first.toInt() to it.second.toInt() }

    /** ARGB for a palette token or hex string, or null if [ref] is neither. Adaptive names give their light value. */
    fun argb(ref: String): Int? = argb(ref, dark = false)

    /** Same as [argb], picking the dark value of adaptive names when [dark] is true. */
    fun argb(ref: String, dark: Boolean): Int? {
        adaptiveTokens[ref]?.let { return (if (dark) it.second else it.first).toInt() }
        tokens[ref]?.let { return it.toInt() }
        if (!ref.startsWith("#")) return null
        return runCatching { android.graphics.Color.parseColor(ref) }.getOrNull()
    }
}
