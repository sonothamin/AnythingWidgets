package com.sonothamin.anythingwidgets.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.text.style.LeadingMarginSpan
import androidx.core.content.res.ResourcesCompat
import com.sonothamin.anythingwidgets.R
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Bitmap drawing for the pieces Glance can't do natively: custom-font text ("glyph") and dot grids.
 * The in-app preview calls the same functions, so what you preview is what the widget draws.
 * Sizes are in dp; bitmaps are created at device pixel size so they show 1:1.
 */
object DotArt {
    private const val MAX_PIXELS = 2_000_000

    private fun typeface(context: Context, font: String?, bold: Boolean): Typeface = when (font) {
        "serif" -> Typeface.create(Typeface.SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
        "oldstandard" -> ResourcesCompat.getFont(context, R.font.old_standard_tt) ?: Typeface.SERIF
        "mono" -> Typeface.create(Typeface.MONOSPACE, if (bold) Typeface.BOLD else Typeface.NORMAL)
        "sans" -> ResourcesCompat.getFont(context, if (bold) R.font.geist_semibold else R.font.geist_regular)
            ?: Typeface.DEFAULT
        else -> ResourcesCompat.getFont(context, R.font.ndot_47) ?: Typeface.MONOSPACE
    }

    /** The first letter, drawn large in Old Standard and spanning [lines] lines of the text beside it. */
    private class DropCapSpan(
        private val initial: String,
        private val lines: Int,
        private val capPaint: TextPaint,
        private val gapPx: Int,
        private val fallbackStep: Float,
    ) : LeadingMarginSpan.LeadingMarginSpan2 {
        private val capWidth = ceil(capPaint.measureText(initial)).toInt()

        override fun getLeadingMarginLineCount() = lines

        override fun getLeadingMargin(first: Boolean) = if (first) capWidth + gapPx else 0

        override fun drawLeadingMargin(
            c: Canvas, p: Paint, x: Int, dir: Int, top: Int, baseline: Int, bottom: Int,
            text: CharSequence, start: Int, end: Int, first: Boolean, layout: Layout?,
        ) {
            if (start != 0) return // called once per line; draw with the first one
            val step = if (layout != null && layout.lineCount > 1) {
                (layout.getLineBaseline(1) - layout.getLineBaseline(0)).toFloat()
            } else fallbackStep
            c.drawText(initial, x.toFloat(), baseline + (lines - 1) * step, capPaint)
        }
    }

    /**
     * Text drawn with a bundled font, trimmed to its ink so layouts line up predictably.
     * Wraps at [maxWidthDp] and keeps at most [maxLines] lines. [align] is "start", "center" or "end".
     * [minSizeDp] lets the text shrink from [sizeDp] until it fits [maxLines]; [dropcap] draws the first
     * letter in Old Standard over that many lines (start-aligned text only).
     */
    fun glyph(
        context: Context,
        text: String,
        font: String?,
        bold: Boolean,
        sizeDp: Float,
        argb: Int,
        align: String?,
        maxWidthDp: Float,
        maxLines: Int,
        dropcap: Int? = null,
        minSizeDp: Float? = null,
    ): Bitmap {
        val density = context.resources.displayMetrics.density
        val alignment = when (align) {
            "center" -> Layout.Alignment.ALIGN_CENTER
            "end" -> Layout.Alignment.ALIGN_OPPOSITE
            else -> Layout.Alignment.ALIGN_NORMAL
        }
        val lineMult = if (font == null || font == "dots") 0.92f else 1f
        val capped = dropcap != null && text.length > 1 && text[0].isLetterOrDigit()
        val capLines = dropcap ?: 2
        val capFace = typeface(context, "oldstandard", false)
        val capRatio = TextPaint().apply { typeface = capFace; textSize = 100f }.let {
            val r = Rect()
            it.getTextBounds("H", 0, 1, r)
            r.height() / 100f
        }
        var capExtra = 0

        fun build(size: Float, widthPx: Int, limit: Boolean): StaticLayout {
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                this.typeface = typeface(context, font, bold)
                textSize = size * density
                color = argb
            }
            val body: CharSequence = if (capped) {
                val rest = SpannableString(text.substring(1))
                val step = paint.fontSpacing * lineMult
                val bodyCap = Rect().also { paint.getTextBounds("H", 0, 1, it) }.height()
                val capPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    typeface = capFace
                    color = argb
                    textSize = ((capLines - 1) * step + bodyCap) / capRatio
                }
                rest.setSpan(
                    DropCapSpan(text.substring(0, 1), capLines, capPaint, (3 * density).toInt(), step),
                    0, rest.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE,
                )
                capExtra = ((capLines - 1) * step + bodyCap + 2 * density).toInt()
                rest
            } else text
            return StaticLayout.Builder.obtain(body, 0, body.length, paint, max(1, widthPx))
                .setAlignment(alignment)
                .setIncludePad(false)
                .setLineSpacing(0f, lineMult)
                .apply { if (limit) setMaxLines(maxLines.coerceAtLeast(1)).setEllipsize(TextUtils.TruncateAt.END) }
                .build()
        }

        val maxPx = max(1, (maxWidthDp * density).toInt())
        val floor = min(minSizeDp ?: sizeDp, sizeDp)
        var size = sizeDp
        // Shrink until the whole text fits; at the floor, fall back to cutting it with an ellipsis.
        while (size > floor && build(size, maxPx, limit = false).lineCount > maxLines.coerceAtLeast(1)) size -= 0.5f
        val measured = build(size, maxPx, limit = true)
        val laid = if (capped) {
            measured
        } else {
            val inkWidth = (0 until measured.lineCount).maxOfOrNull { ceil(measured.getLineWidth(it)).toInt() } ?: 1
            build(size, inkWidth + 2, limit = true)
        }

        val pad = (2 * density).toInt()
        val w = laid.width + pad * 2
        val h = max(laid.height, if (capped) capExtra else 0) + pad * 2
        val ratio = if (w.toLong() * h > MAX_PIXELS) kotlin.math.sqrt(MAX_PIXELS.toDouble() / (w.toLong() * h)).toFloat() else 1f
        val full = Bitmap.createBitmap(max(1, (w * ratio).toInt()), max(1, (h * ratio).toInt()), Bitmap.Config.ARGB_8888)
        Canvas(full).apply {
            scale(ratio, ratio)
            translate(pad.toFloat(), pad.toFloat())
            laid.draw(this)
        }
        return trim(full)
    }

    /** Crops fully transparent margins. */
    private fun trim(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        var top = h
        var bottom = -1
        var left = w
        var right = -1
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                if (Color.alpha(px[row + x]) > 8) {
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                    if (x < left) left = x
                    if (x > right) right = x
                }
            }
        }
        if (bottom < 0) return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        return Bitmap.createBitmap(src, left, top, right - left + 1, bottom - top + 1)
    }

    /**
     * A [columns] x [rows] grid of round dots filling [widthDp] x [heightDp].
     * With a [pattern] (# on, r accent, . off, _ blank; whitespace and '/' ignored) each dot is set by hand;
     * otherwise the first [fraction] of the dots (row by row) are on.
     */
    fun dotGrid(
        context: Context,
        widthDp: Float,
        heightDp: Float,
        columns: Int,
        rows: Int,
        pattern: String?,
        fraction: Float,
        on: Int,
        off: Int,
        accent: Int,
    ): Bitmap {
        val density = context.resources.displayMetrics.density
        var w = max(1, (widthDp * density).roundToInt())
        var h = max(1, (heightDp * density).roundToInt())
        if (w.toLong() * h > MAX_PIXELS) {
            val s = kotlin.math.sqrt(MAX_PIXELS.toDouble() / (w.toLong() * h)).toFloat()
            w = max(1, (w * s).toInt())
            h = max(1, (h * s).toInt())
        }
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val cellW = w / columns.toFloat()
        val cellH = h / rows.toFloat()
        val radius = min(cellW, cellH) * 0.31f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val cells = pattern?.filter { it == '#' || it == 'r' || it == '.' || it == '_' }
        val total = columns * rows
        val lit = (fraction.coerceIn(0f, 1f) * total).roundToInt()
        for (i in 0 until total) {
            val state = if (cells != null) cells.getOrElse(i) { '.' } else if (i < lit) '#' else '.'
            if (state == '_') continue
            paint.color = when (state) {
                '#' -> on
                'r' -> accent
                else -> off
            }
            canvas.drawCircle((i % columns + 0.5f) * cellW, (i / columns + 0.5f) * cellH, radius, paint)
        }
        return bmp
    }
}
