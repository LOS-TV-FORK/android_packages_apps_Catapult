/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.tv.launcher.view

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.LinearLayout
import org.lineageos.tv.launcher.model.Launchable
import org.lineageos.tv.launcher.utils.DropShadowDrawable

abstract class Card @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {
    
    init {
        clipChildren = false
        clipToPadding = false
        isClickable = true
        isLongClickable = true
        setFocusableInTouchMode(true)
    }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        pivotX = w / 2f
        pivotY = h / 2f
    }

    var packageName: String = ""
    var label: String = ""
    var launchIntent: Intent? = null
    var hasMenu: Boolean = true

    // True while this card is being reordered. The main grid's matrix re-anchor
    // must not steal focus away from the card the user is moving.
    open fun isMoving(): Boolean = false

    private var shadowDrawable: DropShadowDrawable? = null
    private var glowView: View? = null
    private var breatheAnimator: ValueAnimator? = null

    open fun setCardInfo(appInfo: Launchable) {
        label = appInfo.label
        packageName = appInfo.packageName
        launchIntent = appInfo.launchIntent
        hasMenu = appInfo.hasMenu
    }

    protected fun shadowPadding(): Int = DropShadowDrawable.shadowPaddingFor(context)

    protected fun applyCardShadow(shadowView: View) {
        shadowView.clipToOutline = false
        val pad = shadowPadding()
        (shadowView.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
            lp.setMargins(-pad, -pad, -pad, -pad)
            shadowView.layoutParams = lp
        }
        val shadow = DropShadowDrawable(context)
        shadowDrawable = shadow
        glowView = shadowView
        shadowView.background = shadow
        if (hasFocus()) startBreathing() else shadow.glowIntensity = 0f
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        if (hasFocus()) startBreathing() else stopBreathing()
    }

    override fun onDetachedFromWindow() {
        stopBreathing()
        super.onDetachedFromWindow()
    }

    private fun startBreathing() {
        val shadow = shadowDrawable ?: return
        if (breatheAnimator?.isRunning == true) return
        breatheAnimator = ValueAnimator.ofFloat(0.35f, 1f).apply {
            duration = 1100
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                shadow.glowIntensity = it.animatedValue as Float
                glowView?.invalidate()
            }
            start()
        }
    }

    private fun stopBreathing() {
        breatheAnimator?.cancel()
        breatheAnimator = null
        shadowDrawable?.glowIntensity = 0f
        glowView?.invalidate()
    }

    companion object
}
