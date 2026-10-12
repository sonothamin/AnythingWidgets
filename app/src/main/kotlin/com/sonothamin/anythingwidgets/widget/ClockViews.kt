package com.sonothamin.anythingwidgets.widget

import android.content.Context
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import android.widget.TextClock
import com.sonothamin.anythingwidgets.R
import com.sonothamin.anythingwidgets.widget.spec.Node

/**
 * Live clocks. Glance can't tick, so these are the platform's own TextClock / AnalogClock,
 * which keep time by themselves on the home screen without any widget update.
 */
object ClockViews {
    private const val DEFAULT_FORMAT = "{h}:mm"

    /** "{hh}" / "{h}" become 12-hour or 24-hour hour patterns so one format works for both settings. */
    fun format12(pattern: String): String = pattern.replace("{hh}", "hh").replace("{h}", "h")
    fun format24(pattern: String): String = pattern.replace("{hh}", "HH").replace("{h}", "H")

    private fun analogLayout(variant: String?): Int? = when (variant) {
        "analog-dark" -> R.layout.clock_analog_dark
        "analog-light" -> R.layout.clock_analog_light
        "analog-dots" -> R.layout.clock_analog_dots
        else -> null
    }

    private fun digitalLayout(align: String?): Int = when (align) {
        "center" -> R.layout.clock_digital_center
        "end" -> R.layout.clock_digital_end
        else -> R.layout.clock_digital_start
    }

    fun build(context: Context, node: Node, argb: Int): RemoteViews {
        analogLayout(node.variant)?.let { return RemoteViews(context.packageName, it) }
        val pattern = node.text ?: DEFAULT_FORMAT
        return RemoteViews(context.packageName, digitalLayout(node.textAlign)).apply {
            setCharSequence(R.id.clock, "setFormat12Hour", format12(pattern))
            setCharSequence(R.id.clock, "setFormat24Hour", format24(pattern))
            setTextColor(R.id.clock, argb)
            setTextViewTextSize(R.id.clock, TypedValue.COMPLEX_UNIT_SP, node.size.toFloat())
        }
    }

    /** Inflates the same layouts for the in-app preview. */
    fun inflate(context: Context, node: Node, argb: Int): View {
        val inflater = android.view.LayoutInflater.from(context)
        analogLayout(node.variant)?.let { return inflater.inflate(it, null) }
        val pattern = node.text ?: DEFAULT_FORMAT
        val view = inflater.inflate(digitalLayout(node.textAlign), null)
        view.findViewById<TextClock>(R.id.clock).apply {
            format12Hour = format12(pattern)
            format24Hour = format24(pattern)
            setTextColor(argb)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, node.size.toFloat())
        }
        return view
    }
}
