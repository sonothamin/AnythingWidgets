package com.sonothamin.anythingwidgets.widget.spec

import kotlinx.serialization.json.Json

object SpecValidator {
    const val SUPPORTED_SCHEMA = 1
    const val MAX_NODES = 100
    const val MAX_DEPTH = 10
    const val MAX_TAGS = 10
    const val MAX_CATEGORIES = 5
    const val MAX_LABEL = 24

    val ID = Regex("[a-z0-9][a-z0-9._-]{0,63}")
    private val SPEC_PATH = Regex("[A-Za-z0-9][A-Za-z0-9._/-]{0,127}")
    private val SHA256 = Regex("[a-f0-9]{64}")
    private val CONTAINERS = setOf("row", "column", "box")
    private val TYPES = CONTAINERS + setOf("text", "spacer", "progress")
    private val ACTIONS = setOf("open_app", "open_url", "refresh")
    private val DATE = Regex("\\d{4}-\\d{2}-\\d{2}")

    private fun metaError(created: String?, tags: List<String>, categories: List<String>): String? {
        if (created != null && !DATE.matches(created)) return "Invalid created date"
        if (tags.size > MAX_TAGS || categories.size > MAX_CATEGORIES) return "Too many tags or categories"
        if ((tags + categories).any { it.isBlank() || it.length > MAX_LABEL }) return "Invalid tag or category"
        return null
    }

    fun validate(spec: WidgetSpec): String? {
        if (spec.schema > SUPPORTED_SCHEMA) return "Unsupported schema ${spec.schema}"
        if (!ID.matches(spec.id)) return "Invalid id"
        if (spec.version < 1) return "Invalid version"
        if (spec.name.isBlank() || spec.name.length > 60) return "Invalid name"
        metaError(spec.created, spec.tags, spec.categories)?.let { return it }
        var count = 0
        fun walk(n: Node, depth: Int): String? {
            if (++count > MAX_NODES) return "Too many nodes"
            if (depth > MAX_DEPTH) return "Nesting too deep"
            if (n.type !in TYPES) return "Unknown node type '${n.type}'"
            if (n.children.isNotEmpty() && n.type !in CONTAINERS) return "'${n.type}' cannot have children"
            if (n.padding !in 0..48 || n.gap !in 0..32 || n.radius !in 0..48) return "Spacing out of range"
            if (n.size !in 8..96) return "Text size out of range"
            if ((n.width ?: 1) !in 1..400 || (n.height ?: 1) !in 1..400) return "Dimension out of range"
            if ((n.text?.length ?: 0) > 200 || (n.progress?.length ?: 0) > 64) return "Text too long"
            if (n.fill != null && n.fill !in setOf("width", "height", "both")) return "Invalid fill"
            n.action?.let { a ->
                if (a.type !in ACTIONS) return "Unknown action '${a.type}'"
                if (a.type == "open_url" && !(a.url?.startsWith("https://") == true && a.url.length <= 512)) {
                    return "open_url requires an https URL"
                }
            }
            n.children.forEach { c -> walk(c, depth + 1)?.let { return it } }
            return null
        }
        walk(spec.root, 1)?.let { return it }
        spec.compact?.let { c -> walk(c, 1)?.let { return it } }
        return null
    }

    fun validate(index: RegistryIndex): String? {
        if (index.schema > SUPPORTED_SCHEMA) return "Unsupported registry schema"
        index.widgets.forEach { e ->
            if (!ID.matches(e.id) || e.version < 1) return "Bad entry '${e.id}'"
            if (!SPEC_PATH.matches(e.spec) || ".." in e.spec) return "Bad spec path for '${e.id}'"
            if (!SHA256.matches(e.sha256)) return "Bad checksum for '${e.id}'"
            metaError(e.created, e.tags, e.categories)?.let { return "Bad metadata for '${e.id}'" }
        }
        return null
    }
}

object SpecCodec {
    val json = Json { ignoreUnknownKeys = true }

    /** Parses and validates; throws on anything malformed or unsafe. */
    fun parseSpec(raw: String): WidgetSpec {
        val spec = json.decodeFromString<WidgetSpec>(raw)
        SpecValidator.validate(spec)?.let { throw IllegalArgumentException(it) }
        return spec
    }

    fun parseIndex(raw: String): RegistryIndex {
        val index = json.decodeFromString<RegistryIndex>(raw)
        SpecValidator.validate(index)?.let { throw IllegalArgumentException(it) }
        return index
    }
}
