package com.sonothamin.anythingwidgets.widget.spec

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

/**
 * A widget definition served by the central registry. Pure data: no scripts, no code.
 * See docs/WIDGET_SPEC.md.
 */
@Serializable
data class WidgetSpec(
    val schema: Int,
    val id: String,
    val version: Int,
    val name: String,
    val description: String? = null,
    val author: String? = null,
    /** Creation date, ISO yyyy-MM-dd. */
    val created: String? = null,
    val tags: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
    /** Default layout (also the wide one when [large] is set). */
    val root: Node,
    /** Optional layout used when the widget is resized below ~180dp wide. */
    val compact: Node? = null,
    /** Optional layout used when the widget is at least ~180dp wide and ~180dp tall. */
    val large: Node? = null,
    /**
     * "none" removes the system card behind the widget so the root node can draw its own shape
     * (circles, pills, floating pieces). Anything else keeps the default card.
     */
    val chrome: String? = null,
    // --- schema 2 -----------------------------------------------------------------------------
    /** Options the user can change in the app; read in layouts as {{param.id}}. */
    val params: List<ParamSpec> = emptyList(),
    /** Values the widget itself changes when tapped (counters, checklists, pickers): mini apps. */
    val state: List<StateSpec> = emptyList(),
    /** Read-only JSON fetched from the internet, only after the user allows it. */
    val feeds: List<FeedSpec> = emptyList(),
) {
    /** Picks the layout for a widget of the given size in dp. */
    fun layoutFor(widthDp: Float, heightDp: Float): Node = when {
        widthDp < 180f -> compact ?: root
        heightDp >= 180f -> large ?: root
        else -> root
    }
}

/** One flat node type keeps the format forward compatible and trivial to validate. */
@Serializable
data class Node(
    /** row | column | box | text | spacer | progress | glyph | dotgrid | clock */
    val type: String,
    val children: List<Node> = emptyList(),
    // layout
    val padding: Int = 0,
    val gap: Int = 0,
    /** Cross axis for row/column; position for box (e.g. "center", "bottom-end"). */
    val align: String? = null,
    /** Main axis for row/column: start | center | end. */
    val justify: String? = null,
    val grow: Boolean = false,
    /** width | height | both */
    val fill: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    // appearance
    val background: String? = null,
    val radius: Int = 0,
    // text
    val text: String? = null,
    val size: Int = 14,
    val bold: Boolean = false,
    val color: String? = null,
    val maxLines: Int? = null,
    val textAlign: String? = null,
    // progress: a template that resolves to 0..1, e.g. "{{battery.fraction}}"
    val progress: String? = null,
    val track: String? = null,
    val action: ActionSpec? = null,
    // text / glyph: pick one entry from a pool instead of [text]. [rotate]: day (default) | hour | random.
    val pool: List<String> = emptyList(),
    val rotate: String? = null,
    /** glyph: dots (dot-matrix, default) | serif | sans | mono. clock: digital | analog-dark | analog-light | analog-dots. */
    val font: String? = null,
    val variant: String? = null,
    // dotgrid: columns x rows of dots. Filled by [progress], or drawn from [pattern]
    // (# on, r accent, . off, _ blank; row by row). [accent] colors the 'r' dots.
    val columns: Int = 0,
    val rows: Int = 0,
    val pattern: String? = null,
    val accent: String? = null,
    // --- schema 2 -----------------------------------------------------------------------------
    /** Condition; the node (and its children) is drawn only when it holds. See SpecRules.evalCondition. */
    val visible: String? = null,
    /** Numeric/bold overrides from bindings, e.g. {"size": "{{param.fontSize}}"}. */
    val bind: Map<String, String> = emptyMap(),
)

/**
 * What tapping a node does.
 * Navigation: open_app | open_url | refresh | launch_app | dial | email | map | settings | timer | alarms.
 * Mini app (changes [StateSpec] values): inc | dec | set | toggle | cycle | reset.
 */
@Serializable
data class ActionSpec(
    val type: String,
    val url: String? = null,
    /** launch_app: package name of an installed app. */
    @SerialName("package") val pkg: String? = null,
    /** dial: phone number. email: address. May use {{param.x}}. */
    val number: String? = null,
    val to: String? = null,
    val subject: String? = null,
    /** map: search text or address. May use {{param.x}}. */
    val query: String? = null,
    /** settings: wifi | bluetooth | display | battery | sound | location | airplane | storage | apps | date | accessibility | all. */
    val screen: String? = null,
    /** timer: seconds to count down in the system clock app. */
    val seconds: Int? = null,
    /** State actions: id of the [StateSpec] to change (reset without one resets all). */
    val target: String? = null,
    /** inc / dec step (default 1). */
    val by: Double? = null,
    /** set: the value to store. */
    val value: JsonPrimitive? = null,
)

/** toggle | choice | text | number | color | date */
@Serializable
data class ParamSpec(
    val id: String,
    val type: String,
    val label: String,
    val description: String? = null,
    /** Any JSON scalar: true, 8, "n.red", "2026-12-25", "dots". */
    val default: JsonPrimitive? = null,
    // number
    val min: Double? = null,
    val max: Double? = null,
    val step: Double? = null,
    val unit: String? = null,
    // choice
    val choices: List<ChoiceSpec> = emptyList(),
    // text
    val maxLength: Int? = null,
)

@Serializable
data class ChoiceSpec(val value: String, val label: String? = null)

/** number | bool | choice. */
@Serializable
data class StateSpec(
    val id: String,
    val type: String,
    val label: String? = null,
    val default: JsonPrimitive? = null,
    val min: Double? = null,
    val max: Double? = null,
    /** number: go from max back to min (and the other way) instead of stopping. */
    val wrap: Boolean = false,
    /** choice: the values cycled through. */
    val values: List<String> = emptyList(),
)

/**
 * A read-only JSON endpoint. [url] is https and may only vary through {{param.x}}.
 * [fields] maps a name to a dotted path, e.g. {"temp": "current.temperature_2m"}; read as {{feed.id.temp}}.
 */
@Serializable
data class FeedSpec(
    val id: String,
    val url: String,
    val fields: Map<String, String>,
    /** Minutes between refreshes (15..1440). */
    val ttl: Int = 60,
    val label: String? = null,
)

@Serializable
data class RegistryIndex(val schema: Int, val widgets: List<IndexEntry>)

@Serializable
data class IndexEntry(
    val id: String,
    val name: String,
    val version: Int,
    /** Path relative to the registry base URL. */
    val spec: String,
    /** Lowercase hex SHA-256 of the spec file bytes. */
    val sha256: String,
    val author: String? = null,
    val description: String? = null,
    val created: String? = null,
    val tags: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
)
