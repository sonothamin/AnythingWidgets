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
        return out
    }

    /** Human-readable names for the device data the widget binds to; empty for a static widget. */
    fun dataUsed(spec: WidgetSpec): List<String> =
        nodes(spec)
            .flatMap { listOfNotNull(it.text, it.progress) }
            .flatMap { t -> TOKEN.findAll(t).map { it.groupValues[1] }.toList() }
            .map {
                when {
                    it.startsWith("battery.") -> "Battery level"
                    it.startsWith("time.") -> "Time"
                    it.startsWith("date.") -> "Date"
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
                    "open_url" -> {
                        val host = a.url?.let { runCatching { URI(it).host }.getOrNull() }
                        if (host.isNullOrBlank()) "Opens a link" else "Opens $host"
                    }
                    else -> a.type
                }
            }
            .distinct()

    fun hasCompactLayout(spec: WidgetSpec): Boolean = spec.compact != null
}
