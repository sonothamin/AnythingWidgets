package com.sonothamin.anythingwidgets.widget.spec

import kotlinx.serialization.json.Json

object SpecValidator {
    const val SUPPORTED_SCHEMA = 2
    const val MAX_NODES = 100
    const val MAX_DEPTH = 10
    const val MAX_TAGS = 10
    const val MAX_CATEGORIES = 5
    const val MAX_LABEL = 24

    val ID = Regex("[a-z0-9][a-z0-9._-]{0,63}")
    private val SPEC_PATH = Regex("[A-Za-z0-9][A-Za-z0-9._/-]{0,127}")
    private val SHA256 = Regex("[a-f0-9]{64}")
    private val CONTAINERS = setOf("row", "column", "box")
    private val TYPES = CONTAINERS + setOf("text", "spacer", "progress", "glyph", "dotgrid", "clock")
    private val FONTS = setOf("dots", "serif", "sans", "mono")
    private val CLOCKS = setOf("digital", "analog-dark", "analog-light", "analog-dots")
    private val ROTATE = setOf("day", "hour", "random")
    private val PATTERN = Regex("[#r._/\\s]*")
    private val CLOCK_FORMAT = Regex("[A-Za-z0-9 :,./'\\-\\n{}]{1,40}")
    private val NAV_ACTIONS = setOf("open_app", "open_url", "refresh", "launch_app", "dial", "email", "map", "settings", "timer", "alarms")
    private val STATE_ACTIONS = setOf("inc", "dec", "set", "toggle", "cycle", "reset")
    val SETTINGS_SCREENS = setOf("wifi", "bluetooth", "display", "battery", "sound", "location", "airplane", "storage", "apps", "date", "accessibility", "all")
    private val PACKAGE = Regex("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*){1,9}")
    private val BINDING_ONLY = Regex("[^{}]*(\\{\\{[A-Za-z0-9_.]+\\}\\}[^{}]*)*")
    private val FEED_FIELD = Regex("[a-z][A-Za-z0-9]{0,23}")
    private val FEED_PATH = Regex("[A-Za-z0-9_\\[\\].-]{1,80}")
    private val HOST = Regex("[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+")
    private val BLOCKED_HOST_SUFFIX = listOf(".local", ".internal", ".lan", ".localhost", ".home.arpa", ".corp")
    const val MAX_PARAMS = 12
    const val MAX_STATE = 12
    const val MAX_FEEDS = 4

    /** Host of an https feed URL, or null when the URL is not acceptable. Templates may not touch the host. */
    fun feedHost(url: String): String? {
        if (!url.startsWith("https://") || url.length > 400) return null
        val authority = url.removePrefix("https://").substringBefore('/').substringBefore('?').substringBefore('#')
        if ('{' in authority || '@' in authority || ':' in authority) return null
        val host = authority.lowercase()
        if (!HOST.matches(host) || host.length > 120) return null
        if (Regex("[0-9.]+").matches(host)) return null // no IP literals
        if (BLOCKED_HOST_SUFFIX.any { host.endsWith(it) } || host == "localhost") return null
        return host
    }

    private fun validateParams(spec: WidgetSpec): String? {
        if (spec.params.size > MAX_PARAMS) return "Too many params"
        val seen = mutableSetOf<String>()
        for (p in spec.params) {
            if (!SpecRules.ID.matches(p.id) || !seen.add(p.id)) return "Bad param id '${p.id}'"
            if (p.type !in SpecRules.PARAM_TYPES) return "Unknown param type '${p.type}'"
            if (p.label.isBlank() || p.label.length > 40 || (p.description?.length ?: 0) > 120) return "Bad param label for '${p.id}'"
            when (p.type) {
                "choice" -> {
                    if (p.choices.size !in 2..16) return "Param '${p.id}' needs 2 to 16 choices"
                    if (p.choices.any { !SpecRules.CHOICE_VALUE.matches(it.value) || (it.label?.length ?: 0) > 30 }) return "Bad choice in '${p.id}'"
                    if (p.choices.map { it.value }.toSet().size != p.choices.size) return "Duplicate choice in '${p.id}'"
                }
                "number" -> {
                    val lo = p.min ?: 0.0
                    val hi = p.max ?: 100.0
                    if (!(lo < hi) || Math.abs(lo) > 1e9 || Math.abs(hi) > 1e9) return "Bad range for '${p.id}'"
                    if (p.step != null && !(p.step > 0.0 && p.step <= hi - lo)) return "Bad step for '${p.id}'"
                }
                "text" -> if ((p.maxLength ?: 60) !in 1..80) return "Bad maxLength for '${p.id}'"
            }
            if (!SpecRules.paramDefaultValid(p)) return "Bad default for '${p.id}'"
        }
        return null
    }

    private fun validateState(spec: WidgetSpec): String? {
        if (spec.state.size > MAX_STATE) return "Too many state values"
        val seen = mutableSetOf<String>()
        for (st in spec.state) {
            if (!SpecRules.ID.matches(st.id) || !seen.add(st.id)) return "Bad state id '${st.id}'"
            if (st.type !in SpecRules.STATE_TYPES) return "Unknown state type '${st.type}'"
            if ((st.label?.length ?: 0) > 40) return "Bad state label"
            when (st.type) {
                "choice" -> {
                    if (st.values.size !in 2..16 || st.values.any { !SpecRules.CHOICE_VALUE.matches(it) } || st.values.toSet().size != st.values.size) {
                        return "Bad values for '${st.id}'"
                    }
                }
                "number" -> {
                    val lo = st.min
                    val hi = st.max
                    if ((lo != null && Math.abs(lo) > 1e9) || (hi != null && Math.abs(hi) > 1e9) || (lo != null && hi != null && lo >= hi)) {
                        return "Bad range for '${st.id}'"
                    }
                    if (st.wrap && (lo == null || hi == null)) return "wrap needs min and max on '${st.id}'"
                }
            }
            if (!SpecRules.stateDefaultValid(st)) return "Bad default for '${st.id}'"
        }
        return null
    }

    private fun validateFeeds(spec: WidgetSpec): String? {
        if (spec.feeds.size > MAX_FEEDS) return "Too many feeds"
        val seen = mutableSetOf<String>()
        for (f in spec.feeds) {
            if (!SpecRules.ID.matches(f.id) || !seen.add(f.id)) return "Bad feed id '${f.id}'"
            if (feedHost(f.url) == null) return "Feed '${f.id}' needs a public https URL"
            val tokens = Regex("\\{\\{([A-Za-z0-9_.]+)\\}\\}").findAll(f.url).map { it.groupValues[1] }.toList()
            if (tokens.any { !it.startsWith("param.") || spec.params.none { p -> "param.${p.id}" == it || it.startsWith("param.${p.id}.") } }) {
                return "Feed '${f.id}' may only use declared {{param.*}} values in its URL"
            }
            if (f.fields.isEmpty() || f.fields.size > 16) return "Feed '${f.id}' needs 1 to 16 fields"
            if (f.fields.any { (k, v) -> !FEED_FIELD.matches(k) || !FEED_PATH.matches(v) }) return "Bad field in feed '${f.id}'"
            val maxTtl = if (f.list != null) 43200 else 1440
            if (f.ttl !in 15..maxTtl) return "Feed ttl must be 15 to $maxTtl minutes"
            if (f.list != null && !FEED_PATH.matches(f.list)) return "Bad list path in feed '${f.id}'"
            if (f.keep !in 1..50) return "Feed keep must be 1 to 50"
            if ((f.label?.length ?: 0) > 40) return "Bad feed label"
        }
        return null
    }

    private fun validateAction(a: ActionSpec, spec: WidgetSpec): String? {
        if (a.type !in NAV_ACTIONS && a.type !in STATE_ACTIONS) return "Unknown action '${a.type}'"
        when (a.type) {
            "open_url" -> if (!(a.url?.startsWith("https://") == true && a.url.length <= 512)) return "open_url requires an https URL"
            "launch_app" -> if (a.pkg == null || !PACKAGE.matches(a.pkg) || a.pkg.length > 128) return "launch_app needs a package name"
            "dial" -> if (a.number.isNullOrBlank() || a.number.length > 40 || !BINDING_ONLY.matches(a.number)) return "dial needs a number"
            "email" -> if (a.to.isNullOrBlank() || a.to.length > 120 || !BINDING_ONLY.matches(a.to) || (a.subject?.length ?: 0) > 120) return "email needs an address"
            "map" -> if (a.query.isNullOrBlank() || a.query.length > 120 || !BINDING_ONLY.matches(a.query)) return "map needs a query"
            "settings" -> if (a.screen !in SETTINGS_SCREENS) return "Unknown settings screen"
            "timer" -> if (a.seconds == null || a.seconds !in 1..86_400) return "timer needs 1 to 86400 seconds"
        }
        if (a.type in STATE_ACTIONS) {
            val target = a.target?.let { t -> spec.state.firstOrNull { it.id == t } ?: return "Action targets unknown state '$t'" }
            if (a.type != "reset" && target == null) return "'${a.type}' needs a target"
            when (a.type) {
                "inc", "dec" -> if (target?.type != "number") return "'${a.type}' needs a number state"
                "toggle" -> if (target?.type != "bool") return "toggle needs a bool state"
                "cycle" -> if (target?.type != "choice") return "cycle needs a choice state"
                "set" -> {
                    if (a.value == null) return "set needs a value"
                    if (SpecRules.normalizeState(target!!, a.value.content) == null) return "Bad value for '${target.id}'"
                }
            }
            if (a.by != null && !(a.by.isFinite() && Math.abs(a.by) <= 1e6)) return "Bad step"
        }
        return null
    }
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
        validateParams(spec)?.let { return it }
        validateState(spec)?.let { return it }
        validateFeeds(spec)?.let { return it }
        var count = 0
        fun walk(n: Node, depth: Int): String? {
            if (++count > MAX_NODES) return "Too many nodes"
            if (depth > MAX_DEPTH) return "Nesting too deep"
            if (n.type !in TYPES) return "Unknown node type '${n.type}'"
            if (n.children.isNotEmpty() && n.type !in CONTAINERS) return "'${n.type}' cannot have children"
            if (n.padding !in 0..48 || n.gap !in 0..32 || n.radius !in 0..200) return "Spacing out of range"
            if (n.size !in 8..200) return "Text size out of range"
            if ((n.width ?: 1) !in 1..400 || (n.height ?: 1) !in 1..400) return "Dimension out of range"
            if ((n.text?.length ?: 0) > 200 || (n.progress?.length ?: 0) > 64) return "Text too long"
            if (n.fill != null && n.fill !in setOf("width", "height", "both")) return "Invalid fill"
            n.action?.let { a -> validateAction(a, spec)?.let { return it } }
            if (n.visible != null && !SpecRules.conditionValid(n.visible)) return "Invalid condition"
            if (n.bind.size > 10) return "Too many bindings"
            n.bind.forEach { (k, v) ->
                if (k !in SpecRules.NUMERIC_BINDS && k !in SpecRules.BOOL_BINDS) return "Cannot bind '$k'"
                if (v.length > 64) return "Binding too long"
            }
            if (n.type == "glyph" && n.font != null && n.font !in FONTS) return "Unknown font '${n.font}'"
            if (n.type == "clock") {
                if (n.variant != null && n.variant !in CLOCKS) return "Unknown clock '${n.variant}'"
                if (n.text != null && !CLOCK_FORMAT.matches(n.text)) return "Invalid clock format"
            }
            if (n.rotate != null && n.rotate !in ROTATE) return "Unknown rotate '${n.rotate}'"
            if (n.pool.size > 32 || n.pool.any { it.length > 160 }) return "Pool too large"
            if (n.type == "dotgrid") {
                if (n.columns !in 1..80 || n.rows !in 1..80 || n.columns * n.rows > 800) return "Invalid dot grid"
                if ((n.pattern?.length ?: 0) > 1024) return "Pattern too long"
                if (n.pattern != null && !n.pattern.contains("{{") && !PATTERN.matches(n.pattern)) return "Invalid pattern"
            }
            n.children.forEach { c -> walk(c, depth + 1)?.let { return it } }
            return null
        }
        walk(spec.root, 1)?.let { return it }
        // The node limit applies to each layout separately.
        spec.compact?.let { c -> count = 0; walk(c, 1)?.let { return it } }
        spec.large?.let { c -> count = 0; walk(c, 1)?.let { return it } }
        if (spec.chrome != null && spec.chrome != "none" && spec.chrome != "default") return "Unknown chrome '${spec.chrome}'"
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
