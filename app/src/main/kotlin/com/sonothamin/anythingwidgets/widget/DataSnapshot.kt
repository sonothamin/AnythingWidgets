package com.sonothamin.anythingwidgets.widget

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.text.format.DateFormat
import com.sonothamin.anythingwidgets.widget.spec.Node
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.random.Random

/** Local, non-sensitive values a spec can bind to with {{key}}. */
object DataSnapshot {
    fun build(context: Context): Map<String, String> {
        val now = Date()
        val loc = Locale.getDefault()
        val battery = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
        val charging = isCharging(context)
        val cal = Calendar.getInstance()
        val dayOfYear = cal.get(Calendar.DAY_OF_YEAR)
        val daysInYear = cal.getActualMaximum(Calendar.DAY_OF_YEAR)
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minuteOfDay = hour * 60 + cal.get(Calendar.MINUTE)
        val weekday = SimpleDateFormat("EEEE", loc).format(now)
        val weekdayShort = SimpleDateFormat("EEE", loc).format(now)
        val month = SimpleDateFormat("MMM", loc).format(now)
        val monthLong = SimpleDateFormat("MMMM", loc).format(now)
        val dice = Random.nextInt(1, 7)
        val heads = Random.nextBoolean()
        val today = LocalDate.now()

        return storage() + mapOf(
            "time.short" to DateFormat.getTimeFormat(context).format(now),
            "date.weekday" to weekday,
            "date.weekday.short" to weekdayShort,
            "date.weekday.upper" to weekday.uppercase(loc),
            "date.weekday.short.upper" to weekdayShort.uppercase(loc),
            "date.day" to SimpleDateFormat("d", loc).format(now),
            "date.day.pad" to SimpleDateFormat("dd", loc).format(now),
            "date.month" to month,
            "date.month.upper" to month.uppercase(loc),
            "date.month.long" to monthLong,
            "date.month.long.upper" to monthLong.uppercase(loc),
            "date.month.num" to SimpleDateFormat("M", loc).format(now),
            "date.month.num.pad" to SimpleDateFormat("MM", loc).format(now),
            "date.year" to SimpleDateFormat("yyyy", loc).format(now),
            "date.dayOfYear" to dayOfYear.toString(),
            "date.daysInYear" to daysInYear.toString(),
            "date.year.fraction" to (dayOfYear / daysInYear.toFloat()).toString(),
            "date.year.percent" to (dayOfYear * 100 / daysInYear).toString(),
            "date.year.daysLeft" to (daysInYear - dayOfYear).toString(),
            "date.week" to cal.get(Calendar.WEEK_OF_YEAR).toString(),
            "date.greeting" to when (hour) {
                in 5..11 -> "GOOD MORNING"
                in 12..16 -> "GOOD AFTERNOON"
                in 17..21 -> "GOOD EVENING"
                else -> "GOOD NIGHT"
            },
            "day.fraction" to (minuteOfDay / 1440f).toString(),
            "day.percent" to (minuteOfDay * 100 / 1440).toString(),
            "day.hour" to hour.toString(),
            // 24 hours: elapsed '#', current 'r', still to come '.'
            "day.pattern" to (0 until 24).joinToString("") { if (it < hour) "#" else if (it == hour) "r" else "." },
            "year.pattern" to yearPattern(today),
            "month.pattern" to monthPattern(today, cal.firstDayOfWeek),
            "battery.level" to level.toString(),
            "battery.level.pad" to level.toString().padStart(2, '0'),
            "battery.fraction" to (level / 100f).toString(),
            "battery.status" to if (charging) "CHARGING" else "",
            "battery.face" to batteryFace(level),
            "battery.mood" to when {
                charging -> "NOM NOM NOM"
                level >= 70 -> "FEELING GREAT"
                level >= 35 -> "DOING FINE"
                level >= 15 -> "A BIT LOW"
                else -> "FEED ME"
            },
            "battery.charging" to charging.toString(),
            "date.iso" to today.toString(),
            "date.weekend" to (cal.get(Calendar.DAY_OF_WEEK).let { it == Calendar.SATURDAY || it == Calendar.SUNDAY }).toString(),
            "time.hour24" to hour.toString(),
            "device.model" to Build.MODEL.orEmpty(),
            "device.brand" to Build.MANUFACTURER.orEmpty().replaceFirstChar { it.uppercase() },
            "device.android" to Build.VERSION.RELEASE.orEmpty(),
            "network.type" to networkType(context),
            "network.online" to (networkType(context) != "OFFLINE").toString(),
            "random.dice" to dice.toString(),
            "random.dice.pattern" to DICE[dice - 1],
            "random.coin" to if (heads) "HEADS" else "TAILS",
            "random.percent" to Random.nextInt(0, 101).toString(),
            "tick.day" to today.toEpochDay().toString(),
            "tick.hour" to (System.currentTimeMillis() / 3_600_000L).toString(),
            "tick.random" to Random.nextInt(0, 1_000_000).toString(),
        )
    }

    private fun networkType(context: Context): String = runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        when {
            caps == null -> "OFFLINE"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WI-FI"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "MOBILE"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
            else -> "ONLINE"
        }
    }.getOrDefault("UNKNOWN")

    /** Internal storage: fractions, a percent and sizes in GB. */
    private fun storage(): Map<String, String> = runCatching {
        val st = StatFs(Environment.getDataDirectory().path)
        val total = st.blockCountLong * st.blockSizeLong
        val free = st.availableBlocksLong * st.blockSizeLong
        val used = (total - free).coerceAtLeast(0L)
        val gb = 1024.0 * 1024.0 * 1024.0
        val fraction = if (total > 0) used.toDouble() / total else 0.0
        mapOf(
            "storage.used.fraction" to fraction.toString(),
            "storage.used.percent" to Math.round(fraction * 100).toString(),
            "storage.free.gb" to "%.1f".format(Locale.US, free / gb),
            "storage.total.gb" to "%.0f".format(Locale.US, total / gb),
        )
    }.getOrDefault(emptyMap())

    private fun isCharging(context: Context): Boolean {
        val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return false
        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    }

    /** 3x3 dice faces, row by row. */
    private val DICE = listOf(
        "....#....", "#.......#", "#...#...#", "#.#...#.#", "#.#.#.#.#", "#.##.##.#",
    )

    /** 9x9 face that gets sadder as the battery drains; the eyes go red when it is nearly flat. */
    private fun batteryFace(level: Int): String {
        val happy = listOf(
            ".........", ".##...##.", ".##...##.", ".........", "#.......#", ".#.....#.", "..#####..", ".........", ".........",
        )
        val flat = listOf(
            ".........", ".##...##.", ".##...##.", ".........", ".........", ".#######.", ".........", ".........", ".........",
        )
        val sad = listOf(
            ".........", ".##...##.", ".##...##.", ".........", ".........", "..#####..", ".#.....#.", "#.......#", ".........",
        )
        val face = when {
            level >= 60 -> happy
            level >= 30 -> flat
            else -> sad
        }.joinToString("")
        // Eyes are the first two rows of '#': turn them red when nearly flat.
        return if (level < 15) face.substring(0, 27).replace('#', 'r') + face.substring(27) else face
    }

    /** 12 months x 31 days. Past days '#', today 'r', future '.', non-existent days '_'. */
    private fun yearPattern(today: LocalDate): String = buildString {
        for (m in 1..12) {
            val len = today.withMonth(m).lengthOfMonth()
            for (d in 1..31) {
                if (d > len) {
                    append('_')
                    continue
                }
                val date = LocalDate.of(today.year, m, d)
                append(
                    when {
                        date.isBefore(today) -> '#'
                        date == today -> 'r'
                        else -> '.'
                    },
                )
            }
        }
    }

    /** 6 weeks x 7 days. Days already gone '.', today 'r', days still to come '#', outside the month '_'. */
    private fun monthPattern(today: LocalDate, firstDayOfWeek: Int): String {
        val first = today.withDayOfMonth(1)
        // Calendar: Sunday = 1 .. Saturday = 7. java.time: Monday = 1 .. Sunday = 7.
        val firstCal = first.dayOfWeek.value % 7 + 1
        val offset = (firstCal - firstDayOfWeek + 7) % 7
        val len = today.lengthOfMonth()
        return buildString {
            for (i in 0 until 42) {
                val d = i - offset + 1
                append(
                    when {
                        d < 1 || d > len -> '_'
                        d < today.dayOfMonth -> '.'
                        d == today.dayOfMonth -> 'r'
                        else -> '#'
                    },
                )
            }
        }
    }

    private val token = Regex("\\{\\{([A-Za-z0-9_.]+)\\}\\}")

    fun apply(template: String, data: Map<String, String>): String =
        token.replace(template) { data[it.groupValues[1]].orEmpty() }

    /** The text a node shows: one entry of its pool (changing daily, hourly or on every refresh), or its own text. */
    fun textOf(node: Node, data: Map<String, String>): String {
        if (node.pool.isNotEmpty()) {
            val seed = when (node.rotate) {
                "hour" -> data["tick.hour"]
                "random" -> data["tick.random"]
                else -> data["tick.day"]
            }?.toLongOrNull() ?: 0L
            return apply(node.pool[Math.floorMod(seed, node.pool.size.toLong()).toInt()], data)
        }
        return apply(node.text.orEmpty(), data)
    }
}
