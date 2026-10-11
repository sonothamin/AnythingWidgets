package com.sonothamin.anythingwidgets.widget.data

import android.content.Context

/** Per-widget user settings, kept on the device. */
object WidgetPrefs {
    private fun prefs(c: Context) = c.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)

    /** Whether the hourly sync may replace this widget with a newer registry version. */
    fun autoUpdate(c: Context, id: String): Boolean = prefs(c).getBoolean("auto_update.$id", true)

    fun setAutoUpdate(c: Context, id: String, enabled: Boolean) {
        prefs(c).edit().putBoolean("auto_update.$id", enabled).apply()
    }

    fun clear(c: Context, id: String) {
        prefs(c).edit().remove("auto_update.$id").apply()
    }
}
