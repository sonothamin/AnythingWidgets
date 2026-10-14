package com.sonothamin.anythingwidgets.ai

import android.content.Context
import com.sonothamin.anythingwidgets.widget.DataSnapshot
import com.sonothamin.anythingwidgets.widget.spec.NothingPalette

/**
 * Builds the system prompt. It is deliberately small: a core that every request needs, plus optional
 * sections (taps, settings, mini apps, online feeds, conditions) that are only added when the request
 * or the widget being edited actually uses them. Many providers have small context windows or token
 * rate limits, so every line here has to earn its place.
 */
internal object AiPrompt {
    enum class Tier {
        /** Core + the sections this request needs + a short example. */
        STANDARD,

        /** Bare minimum, used to retry after a provider said the prompt was too long. */
        MINIMAL,
    }

    // --- section selection ---------------------------------------------------------------------

    private fun words(vararg w: String) = Regex("\\b(" + w.joinToString("|") + ")", RegexOption.IGNORE_CASE)

    private val WANTS_ACTIONS = words(
        "tap", "click", "open", "launch", "call", "dial", "phone", "email", "mail", "map", "navigate",
        "timer", "alarm", "settings", "wi-?fi", "bluetooth", "link", "url", "website", "shortcut", "button",
    )
    private val WANTS_STATE = words(
        "counter", "count", "tally", "tracker", "habit", "checklist", "check", "tick", "toggle", "switch",
        "score", "increment", "streak", "cycle", "picker", "game", "todo", "to-do", "interactive", "reset",
        "water", "mood",
    )
    private val WANTS_PARAMS = words(
        "countdown", "until", "since", "days left", "birthday", "anniversary", "deadline", "custom", "option",
        "setting", "choose", "choice", "configur", "editable", "change", "personal", "name", "label", "goal",
        "target", "colou?r", "theme", "accent", "date",
    )
    private val WANTS_FEEDS = words(
        "weather", "forecast", "temperature", "rain", "online", "internet", "api", "live", "news", "headline",
        "price", "stock", "crypto", "bitcoin", "currency", "exchange", "trivia", "fact", "joke", "feed",
        "fetch", "sunrise", "sunset", "moon", "earthquake",
    )
    private val MINI_ACTIONS = Regex("\"type\"\\s*:\\s*\"(inc|dec|toggle|cycle|set|reset)\"")
    private val NAV_ACTIONS = Regex("\"type\"\\s*:\\s*\"(open_url|launch_app|dial|email|map|settings|timer|alarms)\"")

    private class Needs(request: String, current: String?) {
        private val spec = current.orEmpty()
        private fun has(key: String) = spec.contains("\"$key\"")

        val state = has("state") || MINI_ACTIONS.containsMatchIn(spec) || WANTS_STATE.containsMatchIn(request)
        val feeds = has("feeds") || WANTS_FEEDS.containsMatchIn(request)
        // Feeds read their inputs from params, so they always come together.
        val params = has("params") || feeds || WANTS_PARAMS.containsMatchIn(request)
        val actions = NAV_ACTIONS.containsMatchIn(spec) || WANTS_ACTIONS.containsMatchIn(request)
        val logic = has("visible") || has("bind") || state || params || feeds
    }

    // --- prompt --------------------------------------------------------------------------------

    /** Built per request so the bindings and palette always match what this build supports. */
    fun system(context: Context, request: String, currentRaw: String?, tier: Tier): String {
        val minimal = tier == Tier.MINIMAL
        val needs = if (minimal) null else Needs(request, currentRaw)
        val keys = DataSnapshot.build(context).keys
        return buildString {
            append(CORE)
            append("\n\nCOLORS\n").append(colors(minimal))
            append("\n\nBINDINGS\n").append(bindings(keys, minimal))
            if (needs != null) {
                if (needs.actions) append("\n\n").append(ACTIONS)
                if (needs.logic) append("\n\n").append(LOGIC)
                if (needs.params) append("\n\n").append(PARAMS)
                if (needs.state) append("\n\n").append(STATE)
                if (needs.feeds) append("\n\n").append(FEEDS)
                append("\n\n").append(EXAMPLE)
            }
        }
    }

    fun create(request: String) = "Create a widget: $request"

    fun refine(currentJson: String, request: String) =
        "Here is the current widget spec:\n$currentJson\n\nChange it as follows and return the complete updated spec: $request"

    private fun colors(minimal: Boolean): String {
        val adaptive = NothingPalette.adaptiveNames.joinToString(" ")
        val fixed = NothingPalette.fixedNames.joinToString(" ")
        val material = "Material tokens (follow wallpaper and light/dark): primary secondary tertiary (each also on<X>, " +
            "<x>Container, on<X>Container), surface onSurface surfaceVariant onSurfaceVariant background onBackground error outline."
        return if (minimal) {
            "$material Or Nothing palette: $adaptive $fixed. Or #RRGGBB. Keep text readable."
        } else {
            "$material\n" +
                "Nothing palette, adaptive (light and dark value, with chrome \"none\"): $adaptive. n.card = card background, " +
                "n.ink = text and lit dots, n.muted = secondary text, n.faint = unlit dots and tracks, n.ink06/18/60 = soft ink for dot art.\n" +
                "Nothing palette, fixed: $fixed.\n" +
                "Or #RRGGBB / #AARRGGBB. Keep text readable against its background."
        }
    }

    /** "date: weekday day month ..." instead of one {{date.weekday}} per key; far shorter. */
    private fun bindings(keys: Collection<String>, minimal: Boolean): String {
        val usable = if (minimal) keys.filter { it in ESSENTIAL } else keys.toList()
        val groups = usable.sorted().groupBy { if ('.' in it) it.substringBefore('.') else "" }
        val lines = groups.entries.joinToString("\n") { (group, names) ->
            if (group.isEmpty()) "other: " + names.joinToString(" ") { "{{$it}}" }
            else "$group: " + names.joinToString(" ") { it.substringAfter('.') }
        }
        return "Write {{group.name}} in text, pool, progress or pattern, e.g. {{date.day}}. Only these exist (group: names); " +
            "never invent keys, and there is no calendar, contacts, location or images:\n$lines\n" +
            "Values refresh hourly and on tap, so no live seconds (use a clock node for time)."
    }

    private val ESSENTIAL = setOf(
        "date.weekday", "date.weekday.short", "date.day", "date.month", "date.month.long", "date.year",
        "time.short", "battery.level", "battery.fraction", "year.pattern", "day.fraction",
    )

    private val CORE = """
You design Android home screen widgets for the AnythingWidgets app. A widget is a JSON spec: pure data, no code.
Reply with ONE JSON object and nothing else: no markdown, no code fences, no commentary.

SPEC
{"schema":2,"id":"slug","version":1,"name":"max 60 chars","description":"one sentence","categories":["max 5"],"tags":["max 10"],"chrome":"none"|"default","root":Node,"compact":Node,"large":Node?}
- Labels are 1-24 chars. root = default/wide layout (about 250x110dp). compact = under 180dp wide (about 110x110dp). large = from 180x180dp up (about 250x250dp), only if it really differs.
- Always give compact, and make it a real redesign for a small square, not a squeezed copy.
- chrome "none" removes the system card so root draws its own shape; omit it for a normal card.
- At most 100 nodes per layout, depth 10.

NODE: {"type":"row|column|box|text|spacer|progress|glyph|dotgrid|clock", ...}; every other field is optional.
- children: row, column, box only.
- Layout: padding 0-48, gap 0-32, radius 0-200, width/height 1-400 (dp), fill width|height|both, grow true (takes the remaining space in a row/column), justify start|center|end (main axis), align (cross axis; for box e.g. center, top, bottom-end).
- Colors: background, color, track.
- text: text (max 200 chars), size 8-200, bold, maxLines, textAlign start|center|end. Or pool [strings] with rotate day|hour|random to show one entry.
- glyph: big display text, like text, plus font dots (default)|serif|sans|mono.
- progress: progress "{{battery.fraction}}" (resolves to 0..1); track is the empty color.
- dotgrid: columns and rows (max 80 each, 800 dots total), width, height. Filled by progress, or by pattern: rows split by "/", '#' on, 'r' accent, '.' off, '_' blank, or bound like "{{year.pattern}}". color = lit, track = unlit, accent = 'r' dots.
- clock: variant digital|analog-dark|analog-light|analog-dots; digital takes text as a format like "HH:mm" or "EEE d MMM".
- action on any node: {"type":"open_app"} | {"type":"refresh"}.

STYLE: small, bold, uncluttered, one clear idea, 2-4 elements, padding 12-20, radius 24-32.
""".trim()

    private val ACTIONS = """
MORE TAP ACTIONS: {"type":"open_url","url":"https://..."} | {"type":"launch_app","package":"com.example"} | {"type":"dial","number":"+1555..."} | {"type":"email","to":"a@b.c"} | {"type":"map","query":"text"} | {"type":"settings","screen":"wifi|bluetooth|display|battery|sound|location|airplane|storage|apps|date|accessibility|all"} | {"type":"timer","seconds":300} | {"type":"alarms"}. url may use a text param, e.g. "{{param.link}}".
""".trim()

    private val LOGIC = """
CONDITIONS
- visible: node is drawn only when true. [!]key, or key OP literal with OP == != < <= > >=, joined by && and ||. E.g. "battery.level<20", "!param.compact". Two siblings with opposite conditions give if/else.
- bind: {"size":"{{param.fontSize}}"} overrides numbers (size, padding, gap, radius, width, height, columns, rows, maxLines) and bold from a binding.
""".trim()

    private val PARAMS = """
PARAMS: options the user edits in the app, read as {{param.id}}. Top-level "params":[...], max 12. Use them when someone might want to change a color, text, size, date or toggle.
{"id":"accent","type":"color","label":"Accent","default":"n.red"} | number {"min","max","step","unit"} | choice {"choices":[{"value":"a","label":"A"},{"value":"b"}]} | text {"maxLength"} | date (default "yyyy-MM-dd"; also {{param.id.daysUntil}} .daysSince .days .isPast .isToday .text) | toggle. Ids: letters/digits, start lowercase. A color param is used as "color":"{{param.accent}}" (also background, track, accent). number also gives .fraction; choice gives .label and .index.
""".trim()

    private val STATE = """
STATE (mini apps): values the widget changes itself when tapped, read as {{state.id}}. Top-level "state":[...].
{"id":"count","type":"number","default":0,"min":0,"max":99,"wrap":false} | bool | choice {"values":["a","b"]}. bool gives {{state.id.bit}} ("#" or ".", good for dotgrid patterns); number with min and max gives .fraction; choice gives .index.
Actions: {"type":"inc"|"dec","target":"id","by":1} | {"type":"toggle"|"cycle","target":"id"} | {"type":"set","target":"id","value":..} | {"type":"reset","target":"id?"}.
""".trim()

    private val FEEDS = """
FEEDS (online data), only because the person asked for live online data. Top-level "feeds":[...]:
{"id":"weather","ttl":30,"url":"https://api.open-meteo.com/v1/forecast?latitude={{param.lat}}&longitude={{param.lon}}&current=temperature_2m","fields":{"temp":"current.temperature_2m"}}
https only, a public host that needs no API key, only declared {{param.x}} values in the URL, never invent hosts or endpoints. Read as {{feed.id.temp}} (numbers also .round). Always handle the off state: show "Allow internet access in the app" with visible "!net.allowed", and data nodes with visible "feed.id.ok". The user enables access per widget.
""".trim()

    private val EXAMPLE = """
EXAMPLE
{"schema":2,"id":"battery-dots","version":1,"name":"Battery Dots","description":"Battery level as a dot bar.","categories":["System"],"tags":["battery"],"chrome":"none","root":{"type":"column","fill":"both","background":"n.dark","radius":32,"padding":16,"justify":"center","gap":10,"children":[{"type":"glyph","text":"{{battery.level}}%","size":36,"color":"n.white"},{"type":"dotgrid","columns":20,"rows":1,"width":200,"height":10,"progress":"{{battery.fraction}}","color":"n.white","track":"n.dot"}]},"compact":{"type":"column","fill":"both","background":"n.dark","radius":32,"padding":14,"justify":"center","align":"center","children":[{"type":"glyph","text":"{{battery.level}}","size":40,"color":"n.white"}]}}
""".trim()
}
