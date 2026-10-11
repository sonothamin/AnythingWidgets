package com.sonothamin.anythingwidgets.widget.spec

import kotlinx.serialization.Serializable

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
    val root: Node,
    /** Optional layout used when the widget is resized below ~180dp wide. */
    val compact: Node? = null,
)

/** One flat node type keeps the format forward compatible and trivial to validate. */
@Serializable
data class Node(
    /** row | column | box | text | spacer | progress */
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
)

/** open_app | open_url (https only) | refresh */
@Serializable
data class ActionSpec(val type: String, val url: String? = null)

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
