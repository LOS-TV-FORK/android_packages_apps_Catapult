/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.tv.launcher.model

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.DisplayMetrics
import android.util.LruCache
import org.lineageos.tv.launcher.utils.AppManager.uninstallable

open class LeanbackAppInfo : Launchable {
    var banner: Drawable?
    protected val packageManager: PackageManager = context.packageManager
    private val applicationInfo: ApplicationInfo

    constructor(resolveInfo: ResolveInfo, context: Context) : super(
        resolveInfo.loadLabel(context.packageManager).toString(),
        resolveInfo.activityInfo.packageName,
        resolveInfo.loadIcon(context.packageManager),
        context
    ) {
        applicationInfo = resolveInfo.activityInfo.applicationInfo
        banner = resolveInfo.activityInfo.loadBanner(packageManager)
            ?: resolveInfo.activityInfo.applicationInfo.loadBanner(packageManager)
            ?: TvBannerGenerator.get(
                resolveInfo.activityInfo.applicationInfo, packageManager, context.resources
            )
    }

    constructor(app: ApplicationInfo, context: Context) : super(
        app.loadLabel(context.packageManager).toString(),
        app.packageName,
        app.loadIcon(context.packageManager),
        context
    ) {
        applicationInfo = app
        banner = app.loadBanner(packageManager)
            ?: TvBannerGenerator.get(app, packageManager, context.resources)
    }

    override fun setIntent() = packageManager.getLeanbackLaunchIntentForPackage(packageName)

    fun isUninstallable(): Boolean {
        return uninstallable(applicationInfo, context)
    }
}

/**
 * Builds a TV banner for an app that does not ship one: a 16:9 card with a dark background
 * tinted with the dominant colour of the app icon, the icon on the left and the app name
 * fitted beside it.
 */
private object TvBannerGenerator {

    /** The size Android TV asks a banner to be, at xxxhdpi. */
    private const val WIDTH = 640
    private const val HEIGHT = 360

    /** The density those pixels are at, so the banner scales like any other asset. */
    private const val BANNER_DENSITY = DisplayMetrics.DENSITY_XHIGH

    /** How much of the banner height the icon takes. */
    private const val ICON_PERCENT = 53

    /** A place the name may be broken, and which takes up no room when it is not taken. */
    private const val BREAK_HINT = '\u200B'

    /** How far the icon sits from the left edge, as a fraction of the banner width. */
    private const val ICON_INSET = 0.09f

    /** The gap between the icon and the name, as a fraction of the banner width. */
    private const val LABEL_GAP = 0.05f

    /** How far the name is kept from the right edge, as a fraction of the banner width. */
    private const val LABEL_INSET = 0.09f

    /** The size the name starts at, as a fraction of the banner height. */
    private const val LABEL_MAX = 0.20f

    /** The size the name is not shrunk below, as a fraction of the banner height. */
    private const val LABEL_MIN = 0.12f

    /** The tallest the name may grow, as a fraction of the banner height. */
    private const val LABEL_HEIGHT_MAX = 0.6f

    /** How long a name has to be before it is allowed to run over two lines. */
    private const val SINGLE_LINE_MAX_LENGTH = 12

    /** Neutral colours for an icon that has no colour worth using. */
    private const val FALLBACK_HUE = 0.6f

    /** How much of the app's colour survives into the background. */
    private const val MAX_SATURATION = 0.26f

    /** The band the background lives in: dark, as Android TV asks for. */
    private const val LIGHTNESS = 0.20f

    /** Icon is sampled at this size; the banner itself is drawn at banner resolution. */
    private const val SAMPLE_SIZE = 64

    /** Enough for a couple of pages of a TV launcher. */
    private const val CACHE_SIZE = 32

    private val cache = LruCache<String, Drawable>(CACHE_SIZE)

    /**
     * Returns a banner drawn from the app icon, or null when no icon can be loaded.
     */
    fun get(
        info: ApplicationInfo?,
        packageManager: PackageManager,
        resources: Resources
    ): Drawable? {
        if (info == null) {
            return null
        }
        val key = info.packageName
        cache.get(key)?.let { return it }
        val banner = create(info, packageManager, resources) ?: return null
        cache.put(key, banner)
        return banner
    }

    private fun create(
        info: ApplicationInfo,
        packageManager: PackageManager,
        resources: Resources
    ): Drawable? {
        val icon = try {
            info.loadIcon(packageManager)
        } catch (e: RuntimeException) {
            null
        } ?: return null

        val sample = render(icon, SAMPLE_SIZE) ?: return null
        val background = extractColor(sample)
        sample.recycle()

        val banner = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(banner)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        canvas.drawColor(background)

        val size = HEIGHT * ICON_PERCENT / 100
        val iconLeft = (WIDTH * ICON_INSET).toInt()
        val iconTop = (HEIGHT - size) / 2
        val dst = Rect(iconLeft, iconTop, iconLeft + size, iconTop + size)
        val scaled = render(icon, size)
        if (scaled != null) {
            canvas.drawBitmap(scaled, null, dst, paint)
            scaled.recycle()
        }

        val labelLeft = iconLeft + size + (WIDTH * LABEL_GAP).toInt()
        val label = fitLabel(info.loadLabel(packageManager), labelLeft, background)
        if (label != null) {
            canvas.save()
            canvas.translate(labelLeft.toFloat(), (HEIGHT - label.height) / 2f)
            label.draw(canvas)
            canvas.restore()
        }

        banner.density = BANNER_DENSITY
        return BitmapDrawable(resources, banner)
    }

    private fun fitLabel(name: CharSequence?, left: Int, background: Int): StaticLayout? {
        if (name == null || name.length == 0) {
            return null
        }

        val text = withBreaks(name)
        val width = maxOf(1, WIDTH - left - (WIDTH * LABEL_INSET).toInt())

        val lines = if (widthUnits(name) > SINGLE_LINE_MAX_LENGTH) 2 else 1

        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        paint.color = textColor(background)
        paint.typeface = Typeface.DEFAULT

        var low = (HEIGHT * LABEL_MIN).toInt()
        var high = (HEIGHT * LABEL_MAX).toInt()
        var size = low
        while (low <= high) {
            val mid = (low + high) / 2
            paint.textSize = mid.toFloat()
            if (fits(buildLines(text, paint, width, lines, name), text.length)) {
                size = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }

        paint.textSize = size.toFloat()
        val layout = buildLines(text, paint, width, lines, name)
        if (!fits(layout, text.length)) {
            return layout
        }
        return filled(layout, text, paint, width, lines, name)
    }

    /** How wide the name is, in letters of the Latin alphabet. */
    private fun widthUnits(name: CharSequence): Int {
        var units = 0
        for (i in name.indices) {
            units += if (isWide(name[i])) 2 else 1
        }
        return units
    }

    private fun isWide(c: Char): Boolean {
        val v = c.code
        return v >= 0x1100 && (v <= 0x115f
                || (v >= 0x2e80 && v <= 0x303e)
                || (v >= 0x3041 && v <= 0x33ff)
                || (v >= 0x3400 && v <= 0x4dbf)
                || (v >= 0x4e00 && v <= 0x9fff)
                || (v >= 0xa000 && v <= 0xa4cf)
                || (v >= 0xa960 && v <= 0xa97c)
                || (v >= 0xac00 && v <= 0xd7a3)
                || (v >= 0xf900 && v <= 0xfaff)
                || (v >= 0xfe10 && v <= 0xfe19)
                || (v >= 0xfe30 && v <= 0xfe6f)
                || (v >= 0xff01 && v <= 0xff60)
                || (v >= 0xffe0 && v <= 0xffe6)
                || (v >= 0x1b000 && v <= 0x1b16f)
                || (v >= 0x1f300 && v <= 0x1f64f)
                || (v >= 0x1f900 && v <= 0x1f9ff)
                || (v >= 0x20000 && v <= 0x3fffd))
    }

    /** Marks the points inside the name where a line may be broken. */
    private fun withBreaks(name: CharSequence): CharSequence {
        val length = name.length
        val text = StringBuilder(length + 8)
        var copied = 0
        for (i in 1 until length) {
            if (isCaseBoundary(name, i)) {
                text.append(name, copied, i).append(BREAK_HINT)
                copied = i
            }
        }
        if (copied == 0) {
            return name
        }
        return text.append(name, copied, length)
    }

    private fun isCaseBoundary(name: CharSequence, i: Int): Boolean {
        val before = name[i - 1]
        val at = name[i]
        if (before.isLowerCase() && at.isUpperCase()) {
            return true
        }
        return before.isUpperCase() && at.isUpperCase()
                && i + 1 < name.length && name[i + 1].isLowerCase()
    }

    private fun buildLines(
        text: CharSequence,
        paint: TextPaint,
        width: Int,
        maxLines: Int,
        name: CharSequence
    ): StaticLayout {
        val builder = StaticLayout.Builder
            .obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setMaxLines(maxLines)
            .setIncludePad(false)
        if (hasNoBreakOfItsOwn(text, name)) {
            builder.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NORMAL)
        }
        return builder.build()
    }

    /** Grows a laid-out name until it fills the width of its row. */
    private fun filled(
        layout: StaticLayout,
        text: CharSequence,
        paint: TextPaint,
        width: Int,
        maxLines: Int,
        name: CharSequence
    ): StaticLayout {
        val widest = widestLine(layout)
        if (widest <= 0) {
            return layout
        }
        val scale = minOf(
            width.toFloat() / widest,
            (HEIGHT * LABEL_HEIGHT_MAX) / layout.height
        )
        if (scale <= 1f) {
            return layout
        }
        val baseSize = paint.textSize
        var grown = scale
        while (grown > 1f) {
            paint.textSize = baseSize * grown
            val candidate = buildLines(text, paint, width, maxLines, name)
            if (fits(candidate, text.length)) {
                return candidate
            }
            grown *= 0.98f
        }
        paint.textSize = baseSize
        return layout
    }

    private fun widestLine(layout: StaticLayout): Int {
        var widest = 0
        for (line in 0 until layout.lineCount) {
            widest = maxOf(widest, layout.getLineWidth(line).toInt())
        }
        return widest
    }

    private fun hasNoBreakOfItsOwn(text: CharSequence, name: CharSequence): Boolean {
        if (text.length != name.length) {
            return false
        }
        if (widthUnits(name) <= SINGLE_LINE_MAX_LENGTH) {
            return false
        }
        for (i in name.indices) {
            if (name[i].isWhitespace()) {
                return false
            }
        }
        return true
    }

    private fun fits(layout: StaticLayout, length: Int): Boolean {
        return layout.getEllipsisCount(layout.lineCount - 1) == 0
                && layout.getLineStart(layout.lineCount) == length
    }

    /** Picks a text colour that reads against the background. */
    private fun textColor(background: Int): Int {
        val luminance = (0.2126 * Color.red(background)
                + 0.7152 * Color.green(background)
                + 0.0722 * Color.blue(background)) / 255.0
        return if (luminance > 0.45) 0xde000000.toInt() else Color.WHITE
    }

    /** Draws a drawable into a square bitmap, or returns null if it cannot be drawn. */
    private fun render(drawable: Drawable?, size: Int): Bitmap? {
        if (drawable == null || size <= 0) {
            return null
        }
        return try {
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, size, size)
            drawable.draw(canvas)
            bitmap
        } catch (e: RuntimeException) {
            null
        }
    }

    /**
     * Picks the banner background colour from the icon: colours are grouped into 4-4-4
     * buckets and scored by how often they occur, weighted by saturation, with a penalty
     * for the near-white and near-black of flat monochrome icons.
     */
    private fun extractColor(bitmap: Bitmap): Int {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        val counts = IntArray(4096)
        for (pixel in pixels) {
            if (Color.alpha(pixel) < 0x40) {
                continue
            }
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)
            counts[((r shr 4) shl 8) or ((g shr 4) shl 4) or (b shr 4)]++
        }

        var bestKey = -1
        var bestScore = 0L
        for (key in counts.indices) {
            val count = counts[key]
            if (count == 0) {
                continue
            }
            val color = unquantize(key)
            val max = maxOf(Color.red(color), maxOf(Color.green(color), Color.blue(color)))
            val min = minOf(Color.red(color), minOf(Color.green(color), Color.blue(color)))
            var score = count.toLong() * (max - min)
            if (max > 0xf0 && min > 0xd0) {
                score /= 4 // washed out
            } else if (max < 0x20 && min < 0x20) {
                score /= 4 // almost black
            }
            if (score > bestScore) {
                bestScore = score
                bestKey = key
            }
        }

        if (bestKey < 0) {
            return hslToColor(FALLBACK_HUE, MAX_SATURATION, LIGHTNESS)
        }
        return hslToColor(toHsl(unquantize(bestKey))[0], MAX_SATURATION, LIGHTNESS)
    }

    private fun hslToColor(hue: Float, saturation: Float, lightness: Float): Int {
        val c = (1f - Math.abs(2f * lightness - 1f)) * saturation
        val h = hue * 6f
        val x = c * (1f - Math.abs(h % 2f - 1f))
        val m = lightness - c / 2f
        val r: Float
        val g: Float
        val b: Float
        when {
            h < 1f -> {
                r = c; g = x; b = 0f
            }
            h < 2f -> {
                r = x; g = c; b = 0f
            }
            h < 3f -> {
                r = 0f; g = c; b = x
            }
            h < 4f -> {
                r = 0f; g = x; b = c
            }
            h < 5f -> {
                r = x; g = 0f; b = c
            }
            else -> {
                r = c; g = 0f; b = x
            }
        }
        return Color.rgb(
            Math.round((r + m) * 255), Math.round((g + m) * 255), Math.round((b + m) * 255)
        )
    }

    private fun toHsl(color: Int): FloatArray {
        val r = Color.red(color) / 255f
        val g = Color.green(color) / 255f
        val b = Color.blue(color) / 255f
        val max = maxOf(r, maxOf(g, b))
        val min = minOf(r, minOf(g, b))
        val l = (max + min) / 2f
        if (max == min) {
            return floatArrayOf(0f, 0f, l)
        }
        val d = max - min
        val s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)
        val h = when (max) {
            r -> (g - b) / d + if (g < b) 6f else 0f
            g -> (b - r) / d + 2f
            else -> (r - g) / d + 4f
        }
        return floatArrayOf(h / 6f, s, l)
    }

    /** Rebuilds a representative colour from its 4-4-4 bucket. */
    private fun unquantize(key: Int): Int {
        val r = (key shr 8 and 0xf) * 0x11
        val g = (key shr 4 and 0xf) * 0x11
        val b = (key and 0xf) * 0x11
        return Color.rgb(r, g, b)
    }
}
