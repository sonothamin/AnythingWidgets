package com.sonothamin.anythingwidgets.widget

import android.content.Context
import android.net.Uri
import android.text.format.DateFormat
import com.sonothamin.anythingwidgets.widget.data.Http
import com.sonothamin.anythingwidgets.widget.data.WidgetPrefs
import com.sonothamin.anythingwidgets.widget.spec.ActionSpec
import com.sonothamin.anythingwidgets.widget.spec.FeedSpec
import com.sonothamin.anythingwidgets.widget.spec.SpecCodec
import com.sonothamin.anythingwidgets.widget.spec.SpecRules
import com.sonothamin.anythingwidgets.widget.spec.WidgetSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Date

/**
 * Everything a widget can bind to beyond the device snapshot: the user's parameter values, the
 * widget's own mini-app state, and cached feed data. One place builds the map for both the
 * real widget and the in-app previews.
 */
object WidgetRuntime {
    val STATE_OPS = setOf("inc", "dec", "set", "toggle", "cycle", "reset")

    fun data(context: Context, spec: WidgetSpec): Map<String, String> {
        val out = DataSnapshot.build(context).toMutableMap()
        out["spec.id"] = spec.id
        out["net.allowed"] = WidgetPrefs.netAllowed(context, spec.id).toString()
        putParams(context, spec, out)
        putState(context, spec, out)
        putFeeds(context, spec, out)
        return out
    }

    // --- parameters ----------------------------------------------------------------------------

    fun paramValue(context: Context, spec: WidgetSpec, id: String): String? {
        val p = spec.params.firstOrNull { it.id == id } ?: return null
        return SpecRules.normalizeParam(p, WidgetPrefs.param(context, spec.id, id)) ?: SpecRules.paramDefault(p)
    }

    private fun putParams(context: Context, spec: WidgetSpec, out: MutableMap<String, String>) {
        val today = LocalDate.now()
        for (p in spec.params) {
            val v = paramValue(context, spec, p.id) ?: continue
            val key = "param.${p.id}"
            out[key] = v
            when (p.type) {
                "number" -> {
                    val lo = p.min ?: 0.0
                    val hi = p.max ?: 100.0
                    out["$key.fraction"] = (((v.toDoubleOrNull() ?: lo) - lo) / (hi - lo)).coerceIn(0.0, 1.0).toString()
                }
                "choice" -> {
                    out["$key.label"] = p.choices.firstOrNull { it.value == v }?.let { it.label ?: it.value } ?: v
                    out["$key.index"] = p.choices.indexOfFirst { it.value == v }.toString()
                }
                "date" -> {
                    val d = runCatching { LocalDate.parse(v) }.getOrNull() ?: continue
                    val days = ChronoUnit.DAYS.between(today, d)
                    out["$key.days"] = days.toString()
                    out["$key.daysUntil"] = maxOf(0L, days).toString()
                    out["$key.daysSince"] = maxOf(0L, -days).toString()
                    out["$key.isPast"] = (days < 0).toString()
                    out["$key.isToday"] = (days == 0L).toString()
                    out["$key.text"] = d.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
                }
            }
        }
    }

    // --- mini-app state ------------------------------------------------------------------------

    private fun stateValue(context: Context, spec: WidgetSpec, id: String): String? {
        val st = spec.state.firstOrNull { it.id == id } ?: return null
        return SpecRules.normalizeState(st, WidgetPrefs.state(context, spec.id, id)) ?: SpecRules.stateDefault(st)
    }

    private fun putState(context: Context, spec: WidgetSpec, out: MutableMap<String, String>) {
        for (st in spec.state) {
            val v = stateValue(context, spec, st.id) ?: continue
            val key = "state.${st.id}"
            out[key] = v
            when (st.type) {
                "bool" -> out["$key.bit"] = if (v == "true") "#" else "."
                "number" -> {
                    val lo = st.min
                    val hi = st.max
                    out["$key.fraction"] = if (lo != null && hi != null) {
                        (((v.toDoubleOrNull() ?: lo) - lo) / (hi - lo)).coerceIn(0.0, 1.0).toString()
                    } else "0"
                }
                "choice" -> out["$key.index"] = st.values.indexOf(v).toString()
            }
        }
    }

    /** Applies a mini-app action. Returns true when stored state changed. Anything unexpected is ignored. */
    fun applyOp(context: Context, spec: WidgetSpec, a: ActionSpec): Boolean {
        if (a.type !in STATE_OPS) return false
        if (a.type == "reset") {
            val targets = if (a.target == null) spec.state else spec.state.filter { it.id == a.target }
            targets.forEach { WidgetPrefs.setState(context, spec.id, it.id, null) }
            return targets.isNotEmpty()
        }
        val st = spec.state.firstOrNull { it.id == a.target } ?: return false
        val cur = stateValue(context, spec, st.id) ?: return false
        val next: String = when (a.type) {
            "inc", "dec" -> {
                if (st.type != "number") return false
                var v = (cur.toDoubleOrNull() ?: 0.0) + (a.by ?: 1.0) * (if (a.type == "dec") -1 else 1)
                if (st.wrap && st.min != null && st.max != null) {
                    if (v > st.max) v = st.min else if (v < st.min) v = st.max
                }
                SpecRules.fmt(SpecRules.clampState(st, v))
            }
            "toggle" -> if (st.type == "bool") (cur != "true").toString() else return false
            "cycle" -> if (st.type == "choice") st.values[(st.values.indexOf(cur) + 1).mod(st.values.size)] else return false
            "set" -> SpecRules.normalizeState(st, a.value?.content) ?: return false
            else -> return false
        }
        WidgetPrefs.setState(context, spec.id, st.id, next)
        return true
    }

    // --- feeds ---------------------------------------------------------------------------------

    private fun putFeeds(context: Context, spec: WidgetSpec, out: MutableMap<String, String>) {
        val allowed = WidgetPrefs.netAllowed(context, spec.id)
        for (f in spec.feeds) {
            val key = "feed.${f.id}"
            val cached = if (allowed) readCache(context, spec.id, f.id) else null
            out["$key.ok"] = (cached != null).toString()
            if (cached == null) continue
            val (time, values) = cached
            out["$key.stale"] = (System.currentTimeMillis() - time > f.ttl * 3L * 60_000L).toString()
            out["$key.updated"] = DateFormat.getTimeFormat(context).format(Date(time))
            values.forEach { (name, v) ->
                out["$key.$name"] = v
                v.toDoubleOrNull()?.takeIf { it.isFinite() }?.let { out["$key.$name.round"] = Math.round(it).toString() }
            }
        }
    }

    private fun readCache(context: Context, id: String, feed: String): Pair<Long, Map<String, String>>? {
        val raw = WidgetPrefs.feedCache(context, id, feed) ?: return null
        return runCatching {
            val o = JSONObject(raw)
            val v = o.getJSONObject("v")
            o.getLong("t") to v.keys().asSequence().associateWith { v.getString(it) }
        }.getOrNull()
    }

    /**
     * Fetches feeds that are due (or all of them with [force]). Does nothing unless the user allowed
     * internet access for this widget. Failures keep the last good data.
     */
    suspend fun refreshFeeds(context: Context, spec: WidgetSpec, force: Boolean = false) {
        if (spec.feeds.isEmpty() || !WidgetPrefs.netAllowed(context, spec.id)) return
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val base = HashMap<String, String>().also { putParams(context, spec, it) }
            for (f in spec.feeds) {
                val cached = readCache(context, spec.id, f.id)
                if (!force && cached != null && now - cached.first < f.ttl * 60_000L) continue
                try {
                    fetch(context, spec, f, base, now)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Keep whatever was cached.
                }
            }
        }
    }

    private fun fetch(context: Context, spec: WidgetSpec, f: FeedSpec, params: Map<String, String>, now: Long) {
        val token = Regex("\\{\\{([A-Za-z0-9_.]+)\\}\\}")
        val url = token.replace(f.url) { Uri.encode(params[it.groupValues[1]].orEmpty()) }
        val body = String(Http.get(url).bytes, Charsets.UTF_8)
        val root = SpecCodec.json.parseToJsonElement(body)
        val values = JSONObject()
        f.fields.forEach { (name, path) -> extract(root, path)?.let { values.put(name, it) } }
        if (values.length() == 0) return
        WidgetPrefs.setFeedCache(context, spec.id, f.id, JSONObject().put("t", now).put("v", values).toString())
    }

    private fun extract(root: JsonElement, path: String): String? {
        var cur: JsonElement? = root
        for (part in path.split('.')) {
            if (part.isEmpty()) return null
            val name = part.substringBefore('[')
            if (name.isNotEmpty()) cur = (cur as? JsonObject)?.get(name)
            var rest = part.substring(name.length)
            while (rest.startsWith("[")) {
                val end = rest.indexOf(']')
                if (end < 0) return null
                val idx = rest.substring(1, end).toIntOrNull() ?: return null
                cur = (cur as? JsonArray)?.getOrNull(idx)
                rest = rest.substring(end + 1)
            }
            if (cur == null) return null
        }
        val prim = cur as? JsonPrimitive ?: return null
        if (prim is JsonNull) return null
        return prim.content.take(200)
    }
}
