/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.tv.launcher.view

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import android.view.ViewTreeObserver
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import org.lineageos.tv.launcher.R

class FocusHighlightCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = com.google.android.material.R.attr.materialCardViewStyle
) : MaterialCardView(context, attrs, defStyleAttr) {

    private val density = context.resources.displayMetrics.density
    private val normalStrokeColor = strokeColor
    private val normalStrokeWidth = strokeWidth
    private val highlightStrokeWidth = (2f * density).toInt()
    private val highlightColor by lazy {
        runCatching { MaterialColors.getColor(this, R.attr.colorPrimary) }
            .getOrDefault(Color.WHITE)
    }

    private val focusListener = ViewTreeObserver.OnGlobalFocusChangeListener { _, focus ->
        val focused = focus != null && isFocusInside(focus)
        strokeColor = if (focused) highlightColor else normalStrokeColor
        strokeWidth = if (focused) highlightStrokeWidth else normalStrokeWidth
    }

    private fun isFocusInside(focus: View): Boolean {
        var parent: View? = focus
        while (parent != null) {
            if (parent === this) return true
            parent = parent.parent as? View
        }
        return false
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnGlobalFocusChangeListener(focusListener)
    }

    override fun onDetachedFromWindow() {
        if (viewTreeObserver.isAlive) {
            viewTreeObserver.removeOnGlobalFocusChangeListener(focusListener)
        }
        super.onDetachedFromWindow()
    }
}
