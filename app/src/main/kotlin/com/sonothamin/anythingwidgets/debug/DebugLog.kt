package com.sonothamin.anythingwidgets.debug

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Opt-in diagnostic log, kept in a small file so widget taps (which run outside the app UI) and the
 * app itself write to the same place. Off by default; when off, [log] does nothing at all.
 */
object DebugLog {
    enum class Level(val letter: Char) { DEBUG('D'), INFO('I'), WARN('W'), ERROR('E') }

    data class Entry(val time: Long, val level: Level, val tag: String, val message: String)

    private const val PREFS = "settings"
    private const val KEY_ENABLED = "debug_logging"
    private const val FILE = "debug.log"
    private const val MAX_BYTES = 256 * 1024L
    private val lock = Any()

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun file(c: Context) = File(c.applicationContext.filesDir, FILE)

    fun enabled(c: Context): Boolean = prefs(c).getBoolean(KEY_ENABLED, false)

    fun setEnabled(c: Context, on: Boolean) {
        prefs(c).edit().putBoolean(KEY_ENABLED, on).apply()
        if (on) log(c, Level.INFO, "debug", "Logging turned on")
    }

    fun d(c: Context, tag: String, msg: String) = log(c, Level.DEBUG, tag, msg)
    fun i(c: Context, tag: String, msg: String) = log(c, Level.INFO, tag, msg)
    fun w(c: Context, tag: String, msg: String) = log(c, Level.WARN, tag, msg)
    fun e(c: Context, tag: String, msg: String, t: Throwable? = null) =
        log(c, Level.ERROR, tag, if (t == null) msg else "$msg: ${t::class.java.simpleName}: ${t.message}")

    fun log(c: Context, level: Level, tag: String, msg: String) {
        if (!enabled(c)) return
        Log.println(
            when (level) {
                Level.DEBUG -> Log.DEBUG
                Level.INFO -> Log.INFO
                Level.WARN -> Log.WARN
                Level.ERROR -> Log.ERROR
            },
            "AW/$tag",
            msg,
        )
        runCatching {
            synchronized(lock) {
                val f = file(c)
                // One entry per line: tab-separated, with newlines in the message escaped.
                f.appendText("${System.currentTimeMillis()}\t${level.letter}\t$tag\t${msg.replace("\n", "\\n")}\n")
                if (f.length() > MAX_BYTES) {
                    // Keep the newest half, starting at a line boundary.
                    val text = f.readText()
                    val cut = text.indexOf('\n', text.length / 2)
                    f.writeText(if (cut >= 0) text.substring(cut + 1) else "")
                }
            }
        }
    }

    /** Oldest first. */
    fun read(c: Context): List<Entry> = synchronized(lock) {
        val f = file(c)
        if (!f.exists()) return emptyList()
        f.readLines().mapNotNull { line ->
            val p = line.split('\t', limit = 4)
            if (p.size < 4) return@mapNotNull null
            Entry(
                time = p[0].toLongOrNull() ?: return@mapNotNull null,
                level = Level.entries.firstOrNull { it.letter.toString() == p[1] } ?: return@mapNotNull null,
                tag = p[2],
                message = p[3].replace("\\n", "\n"),
            )
        }
    }

    fun clear(c: Context) {
        synchronized(lock) { runCatching { file(c).delete() } }
    }
}
