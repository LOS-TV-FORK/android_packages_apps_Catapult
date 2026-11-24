/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.tv.launcher.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.View
import com.google.android.material.color.MaterialColors
import org.lineageos.tv.launcher.R

class DropShadowDrawable(context: Context) : Drawable() {

    private val blurRadius = BLUR_DP * context.resources.displayMetrics.density
    private val shadowPadding = shadowPaddingFor(context)
    private val cornerRadius = CardBackgroundHelper.cardCornerRadiusPx(context)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        setShadowLayer(blurRadius, 0f, 0f, SHADOW_COLOR)
        color = Color.TRANSPARENT
    }
    private var shadowBitmap: Bitmap? = null
    private var shadowWidth = 0
    private var shadowHeight = 0

    // Focus glow: a colored halo around the same rounded rect, drawn over the
    // shadow with an animated alpha. Rendered lazily into its own bitmap so the
    // breathing only costs an alpha-blended draw. Kept inside this drawable so
    // the card background stays a single drawable and its padding is unchanged.
    private val glowColor = runCatching {
        val base = MaterialColors.getColor(View(context), R.attr.colorPrimary)
        Color.rgb(Color.red(base), Color.green(base), Color.blue(base))
    }.getOrDefault(Color.WHITE)
    private val glowBlur = shadowPadding * 0.7f
    private val glowAlphaPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private var glowBitmap: Bitmap? = null
    private var glowWidth = 0
    private var glowHeight = 0
    var glowIntensity: Float = 0f

    override fun getPadding(padding: Rect): Boolean {
        padding.set(shadowPadding, shadowPadding, shadowPadding, shadowPadding)
        return true
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    override fun onBoundsChange(bounds: Rect) {
        val width = bounds.width()
        val height = bounds.height()
        if (width <= 0 || height <= 0) {
            shadowBitmap?.recycle()
            shadowBitmap = null
            shadowWidth = 0
            shadowHeight = 0
            glowBitmap?.recycle()
            glowBitmap = null
            glowWidth = 0
            glowHeight = 0
            return
        }
        // Bounds change on every measure pass; rebuilding the shadow bitmap each
        // time while the old one waits for the GC is what took the launcher down
        // with an OutOfMemoryError in the recents overlay. Reuse while the size
        // is unchanged, recycle on rebuild.
        if (width == shadowWidth && height == shadowHeight && shadowBitmap != null) {
            return
        }
        shadowBitmap?.recycle()
        glowBitmap?.recycle()
        glowBitmap = null
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawRoundRect(
            shadowPadding.toFloat(),
            shadowPadding.toFloat(),
            (width - shadowPadding).toFloat(),
            (height - shadowPadding).toFloat(),
            cornerRadius,
            cornerRadius,
            paint
        )
        shadowBitmap = bitmap
        shadowWidth = width
        shadowHeight = height
    }

    override fun draw(canvas: Canvas) {
        shadowBitmap?.let {
            canvas.drawBitmap(it, bounds.left.toFloat(), bounds.top.toFloat(), null)
        }
        if (glowIntensity > 0f) {
            val w = bounds.width()
            val h = bounds.height()
            if (w > 0 && h > 0) {
                ensureGlow(w, h)
                glowBitmap?.let {
                    glowAlphaPaint.alpha = (glowIntensity * 255f).toInt().coerceIn(0, 255)
                    canvas.drawBitmap(it, bounds.left.toFloat(), bounds.top.toFloat(), glowAlphaPaint)
                }
            }
        }
    }

    private fun ensureGlow(w: Int, h: Int) {
        if (glowBitmap != null && w == glowWidth && h == glowHeight) return
        glowBitmap?.recycle()
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.TRANSPARENT
            setShadowLayer(glowBlur, 0f, 0f, (glowColor and 0x00FFFFFF) or (0xCC shl 24))
        }
        canvas.drawRoundRect(
            shadowPadding.toFloat(),
            shadowPadding.toFloat(),
            (w - shadowPadding).toFloat(),
            (h - shadowPadding).toFloat(),
            cornerRadius,
            cornerRadius,
            paint
        )
        glowBitmap = bitmap
        glowWidth = w
        glowHeight = h
    }

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    companion object {
        private const val BLUR_DP = 8f
        private const val SHADOW_COLOR = 0x4D000000

        fun shadowPaddingFor(context: Context): Int =
            (BLUR_DP * context.resources.displayMetrics.density * 1.8f).toInt()
    }
}
