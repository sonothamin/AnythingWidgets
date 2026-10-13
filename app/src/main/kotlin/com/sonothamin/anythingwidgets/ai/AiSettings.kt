package com.sonothamin.anythingwidgets.ai

import android.content.Context

/** Whether the AI tab is shown. Generating widgets with a model is optional, so it can be hidden. */
object AiSettings {
    private const val KEY_TAB = "ai_tab_enabled"

    private fun prefs(c: Context) = c.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun tabEnabled(c: Context): Boolean = prefs(c).getBoolean(KEY_TAB, true)

    fun setTabEnabled(c: Context, enabled: Boolean) {
        prefs(c).edit().putBoolean(KEY_TAB, enabled).apply()
    }
}
