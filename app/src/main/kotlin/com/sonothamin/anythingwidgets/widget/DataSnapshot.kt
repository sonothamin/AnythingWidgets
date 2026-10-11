package com.sonothamin.anythingwidgets.widget

import android.content.Context
import android.os.BatteryManager
import android.text.format.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Local, non-sensitive values a spec can bind to with {{key}}. */
object DataSnapshot {
    fun build(context: Context): Map<String, String> {
        val now = Date()
        val loc = Locale.getDefault()
        val battery = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
        val cal = Calendar.getInstance()
        val dayOfYear = cal.get(Calendar.DAY_OF_YEAR)
        val daysInYear = cal.getActualMaximum(Calendar.DAY_OF_YEAR)
        return mapOf(
            "time.short" to DateFormat.getTimeFormat(context).format(now),
            "date.weekday" to SimpleDateFormat("EEEE", loc).format(now),
            "date.weekday.short" to SimpleDateFormat("EEE", loc).format(now),
            "date.day" to SimpleDateFormat("d", loc).format(now),
            "date.month" to SimpleDateFormat("MMM", loc).format(now),
            "date.month.long" to SimpleDateFormat("MMMM", loc).format(now),
            "date.year" to SimpleDateFormat("yyyy", loc).format(now),
            "date.dayOfYear" to dayOfYear.toString(),
            "date.daysInYear" to daysInYear.toString(),
            "date.year.fraction" to (dayOfYear / daysInYear.toFloat()).toString(),
            "date.year.percent" to (dayOfYear * 100 / daysInYear).toString(),
            "battery.level" to level.toString(),
            "battery.fraction" to (level / 100f).toString(),
        )
    }

    private val token = Regex("\\{\\{([A-Za-z0-9_.]+)\\}\\}")

    fun apply(template: String, data: Map<String, String>): String =
        token.replace(template) { data[it.groupValues[1]].orEmpty() }
}
