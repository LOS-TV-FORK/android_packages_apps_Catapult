/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.tv.launcher.utils

import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Rect
import android.graphics.RenderEffect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.SystemClock
import android.os.Looper
import android.view.Choreographer
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.view.isVisible
import androidx.palette.graphics.Palette
import coil.drawable.CrossfadeDrawable
import java.util.IdentityHashMap
import kotlin.math.abs

class DynamicBackgroundHelper(
    private val container: FrameLayout,
    private val layer: FrameLayout,
    private val bgA: ImageView,
    private val bgB: ImageView,
    private val bgC: ImageView,
    private val density: Float,
    private val onImage: ((Bitmap?) -> Unit)? = null
) {
    private companion object {
        // The source tile image is reduced to this long side before being blown
        // up full-screen: the upscale carries most of the blur, so the hardware
        // blur radius stays small and cheap on TV boxes of any screen resolution.
        const val TARGET_LONG_SIDE = 256f
        const val DEBOUNCE_MS = 300L
        const val CALM_MS = 600L
        const val CROSSFADE_MS = 1400L
        const val RETRY_MS = 500L
        const val MAX_SWAP_RETRIES = 12
        const val KEN_BURNS_MS = 22000L
        const val LOW_FPS_STEP_MS = 50L
        const val AUTO_INTERVAL_MS = 15000L
        const val FPS_WINDOW_MS = 1000L
        const val SCALE_FROM = 1.05f
        const val SCALE_TO = 1.14f
        const val PAN_FRACTION = 0.02f
    }

    private val handler = Handler(Looper.getMainLooper())
    // Three layers so a rapid card switch never interrupts an in-flight fade:
    // the new image always lands on a free layer, old fades retire on their own.
    private val pool = listOf(bgA, bgB, bgC)
    private val bitmaps = IdentityHashMap<ImageView, Bitmap?>()
    private var shownView: ImageView? = null
    private var currentKey: String? = null
    private var pendingKey: String? = null
    private var pendingProvider: (() -> Drawable?)? = null
    private var swapRetries = 0
    private var pendingColorMode = false
    private var debounceRunnable: Runnable? = null
    private var lastRequestTime = 0L
    private var blurRadiusDp = 14f
    private var blurEffect: RenderEffect? = null
    private var preBakedBlur = false
    private var preBakedActive = false
    private var visible = false
    private var kbPaused = false
    private var kbPhaseMs = 0L
    private var kenBurnsActive = false
    private var kenBurnsEnabled = true
    private var lowFpsManual = false
    private var pauseManual = false
    private var lowFpsActive = false
    private var pauseKbDuringTransitions = false
    private var autoPerf = false
    private var autoTier = 0
    private var goodWindows = 0
    private var fpsFrames = 0
    private var fpsStart = 0L
    private var poolProvider: () -> List<Pair<String, Drawable>> = { emptyList() }
    private val dominantCache = LinkedHashMap<String, Int>()
    private var previousFocusKey: String? = null
    private var currentGradient: GradientPaletteDrawable? = null
    private var gradientAnimator: ValueAnimator? = null

    // Supplies the icon drawables of all currently bound app cards so the
    // gradient can carry the whole screen's palette, not just the focused one.
    fun setAppCardPool(provider: () -> List<Pair<String, Drawable>>) {
        poolProvider = provider
    }

    private fun morphWeights(drawable: GradientPaletteDrawable, target: FloatArray) {
        gradientAnimator?.cancel()
        val start = drawable.weightsCopy()
        if (start.size != target.size) {
            drawable.setWeights(target)
            return
        }
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 900L
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                val t = anim.animatedFraction
                drawable.setWeights(
                    FloatArray(target.size) { i -> start[i] + (target[i] - start[i]) * t }
                )
            }
        }
        gradientAnimator = animator
        animator.start()
        pauseKbForTransition()
    }

    fun setBlurRadius(dp: Float) {
        blurRadiusDp = dp.coerceIn(0f, 30f)
        recomputeBlurEffect()
    }

    // CPU pre-baked Gaussian instead of the live RenderEffect: same kernel,
    // applied to the 256px artwork once, radius converted for the upscale.
    fun setPreBakedBlur(enabled: Boolean) {
        if (preBakedBlur == enabled) return
        preBakedBlur = enabled
        if (!autoPerf) applyPerfState()
    }

    private fun recomputeBlurEffect() {
        val dp = if (preBakedActive) 0f else blurRadiusDp
        blurEffect = if (dp > 0f) {
            RenderEffect.createBlurEffect(dp * density, dp * density, Shader.TileMode.CLAMP)
        } else null
        pool.forEach { view ->
            view.setRenderEffect(
                if (view.drawable is GradientPaletteDrawable) null else blurEffect
            )
        }
    }

    private fun preBlurIfNeeded(src: Bitmap): Bitmap {
        if (!preBakedActive || blurRadiusDp <= 0f) return src
        val viewW = container.width.takeIf { it > 0 } ?: return src
        // RenderEffect radius R behaves as sigma = R/2 in Skia; the box-3 core
        // lands at sigma ~ r, so take half the small-radius to match it.
        val radiusSmall = blurRadiusDp * density * src.width.toFloat() / viewW / 2f
        return gaussianBlur(src, radiusSmall)
    }

    // Three separable box-blur iterations approximate a Gaussian closely
    // (the same math GPU blur stacks use); O(n) via running sums.
    private fun gaussianBlur(src: Bitmap, radiusPx: Float): Bitmap {
        val r = Math.round(radiusPx)
        if (r < 1) return src
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        repeat(3) {
            boxPass(px, w, h, r, horizontal = true)
            boxPass(px, w, h, r, horizontal = false)
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun boxPass(px: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val len = if (horizontal) w else h
        val outer = if (horizontal) h else w
        val tmp = IntArray(len)
        val window = 2 * r + 1
        for (o in 0 until outer) {
            var sA = 0
            var sR = 0
            var sG = 0
            var sB = 0
            // Edge replication matches TileMode.CLAMP of the live blur.
            fun get(i: Int): Int {
                val j = i.coerceIn(0, len - 1)
                return if (horizontal) px[o * w + j] else px[j * w + o]
            }
            for (i in -r..r) {
                val p = get(i)
                sA += p ushr 24
                sR += p shr 16 and 255
                sG += p shr 8 and 255
                sB += p and 255
            }
            for (i in 0 until len) {
                tmp[i] = (sA / window shl 24) or
                    (sR / window shl 16) or
                    (sG / window shl 8) or
                    (sB / window)
                val add = get(i + r + 1)
                val sub = get(i - r)
                sA += (add ushr 24) - (sub ushr 24)
                sR += (add shr 16 and 255) - (sub shr 16 and 255)
                sG += (add shr 8 and 255) - (sub shr 8 and 255)
                sB += (add and 255) - (sub and 255)
            }
            for (i in 0 until len) {
                px[if (horizontal) o * w + i else i * w + o] = tmp[i]
            }
        }
    }

    // Manual toggles; while auto mode runs they are ignored.
    fun setLowFpsMode(enabled: Boolean) {
        lowFpsManual = enabled
        if (!autoPerf) applyPerfState()
    }

    fun setPauseDuringTransitions(enabled: Boolean) {
        pauseManual = enabled
        if (!autoPerf) applyPerfState()
    }

    // Adaptive performance: a 1s frame window every 15s (near-free) decides
    // whether to simplify the effect stack step by step or restore a step.
    // The learned tier survives restarts; re-enabling auto starts from 0 again.
    fun setAutoPerf(enabled: Boolean, learnedTier: Int = 0) {
        if (autoPerf == enabled) return
        autoPerf = enabled
        if (enabled) {
            autoTier = learnedTier.coerceIn(0, 3)
            goodWindows = 0
            scheduleAutoWindow()
        } else {
            autoTier = 0
            handler.removeCallbacks(autoScheduleRunnable)
        }
        applyPerfState()
    }

    private var autoTierListener: ((Int) -> Unit)? = null

    fun setAutoTierListener(listener: (Int) -> Unit) {
        autoTierListener = listener
    }

    private fun applyPerfState() {
        lowFpsActive = if (autoPerf) autoTier >= 1 else lowFpsManual
        pauseKbDuringTransitions = if (autoPerf) true else pauseManual
        preBakedActive = if (autoPerf) autoTier >= 2 else preBakedBlur
        val enabled = if (autoPerf) autoTier < 3 else true
        recomputeBlurEffect()
        if (kenBurnsEnabled != enabled) {
            kenBurnsEnabled = enabled
            if (enabled) {
                if (visible) startKenBurns()
            } else {
                stopKenBurns()
            }
        }
    }

    private val autoScheduleRunnable = Runnable { measureFpsWindow() }

    private fun scheduleAutoWindow() {
        handler.removeCallbacks(autoScheduleRunnable)
        handler.postDelayed(autoScheduleRunnable, AUTO_INTERVAL_MS)
    }

    private fun measureFpsWindow() {
        if (!autoPerf || !visible) return
        fpsFrames = 0
        fpsStart = SystemClock.elapsedRealtime()
        Choreographer.getInstance().postFrameCallback(fpsCallback)
    }

    private val fpsCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!autoPerf) return
            fpsFrames++
            val now = SystemClock.elapsedRealtime()
            if (now - fpsStart < FPS_WINDOW_MS) {
                Choreographer.getInstance().postFrameCallback(this)
            } else {
                val fps = fpsFrames * 1000f / (now - fpsStart).coerceAtLeast(1L)
                if (fps < 45f) {
                    goodWindows = 0
                    if (autoTier < 3) {
                        autoTier++
                        android.util.Log.d("DynamicBG", "auto-perf downgrade fps=$fps tier=$autoTier")
                        applyPerfState()
                        autoTierListener?.invoke(autoTier)
                    }
                } else if (fps >= 55f) {
                    goodWindows++
                    if (goodWindows >= 3 && autoTier > 0) {
                        autoTier--
                        goodWindows = 0
                        android.util.Log.d("DynamicBG", "auto-perf upgrade tier=$autoTier")
                        applyPerfState()
                        autoTierListener?.invoke(autoTier)
                    }
                } else {
                    goodWindows = 0
                }
                scheduleAutoWindow()
            }
        }
    }

    fun show() {
        visible = true
        container.isVisible = true
        setBlurRadius(blurRadiusDp)
        startKenBurns()
    }

    fun hide() {
        visible = false
        container.isVisible = false
        debounceRunnable?.let { handler.removeCallbacks(it) }
        debounceRunnable = null
        pendingProvider = null
        stopKenBurns()
    }

    fun pause() {
        kbPaused = true
    }

    fun resume() {
        kbPaused = false
        if (visible && !kenBurnsActive) {
            startKenBurns()
        }
    }

    fun setImage(key: String, drawableProvider: () -> Drawable?) {
        pendingColorMode = false
        startSwap(key, drawableProvider)
    }

    // App banners look bad as blurred wallpapers; for app cards the background is
    // generated from the icon palette (androidx Palette, same as card banners)
    // instead of the artwork itself.
    fun setColorImage(key: String, drawableProvider: () -> Drawable?) {
        pendingColorMode = true
        startSwap(key, drawableProvider)
    }

    private fun startSwap(key: String, drawableProvider: (() -> Drawable?)) {
        if (!visible) return
        if (key == currentKey) {
            android.util.Log.d("DynamicBG", "skip-same key=$key")
            return
        }
        pendingKey = key
        pendingProvider = drawableProvider
        swapRetries = 0
        debounceRunnable?.let { handler.removeCallbacks(it) }
        val runnable = Runnable { swap() }
        debounceRunnable = runnable
        // After a calm period start instantly; debounce only while the user
        // is flicking through cards in quick succession.
        val now = SystemClock.elapsedRealtime()
        val sinceLast = now - lastRequestTime
        lastRequestTime = now
        val delay = if (sinceLast > CALM_MS) 0L else DEBOUNCE_MS
        handler.postDelayed(runnable, delay)
    }

    fun release() {
        hide()
        gradientAnimator?.cancel()
        gradientAnimator = null
        currentGradient = null
        dominantCache.clear()
        shownView = null
        currentKey = null
        pendingProvider = null
        pool.forEach { view ->
            view.animate().cancel()
            view.setImageDrawable(null)
            view.alpha = 0f
        }
        bitmaps.values.forEach { it?.recycle() }
        bitmaps.clear()
    }

    private fun swap() {
        debounceRunnable = null
        val provider = pendingProvider ?: return
        val key = pendingKey ?: return
        if (pendingColorMode) {
            val provided = provider()
            val raw = (provided as? CrossfadeDrawable)?.end ?: provided
            val iconBmp = raw?.let { iconBitmap(it) }
            val focusKey = key.substringBefore('@')
            val focusColor = dominantCache[focusKey]
                ?: iconBmp?.let { paletteColor(it) }
                ?: paletteFromKey(key).first()
            dominantCache[focusKey] = focusColor
            val presentKeys = HashSet<String>()
            presentKeys.add(focusKey)
            for ((k, d) in poolProvider()) {
                val kk = k.substringBefore('@')
                val computed = iconBitmap(d)?.let { bitmap ->
                    val color = paletteColor(bitmap)
                    bitmap.recycle()
                    color
                }
                val cached = dominantCache[kk] ?: computed
                if (cached != null) {
                    dominantCache[kk] = cached
                    presentKeys.add(kk)
                }
            }
            val keys = dominantCache.keys.toList()
            val colors = dominantCache.values.toList()
            val focusIdx = keys.indexOf(focusKey)
            val previous = previousFocusKey
            previousFocusKey = focusKey
            // Visible ramp carries only the focused color and the one it came
            // from: everything else collapses to an invisible sliver, so the
            // gradient never mixes more than two hues.
            val weights = FloatArray(keys.size) { i ->
                when {
                    i == focusIdx -> 0.75f
                    keys[i] == previous -> 0.22f
                    else -> 0.001f
                }
            }
            if (colors.size == 1) {
                val single = colors.first()
                present(
                    GradientPaletteDrawable(
                        listOf(single, shiftedHue(single)), floatArrayOf(0.75f, 0.25f)
                    ),
                    iconBmp,
                    key
                )
                currentGradient = null
                return
            }
            val existing = currentGradient
            if (existing != null && existing.colors() == colors &&
                shownView?.drawable === existing
            ) {
                bitmaps[shownView]?.recycle()
                bitmaps[shownView] = iconBmp
                onImage?.invoke(iconBmp)
                currentKey = key
                morphWeights(existing, weights)
                return
            }
            val gradient = GradientPaletteDrawable(colors, weights)
            android.util.Log.d(
                "DynamicBG",
                "focus=$focusKey colors=${colors.size} fw=${weights.getOrNull(focusIdx)}"
            )
            present(gradient, iconBmp, key)
            currentGradient = gradient
            return
        }
        // Channel posters are loaded asynchronously by Coil: the ImageView may still
        // hold a solid placeholder at focus time. Sample the drawable again at swap
        // time and retry a few times until the real artwork arrives.
        // During scroll Coil re-runs its crossfade and the ImageView holds a
        // CrossfadeDrawable that cannot be rasterized: unwrap to the final artwork.
        val raw = provider()
        val drawable = (raw as? CrossfadeDrawable)?.end
            ?.takeUnless { it is ColorDrawable }
            ?: raw?.takeUnless { it is ColorDrawable || it is CrossfadeDrawable }
        if (drawable == null) {
            retrySwap()
            return
        }
        val bitmap = downscale(drawable)
        if (bitmap == null || isSolid(bitmap)) {
            bitmap?.recycle()
            retrySwap()
            return
        }
        val artworkBitmap = preBlurIfNeeded(bitmap)
        present(UpscaleDrawable(artworkBitmap), bitmap, key)
    }

    private fun present(artwork: Drawable, tint: Bitmap?, key: String) {
        swapRetries = 0
        currentGradient = null
        val previous = shownView
        // Prefer a layer that is invisible and holds no leftover bitmap.
        val candidates = pool.filter { it !== previous && it.alpha < 0.05f }
        val next = candidates.minByOrNull { if (bitmaps.containsKey(it)) 1 else 0 }
            ?: pool.filter { it !== previous }.minByOrNull { it.alpha }
            ?: run { tint?.recycle(); return }
        next.animate().cancel()
        bitmaps.remove(next)?.recycle()
        next.setImageDrawable(artwork)
        next.setRenderEffect(
            if (artwork is GradientPaletteDrawable) null else blurEffect
        )
        bitmaps[next] = tint
        shownView = next
        previous?.animate()?.alpha(0f)?.setDuration(CROSSFADE_MS)
            ?.setInterpolator(AccelerateDecelerateInterpolator())?.withEndAction {
                previous.setImageDrawable(null)
                bitmaps.remove(previous)?.recycle()
            }?.start()
        next.animate().alpha(1f).setDuration(CROSSFADE_MS)
            .setInterpolator(AccelerateDecelerateInterpolator()).start()
        currentKey = key
        onImage?.invoke(tint)
        pauseKbForTransition()
    }

    private fun retrySwap() {
        if (swapRetries < MAX_SWAP_RETRIES) {
            swapRetries++
            val runnable = Runnable { swap() }
            debounceRunnable = runnable
            handler.postDelayed(runnable, RETRY_MS)
        }
    }

    // Same rasterization the card banner painter uses (CardBackgroundHelper):
    // set bounds to intrinsic size and draw onto a software canvas - adaptive
    // icons render correctly this way, always return an owned copy.
    private fun iconBitmap(drawable: Drawable): Bitmap? {
        if (drawable is ColorDrawable) return null
        return try {
            val source = (drawable as? BitmapDrawable)?.bitmap?.takeIf { !it.isRecycled }
            if (source != null) {
                source.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                val width = drawable.intrinsicWidth.coerceAtLeast(1)
                val height = drawable.intrinsicHeight.coerceAtLeast(1)
                val oldBounds = Rect(drawable.bounds)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                drawable.setBounds(0, 0, width, height)
                drawable.draw(Canvas(bitmap))
                drawable.bounds = oldBounds
                bitmap
            }
        } catch (e: Throwable) {
            null
        }
    }

    private fun paletteColor(bitmap: Bitmap): Int {
        val palette = try {
            Palette.from(bitmap).generate()
        } catch (e: Throwable) {
            return Color.GRAY
        }
        val vibrant = palette.getVibrantColor(0)
        if (vibrant != 0) return vibrant
        val dark = palette.getDarkVibrantColor(0)
        if (dark != 0) return dark
        val light = palette.getLightVibrantColor(0)
        if (light != 0) return light
        return palette.getDominantColor(Color.GRAY)
    }

    private fun downscale(drawable: Drawable): Bitmap? {
        val bounds = drawable.bounds
        val sourceW = drawable.intrinsicWidth.takeIf { it > 0 }
            ?: bounds.width().takeIf { it > 0 } ?: 108
        val sourceH = drawable.intrinsicHeight.takeIf { it > 0 }
            ?: bounds.height().takeIf { it > 0 } ?: 108
        val scale = (TARGET_LONG_SIDE / maxOf(sourceW, sourceH).toFloat()).coerceAtMost(1f)
        val width = (sourceW * scale).toInt().coerceAtLeast(1)
        val height = (sourceH * scale).toInt().coerceAtLeast(1)
        return try {
            val src = (drawable as? BitmapDrawable)?.bitmap?.takeIf { !it.isRecycled }
            if (src != null) {
                // Coil decodes HARDWARE bitmaps: pixel ops and software canvas draw
                // are unsupported on them, so convert to a software copy first.
                val software =
                    if (src.config == Bitmap.Config.HARDWARE) src.copy(Bitmap.Config.ARGB_8888, false)
                    else src
                val scaled = Bitmap.createScaledBitmap(software, width, height, true)
                if (scaled === src || scaled === software) {
                    scaled.copy(Bitmap.Config.ARGB_8888, false)
                } else scaled
            } else {
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.scale(width.toFloat() / sourceW, height.toFloat() / sourceH)
                canvas.translate(-bounds.left.toFloat(), -bounds.top.toFloat())
                drawable.draw(canvas)
                bitmap
            }
        } catch (e: Throwable) {
            null
        }
    }

    private fun shiftedHue(color: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[0] = (hsv[0] + 40f) % 360f
        hsv[2] = maxOf(0.3f, hsv[2] * 0.9f)
        return Color.HSVToColor(hsv)
    }

    // Bilinear full-screen upscale: ImageView scales bitmaps with nearest-neighbor,
    // which produces blocky steps when a small bitmap is blown up.
    private class UpscaleDrawable(private val bitmap: Bitmap) : Drawable() {
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

        override fun draw(canvas: Canvas) {
            canvas.drawBitmap(bitmap, Rect(0, 0, bitmap.width, bitmap.height), bounds, paint)
        }

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            paint.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.OPAQUE
    }

    // Coil keeps a solid placeholder (colorSecondaryContainer) on the poster ImageView
    // until the provider artwork arrives; such a frame must not become the background.
    private fun isSolid(bitmap: Bitmap): Boolean {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 4 || h < 4) return true
        val first = bitmap.getPixel(w / 2, h / 2)
        val r0 = (first shr 16 and 0xFF)
        val g0 = (first shr 8 and 0xFF)
        val b0 = (first and 0xFF)
        for (x in 0 until w step 16) {
            for (y in 0 until h step 16) {
                val p = bitmap.getPixel(x, y)
                val dr = abs((p shr 16 and 0xFF) - r0)
                val dg = abs((p shr 8 and 0xFF) - g0)
                val db = abs((p and 0xFF) - b0)
                if (dr > 3 || dg > 3 || db > 3) return false
            }
        }
        return true
    }

    // Manual Ken Burns stepper instead of an infinite AnimatorSet: one cheap
    // Runnable drives scale/pan. At 20 fps the transform updates are four
    // times rarer, and GPU work only happens when the matrix actually moves.
    private val kenBurnsTick = object : Runnable {
        override fun run() {
            val step = if (lowFpsActive) LOW_FPS_STEP_MS else 16L
            if (!kbPaused && kenBurnsEnabled) {
                kbPhaseMs += step
                applyKenBurnsFrame()
            }
            handler.postDelayed(this, step)
        }
    }

    private fun applyKenBurnsFrame() {
        val w = layer.width.toFloat()
        val h = layer.height.toFloat()
        if (w <= 0f || h <= 0f) return
        val cycle = KEN_BURNS_MS * 2
        val el = kbPhaseMs % cycle
        val t = (if (el < KEN_BURNS_MS) el else cycle - el).toFloat() / KEN_BURNS_MS
        val interp = AccelerateDecelerateInterpolator().getInterpolation(t)
        val scale = SCALE_FROM + (SCALE_TO - SCALE_FROM) * interp
        layer.scaleX = scale
        layer.scaleY = scale
        layer.translationX = (interp * 2f - 1f) * w * PAN_FRACTION
        layer.translationY = (interp * 2f - 1f) * h * PAN_FRACTION
    }

    private fun startKenBurns() {
        if (!kenBurnsEnabled || kenBurnsActive) return
        val w = layer.width.toFloat()
        val h = layer.height.toFloat()
        if (w <= 0f || h <= 0f) {
            layer.post { if (visible) startKenBurns() }
            return
        }
        layer.pivotX = w / 2f
        layer.pivotY = h / 2f
        kenBurnsActive = true
        handler.postDelayed(kenBurnsTick, if (lowFpsActive) LOW_FPS_STEP_MS else 16L)
    }

    private fun stopKenBurns() {
        kenBurnsActive = false
        handler.removeCallbacks(kenBurnsTick)
        kbPhaseMs = 0L
        layer.scaleX = SCALE_FROM
        layer.scaleY = SCALE_FROM
        layer.translationX = 0f
        layer.translationY = 0f
    }

    private fun pauseKbForTransition() {
        if (!pauseKbDuringTransitions || !kenBurnsEnabled) return
        kbPaused = true
        handler.postDelayed({ kbPaused = false }, CROSSFADE_MS + 300L)
    }
}

// Snap the icon color to the nearest reference hue and brighten it: the
// background reads as a vivid standard tone close to the icon, not a 1:1 copy.
private val REFERENCE_HUES = floatArrayOf(
    0f, 30f, 55f, 80f, 120f, 150f, 175f, 200f, 230f, 260f, 290f, 320f
)

private fun toneColor(color: Int): Int {
    val hsv = FloatArray(3)
    Color.colorToHSV(color, hsv)
    if (hsv[1] < 0.08f) {
        hsv[2] = hsv[2].coerceIn(0.18f, 0.55f)
        return Color.HSVToColor(hsv)
    }
    var best = hsv[0]
    var bestDist = 360f
    for (ref in REFERENCE_HUES) {
        val d = abs(hsv[0] - ref)
        val dist = minOf(d, 360f - d)
        if (dist < bestDist) {
            bestDist = dist
            best = ref
        }
    }
    hsv[0] = best
    hsv[1] = hsv[1].coerceIn(0.6f, 0.95f)
    hsv[2] = hsv[2].coerceIn(0.6f, 0.9f)
    return Color.HSVToColor(hsv)
}

// Static micro-noise tile used as dither overlay (alpha 7 keeps it invisible
// as grain but destroys gradient contour lines).
private val NOISE: Bitmap by lazy {
    val size = 128
    val random = java.util.Random(7919L)
    val pixels = IntArray(size * size)
    for (i in pixels.indices) {
        pixels[i] = (7 shl 24) or
            (random.nextInt(256) shl 16) or
            (random.nextInt(256) shl 8) or
            random.nextInt(256)
    }
    Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}

// Deterministic 3-color tonal ramp from the card key (icon-less fallback).
private fun paletteFromKey(key: String): List<Int> {
    val base = key.hashCode()
    val hsv = FloatArray(3)
    Color.colorToHSV(base and 0xFFFFFF, hsv)
    hsv[1] = 0.55f
    hsv[2] = 0.5f
    val start = Color.HSVToColor(hsv)
    val mid = shiftedHueColor(start)
    val end = shiftedHueColor(mid)
    return listOf(start, mid, end)
}

private fun shiftedHueColor(color: Int): Int {
    val hsv = FloatArray(3)
    Color.colorToHSV(color, hsv)
    hsv[0] = (hsv[0] + 45f) % 360f
    return Color.HSVToColor(hsv)
}

// Clean diagonal tonal gradient of the palette, rasterized at the view's real
// size with dithering (Romain Guy's standard banding fix), plus a soft light
// and a bottom vignette for depth.
private class GradientPaletteDrawable(
    private val colors: List<Int>,
    weights: FloatArray
) : Drawable() {
    private val paint = Paint().apply { isDither = true }
    private var weights = weights.copyOf()
    private val positions = FloatArray(colors.size)

    init {
        computePositions()
    }

    fun weightsCopy(): FloatArray = weights.copyOf()

    fun colors(): List<Int> = colors

    fun setWeights(newWeights: FloatArray) {
        if (newWeights.size != weights.size) return
        weights = newWeights.copyOf()
        computePositions()
        invalidateSelf()
    }

    // Each color owns a band proportional to its weight; stops sit at band
    // centers so interpolated transitions land exactly on the band borders.
    private fun computePositions() {
        val total = weights.sum().coerceAtLeast(0.001f)
        var start = 0f
        for (i in weights.indices) {
            val end = start + weights[i] / total
            positions[i] = (start + end) / 2f
            start = end
        }
    }

    override fun draw(canvas: Canvas) {
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        val ramp = colors.map { toneColor(it) }.toIntArray()
        paint.shader = LinearGradient(
            0f, 0f, w, h, ramp, positions, Shader.TileMode.CLAMP
        )
        canvas.drawPaint(paint)
        paint.shader = RadialGradient(
            w * 0.3f, h * 0.25f, w * 0.8f,
            0x30FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP
        )
        canvas.drawPaint(paint)
        paint.shader = LinearGradient(
            0f, 0f, 0f, h, 0x00000000, 0x66000000, Shader.TileMode.CLAMP
        )
        canvas.drawPaint(paint)
        // GPU dither (Paint.isDither) is ignored by Skia, so 8-bit gradients band
        // on hardware. A tiled micro-noise overlay breaks the contour lines -
        // the classic Android banding workaround.
        paint.shader = BitmapShader(NOISE, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        canvas.drawPaint(paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.OPAQUE
}
