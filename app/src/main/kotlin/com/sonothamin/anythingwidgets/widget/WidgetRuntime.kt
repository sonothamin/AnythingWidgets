package com.sonothamin.anythingwidgets.widget

import android.content.Context
import android.net.Uri
import android.text.Html
import android.text.format.DateFormat
import com.sonothamin.anythingwidgets.debug.DebugLog
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
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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
        // A daily value set on an earlier day counts as unset.
        val stale = st.daily && WidgetPrefs.stateDay(context, spec.id, id) != LocalDate.now().toString()
        val stored = if (stale) null else WidgetPrefs.state(context, spec.id, id)
        return SpecRules.normalizeState(st, stored) ?: SpecRules.stateDefault(st)
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

    private class FeedCache(val time: Long, val items: List<Map<String, String>>)

    private val TOKEN = Regex("\\{\\{([A-Za-z0-9_.]+)\\}\\}")
    private val NON_KEY = Regex("[^a-z0-9]+")
    private val ENTITY = Regex("&(#\\d+|#[xX][0-9a-fA-F]+|[A-Za-z][A-Za-z0-9]*);")

    /**
     * The feed URL with params filled in. A query pair whose value comes out empty or as "any" is left
     * out, so an optional filter can say "no preference".
     */
    private fun feedUrl(f: FeedSpec, params: Map<String, String>): String {
        val filled = TOKEN.replace(f.url) { Uri.encode(params[it.groupValues[1]].orEmpty()) }
        val q = filled.indexOf('?')
        if (q < 0) return filled
        val kept = filled.substring(q + 1).split('&').filter { pair ->
            val eq = pair.indexOf('=')
            eq < 0 || pair.substring(eq + 1).let { it.isNotEmpty() && it != "any" }
        }
        return filled.substring(0, q) + if (kept.isEmpty()) "" else "?" + kept.joinToString("&")
    }

    private fun daysSince(time: Long): Int =
        ChronoUnit.DAYS.between(Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).toLocalDate(), LocalDate.now())
            .coerceAtLeast(0).toInt()

    private fun putFeeds(context: Context, spec: WidgetSpec, out: MutableMap<String, String>) {
        val allowed = WidgetPrefs.netAllowed(context, spec.id)
        for (f in spec.feeds) {
            val key = "feed.${f.id}"
            val cached = if (allowed) readCache(context, spec.id, f.id, feedUrl(f, out)) else null
            out["$key.ok"] = (cached != null).toString()
            if (cached == null) continue
            // A list feed shows the next cached item each day, wrapping around if the cache outlives its list.
            val n = cached.items.size
            val idx = if (n > 1) daysSince(cached.time).mod(n) else 0
            out["$key.stale"] = (System.currentTimeMillis() - cached.time > f.ttl * 3L * 60_000L).toString()
            out["$key.updated"] = DateFormat.getTimeFormat(context).format(Date(cached.time))
            out["$key.count"] = n.toString()
            out["$key.index"] = (idx + 1).toString()
            cached.items[idx].forEach { (name, v) ->
                out["$key.$name"] = v
                // The part after a colon ("Entertainment: Music" -> "Music") and a lowercase id for conditions.
                val short = v.substringAfterLast(':').trim()
                out["$key.$name.short"] = short
                out["$key.$name.key"] = short.lowercase().replace(NON_KEY, "-").trim('-').take(32)
                v.toDoubleOrNull()?.takeIf { it.isFinite() }?.let { out["$key.$name.round"] = Math.round(it).toString() }
            }
        }
    }

    /** Null when nothing is cached, it can't be read, or it was fetched for different param values. */
    private fun readCache(context: Context, id: String, feed: String, url: String): FeedCache? {
        val raw = WidgetPrefs.feedCache(context, id, feed) ?: return null
        return runCatching {
            val o = JSONObject(raw)
            val fetchedFor = o.optString("u")
            if (fetchedFor.isNotEmpty() && fetchedFor != url) return@runCatching null
            fun toMap(v: JSONObject) = v.keys().asSequence().associateWith { v.getString(it) }
            val items = if (o.has("i")) {
                val arr = o.getJSONArray("i")
                (0 until arr.length()).map { toMap(arr.getJSONObject(it)) }
            } else {
                listOf(toMap(o.getJSONObject("v")))
            }
            if (items.isEmpty()) null else FeedCache(o.getLong("t"), items)
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
                val url = feedUrl(f, base)
                val cached = readCache(context, spec.id, f.id, url)
                val used = cached != null && f.list != null && daysSince(cached.time) >= cached.items.size
                if (!force && cached != null && !used && now - cached.time < f.ttl * 60_000L) continue
                try {
                    fetch(context, spec, f, url, now)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Keep whatever was cached.
                    DebugLog.w(context, "feed", "${spec.id}/${f.id} fetch failed: ${e::class.java.simpleName}: ${e.message}")
                }
            }
        }
    }

    private fun fetch(context: Context, spec: WidgetSpec, f: FeedSpec, url: String, now: Long) {
        val body = String(Http.get(url).bytes, Charsets.UTF_8)
        val root = SpecCodec.json.parseToJsonElement(body)
        val cache = JSONObject().put("t", now).put("u", url)
        if (f.list == null) {
            val values = readFields(root, f.fields)
            if (values.length() == 0) return
            cache.put("v", values)
        } else {
            val elements: List<JsonElement> = (walk(root, f.list) as? JsonArray) ?: emptyList()
            val items = JSONArray()
            for (el in elements) {
                if (items.length() >= f.keep) break
                val values = readFields(el, f.fields)
                if (values.length() > 0) items.put(values)
            }
            if (items.length() == 0) {
                DebugLog.w(context, "feed", "${spec.id}/${f.id}: $url returned no usable items")
                return
            }
            cache.put("i", items)
        }
        WidgetPrefs.setFeedCache(context, spec.id, f.id, cache.toString())
        DebugLog.i(context, "feed", "${spec.id}/${f.id}: cached ${if (f.list != null) cache.getJSONArray("i").length() else 1} item(s) from $url")
    }

    private fun readFields(root: JsonElement, fields: Map<String, String>): JSONObject {
        val values = JSONObject()
        fields.forEach { (name, path) -> extract(root, path)?.let { values.put(name, it) } }
        return values
    }

    private fun walk(root: JsonElement, path: String): JsonElement? {
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
        return cur
    }

    private fun extract(root: JsonElement, path: String): String? {
        val prim = walk(root, path) as? JsonPrimitive ?: return null
        if (prim is JsonNull) return null
        return decodeEntities(prim.content).take(200)
    }

    /** Some APIs send HTML entities (&quot; &#039;) inside plain text. Tags are never interpreted. */
    private fun decodeEntities(s: String): String =
        if (!ENTITY.containsMatchIn(s)) s
        else Html.fromHtml(s.replace("<", "&lt;"), Html.FROM_HTML_MODE_LEGACY).toString().trim()
}
