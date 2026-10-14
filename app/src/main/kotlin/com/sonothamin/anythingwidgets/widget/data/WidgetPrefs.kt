package com.sonothamin.anythingwidgets.widget.data

import android.content.Context

/** Per-widget user settings, kept on the device. */
object WidgetPrefs {
    private fun prefs(c: Context) = c.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)

    /** Parameter values, mini-app state, feed consent and cached feed data. Keys are "<widgetId>|<kind>|<name>". */
    private fun rt(c: Context) = c.getSharedPreferences("widget_runtime", Context.MODE_PRIVATE)

    /** Whether the hourly sync may replace this widget with a newer registry version. */
    fun autoUpdate(c: Context, id: String): Boolean = prefs(c).getBoolean("auto_update.$id", true)

    fun setAutoUpdate(c: Context, id: String, enabled: Boolean) {
        prefs(c).edit().putBoolean("auto_update.$id", enabled).apply()
    }

    // --- appearance ----------------------------------------------------------------------------

    /** The user's choice of "system", "dark" or "light" for this widget; null means use the spec's default. */
    fun theme(c: Context, id: String): String? = rt(c).getString("$id|theme", null)

    fun setTheme(c: Context, id: String, mode: String?) = put(c, "$id|theme", mode)

    // --- parameters ----------------------------------------------------------------------------

    fun param(c: Context, id: String, name: String): String? = rt(c).getString("$id|p|$name", null)

    fun setParam(c: Context, id: String, name: String, value: String?) = put(c, "$id|p|$name", value)

    fun resetParams(c: Context, id: String) = removeKind(c, id, "p")

    // --- mini-app state ------------------------------------------------------------------------

    fun state(c: Context, id: String, name: String): String? = rt(c).getString("$id|s|$name", null)

    fun setState(c: Context, id: String, name: String, value: String?) {
        put(c, "$id|s|$name", value)
        put(c, "$id|sd|$name", if (value == null) null else java.time.LocalDate.now().toString())
    }

    /** The day (yyyy-MM-dd) a state value was last set, for `daily` state. */
    fun stateDay(c: Context, id: String, name: String): String? = rt(c).getString("$id|sd|$name", null)

    fun resetState(c: Context, id: String) {
        removeKind(c, id, "s")
        removeKind(c, id, "sd")
    }

    // --- internet access -----------------------------------------------------------------------

    /** Off until the user allows it for this widget. */
    fun netAllowed(c: Context, id: String): Boolean = rt(c).getBoolean("$id|net", false)

    fun setNetAllowed(c: Context, id: String, allowed: Boolean) {
        rt(c).edit().putBoolean("$id|net", allowed).apply()
        if (!allowed) removeKind(c, id, "f")
    }

    fun feedCache(c: Context, id: String, feed: String): String? = rt(c).getString("$id|f|$feed", null)

    fun setFeedCache(c: Context, id: String, feed: String, json: String?) = put(c, "$id|f|$feed", json)

    // --- housekeeping --------------------------------------------------------------------------

    fun clear(c: Context, id: String) {
        prefs(c).edit().remove("auto_update.$id").apply()
        val r = rt(c)
        val edit = r.edit()
        r.all.keys.filter { it.startsWith("$id|") }.forEach { edit.remove(it) }
        edit.apply()
    }

    private fun put(c: Context, key: String, value: String?) {
        rt(c).edit().apply { if (value == null) remove(key) else putString(key, value) }.apply()
    }

    private fun removeKind(c: Context, id: String, kind: String) {
        val r = rt(c)
        val edit = r.edit()
        r.all.keys.filter { it.startsWith("$id|$kind|") }.forEach { edit.remove(it) }
        edit.apply()
    }
}
