package com.sonothamin.anythingwidgets.widget

import android.content.Context
import com.sonothamin.anythingwidgets.widget.data.WidgetPrefs
import com.sonothamin.anythingwidgets.widget.spec.ThemeMode
import com.sonothamin.anythingwidgets.widget.spec.WidgetSpec

/** Which look a widget uses: the user's choice in the app, else the spec's own default, else the system. */
object WidgetTheme {
    fun mode(context: Context, spec: WidgetSpec): ThemeMode =
        WidgetPrefs.theme(context, spec.id)?.let { ThemeMode.of(it) } ?: ThemeMode.of(spec.theme)

    /** true = dark, false = light, null = follow the system. */
    fun forcedDark(context: Context, spec: WidgetSpec): Boolean? = mode(context, spec).forcedDark
}
