package com.sonothamin.anythingwidgets.widget.spec

import java.time.LocalDate

/** Rules shared by the validator, the runtime and the settings UI so they can never disagree. */
object SpecRules {
    val MATERIAL_TOKENS = setOf(
        "primary", "onPrimary", "primaryContainer", "onPrimaryContainer",
        "secondary", "onSecondary", "secondaryContainer", "onSecondaryContainer",
        "tertiary", "onTertiary", "tertiaryContainer", "onTertiaryContainer",
        "surface", "onSurface", "surfaceVariant", "onSurfaceVariant",
        "background", "onBackground", "error", "outline",
    )
    private val HEX = Regex("#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})")

    val PARAM_TYPES = setOf("toggle", "choice", "text", "number", "color", "date")
    val STATE_TYPES = setOf("number", "bool", "choice")
    val NUMERIC_BINDS = setOf("size", "padding", "gap", "radius", "width", "height", "columns", "rows", "maxLines")
    val BOOL_BINDS = setOf("bold")
    private val FALSY = setOf("", "false", "0", "no", "off")

    val ID = Regex("[a-z][A-Za-z0-9]{0,23}")
    val CHOICE_VALUE = Regex("[A-Za-z0-9_.-]{1,24}")

    fun validColor(ref: String): Boolean =
        ref in MATERIAL_TOKENS || ref in NothingPalette.names || HEX.matches(ref)

    fun fmt(d: Double): String = if (d == Math.floor(d) && Math.abs(d) < 1e12) d.toLong().toString() else d.toString()

    // --- params --------------------------------------------------------------------------------

    private fun numberRange(p: ParamSpec) = (p.min ?: 0.0) to (p.max ?: 100.0)

    /** Returns the cleaned value for [raw], or null if it is not acceptable for this parameter. */
    fun normalizeParam(p: ParamSpec, raw: String?): String? {
        val v = raw ?: return null
        return when (p.type) {
            "toggle" -> v.takeIf { it == "true" || it == "false" }
            "choice" -> v.takeIf { c -> p.choices.any { it.value == c } }
            "text" -> v.takeIf { it.length <= (p.maxLength ?: 60) }
            "number" -> {
                val (lo, hi) = numberRange(p)
                v.toDoubleOrNull()?.takeIf { it.isFinite() }?.coerceIn(lo, hi)?.let(::fmt)
            }
            "color" -> v.takeIf(::validColor)
            "date" -> runCatching { LocalDate.parse(v).toString() }.getOrNull()
            else -> null
        }
    }

    /** The default as a string, falling back to a sensible zero value so a missing default is never invalid. */
    fun paramDefault(p: ParamSpec): String {
        val given = p.default?.content
        normalizeParam(p, given)?.let { return it }
        return when (p.type) {
            "toggle" -> "false"
            "choice" -> p.choices.firstOrNull()?.value.orEmpty()
            "number" -> fmt(numberRange(p).first)
            "color" -> "primary"
            "date" -> LocalDate.now().toString()
            else -> ""
        }
    }

    fun paramDefaultValid(p: ParamSpec): Boolean = p.default == null || normalizeParam(p, p.default.content) != null

    // --- state ---------------------------------------------------------------------------------

    fun clampState(s: StateSpec, d: Double): Double = d.coerceIn(s.min ?: -1e9, s.max ?: 1e9)

    fun normalizeState(s: StateSpec, raw: String?): String? {
        val v = raw ?: return null
        return when (s.type) {
            "bool" -> v.takeIf { it == "true" || it == "false" }
            "choice" -> v.takeIf { it in s.values }
            "number" -> v.toDoubleOrNull()?.takeIf { it.isFinite() }?.let { fmt(clampState(s, it)) }
            else -> null
        }
    }

    fun stateDefault(s: StateSpec): String {
        normalizeState(s, s.default?.content)?.let { return it }
        return when (s.type) {
            "bool" -> "false"
            "choice" -> s.values.firstOrNull().orEmpty()
            else -> fmt(clampState(s, 0.0))
        }
    }

    fun stateDefaultValid(s: StateSpec): Boolean = s.default == null || normalizeState(s, s.default.content) != null

    // --- conditions ----------------------------------------------------------------------------

    private val CLAUSE = Regex("(!)?([A-Za-z0-9_.]{1,64})(?:\\s*(==|!=|<=|>=|<|>)\\s*('[^']{0,32}'|[^\\s'<>=!&|]{1,32}))?")
    const val MAX_CONDITION = 160

    /** Grammar: clause (&& clause)* (|| ...)*; clause = [!]key | key op literal, op in == != < <= > >=. */
    fun conditionValid(expr: String): Boolean {
        if (expr.length > MAX_CONDITION) return false
        return expr.split("||").all { or -> or.split("&&").all { CLAUSE.matches(it.trim()) } }
    }

    fun evalCondition(expr: String?, data: Map<String, String>): Boolean {
        if (expr.isNullOrBlank()) return true
        return expr.split("||").any { or ->
            or.split("&&").all { clause ->
                val m = CLAUSE.matchEntire(clause.trim()) ?: return@all false
                val negate = m.groupValues[1] == "!"
                val left = data[m.groupValues[2]].orEmpty()
                val op = m.groupValues[3]
                val result = if (op.isEmpty()) {
                    left.lowercase() !in FALSY
                } else {
                    val right = m.groupValues[4].removeSurrounding("'")
                    val a = left.toDoubleOrNull()
                    val b = right.toDoubleOrNull()
                    if (a != null && b != null) when (op) {
                        "==" -> a == b
                        "!=" -> a != b
                        "<" -> a < b
                        "<=" -> a <= b
                        ">" -> a > b
                        else -> a >= b
                    } else when (op) {
                        "==" -> left == right
                        "!=" -> left != right
                        "<" -> left < right
                        "<=" -> left <= right
                        ">" -> left > right
                        else -> left >= right
                    }
                }
                result != negate
            }
        }
    }

    // --- node resolution -----------------------------------------------------------------------

    private val TOKEN = Regex("\\{\\{([A-Za-z0-9_.]+)\\}\\}")

    private fun apply(t: String, data: Map<String, String>) = TOKEN.replace(t) { data[it.groupValues[1]].orEmpty() }

    /**
     * Applies `visible`, `bind` and templated colors. Returns null when the node should not be drawn.
     * Both renderers call this first, so previews and real widgets behave identically.
     */
    fun resolve(node: Node, data: Map<String, String>): Node? {
        if (!evalCondition(node.visible, data)) return null
        var n = node
        for ((key, template) in node.bind) {
            val v = apply(template, data).trim()
            if (key == "bold") {
                n = n.copy(bold = v.lowercase() !in FALSY)
                continue
            }
            val i = v.toDoubleOrNull()?.toInt() ?: continue
            n = when (key) {
                "size" -> n.copy(size = i.coerceIn(8, 200))
                "padding" -> n.copy(padding = i.coerceIn(0, 48))
                "gap" -> n.copy(gap = i.coerceIn(0, 32))
                "radius" -> n.copy(radius = i.coerceIn(0, 200))
                "width" -> n.copy(width = i.coerceIn(1, 400))
                "height" -> n.copy(height = i.coerceIn(1, 400))
                "columns" -> n.copy(columns = i.coerceIn(1, 80))
                "rows" -> n.copy(rows = i.coerceIn(1, 80))
                "maxLines" -> n.copy(maxLines = i.coerceIn(1, 20))
                else -> n
            }
        }
        fun color(c: String?) = c?.takeIf { "{{" in it }?.let { apply(it, data) } ?: c
        return n.copy(color = color(n.color), background = color(n.background), track = color(n.track), accent = color(n.accent))
    }
}
