package com.sonothamin.anythingwidgets.widget.spec

/**
 * Fixed Nothing-style colors a spec can use by name (they do not follow the wallpaper), plus
 * "#RRGGBB" / "#AARRGGBB" hex. Material theme tokens are resolved by the renderers themselves.
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

    val names: Set<String> get() = tokens.keys

    /** ARGB for a palette token or hex string, or null if [ref] is neither. */
    fun argb(ref: String): Int? {
        tokens[ref]?.let { return it.toInt() }
        if (!ref.startsWith("#")) return null
        return runCatching { android.graphics.Color.parseColor(ref) }.getOrNull()
    }
}
