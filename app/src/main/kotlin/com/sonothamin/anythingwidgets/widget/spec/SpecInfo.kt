package com.sonothamin.anythingwidgets.widget.spec

import java.net.URI

/** Read-only facts derived from a spec, shown on the widget details screen. */
object SpecInfo {
    private val TOKEN = Regex("\\{\\{([A-Za-z0-9_.]+)\\}\\}")

    private fun nodes(spec: WidgetSpec): List<Node> {
        val out = mutableListOf<Node>()
        fun walk(n: Node) {
            out += n
            n.children.forEach(::walk)
        }
        walk(spec.root)
        spec.compact?.let(::walk)
        spec.large?.let(::walk)
        return out
    }

    /**
     * Whether the light/dark choice changes anything: the default card follows it, and so do adaptive
     * palette names, Material tokens and any color left unset (which falls back to the theme's own).
     */
    fun followsTheme(spec: WidgetSpec): Boolean {
        if (spec.chrome != "none") return true
        fun adapts(ref: String?) = ref != null && (NothingPalette.adaptive(ref) != null || ref in SpecRules.MATERIAL_TOKENS)
        return nodes(spec).any { n ->
            val coloured = n.type in setOf("text", "glyph", "dotgrid", "progress", "clock")
            adapts(n.color) || adapts(n.background) || adapts(n.track) || adapts(n.accent) || (coloured && n.color == null)
        }
    }

    /** Human-readable names for the device data the widget binds to; empty for a static widget. */
    fun dataUsed(spec: WidgetSpec): List<String> =
        nodes(spec)
            .flatMap { n ->
                val keys = (listOfNotNull(n.text, n.progress, n.pattern) + n.pool)
                    .flatMap { t -> TOKEN.findAll(t).map { it.groupValues[1] }.toList() }
                keys + listOfNotNull(
                    "time.live".takeIf { n.type == "clock" },
                    "random.pool".takeIf { n.rotate == "random" && n.pool.isNotEmpty() },
                )
            }
            .mapNotNull {
                when {
                    it.startsWith("battery.") -> "Battery level"
                    it.startsWith("time.") || it.startsWith("day.") -> "Time"
                    it.startsWith("date.") || it.startsWith("year.") || it.startsWith("month.") -> "Date"
                    it.startsWith("random.") -> "Chance"
                    it.startsWith("storage.") -> "Storage"
                    it.startsWith("network.") -> "Network status"
                    it.startsWith("device.") -> "Device info"
                    it.startsWith("param.") || it.startsWith("state.") || it.startsWith("spec.") || it.startsWith("net.") -> null
                    it.startsWith("feed.") -> "Online data"
                    else -> it
                }
            }
            .distinct()

    /** What tapping the widget can do, e.g. "Opens this app", "Opens example.com". */
    fun actions(spec: WidgetSpec): List<String> =
        nodes(spec)
            .mapNotNull { it.action }
            .map { a ->
                when (a.type) {
                    "open_app" -> "Opens this app"
                    "refresh" -> "Refreshes the widget"
                    "launch_app" -> "Opens ${a.pkg}"
                    "dial" -> "Starts a call"
                    "email" -> "Starts an email"
                    "map" -> "Opens a map search"
                    "settings" -> "Opens ${a.screen} settings"
                    "timer" -> "Starts a timer"
                    "alarms" -> "Opens alarms"
                    "inc", "dec", "set", "toggle", "cycle", "reset" -> "Changes its own data"
                    "open_url" -> {
                        if (a.url?.contains("{{") == true) {
                            "Opens a link you choose in the widget settings"
                        } else {
                            val host = a.url?.let { runCatching { URI(it).host }.getOrNull() }
                            if (host.isNullOrBlank()) "Opens a link" else "Opens $host"
                        }
                    }
                    else -> a.type
                }
            }
            .distinct()

    /** True when tapping the widget changes something the widget remembers (a mini app). */
    fun isInteractive(spec: WidgetSpec): Boolean = spec.state.isNotEmpty() && nodes(spec).any { it.action?.type in STATE_OPS }

    /** Hosts the widget would contact if the user allows internet access. */
    fun feedHosts(spec: WidgetSpec): List<String> =
        spec.feeds.mapNotNull { SpecValidator.feedHost(it.url) }.distinct()

    private val STATE_OPS = setOf("inc", "dec", "set", "toggle", "cycle", "reset")

    fun hasSizeLayouts(spec: WidgetSpec): Boolean = spec.compact != null || spec.large != null
}
