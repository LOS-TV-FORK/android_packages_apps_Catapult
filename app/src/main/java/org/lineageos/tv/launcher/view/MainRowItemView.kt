/*
 * SPDX-FileCopyrightText: 2024-2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.tv.launcher.view

import android.animation.ValueAnimator
import android.content.SharedPreferences
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.leanback.widget.HorizontalGridView
import org.lineageos.tv.launcher.R
import org.lineageos.tv.launcher.model.MainRowItem
import org.lineageos.tv.launcher.adapter.AllAppsAdapter
import org.lineageos.tv.launcher.adapter.CollectionAdapter
import org.lineageos.tv.launcher.adapter.FavoritesAdapter
import org.lineageos.tv.launcher.adapter.PreviewProgramsAdapter
import org.lineageos.tv.launcher.adapter.WatchNextAdapter
import org.lineageos.tv.launcher.ext.allAppsGrid
import org.lineageos.tv.launcher.ext.allAppsGridColumns
import org.lineageos.tv.launcher.ext.appCardSize
import org.lineageos.tv.launcher.ext.favoriteCardSize
import org.lineageos.tv.launcher.ext.watchNextCardSize
import org.lineageos.tv.launcher.utils.GridSpacingItemDecoration

class MainRowItemView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {
    private val horizontalGridView by lazy { findViewById<HorizontalGridView>(R.id.horizontal_grid)!! }
    private val verticalGridRecyclerView by lazy { findViewById<RecyclerView>(R.id.vertical_grid)!! }
    private val titleView by lazy { findViewById<TextView>(R.id.title)!! }
    
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (horizontalGridView.visibility == View.VISIBLE) {
            updateCarouselPadding()
        }
    }
    
    fun updateCarouselPadding() {
        val density = context.resources.displayMetrics.density
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val adapter = horizontalGridView.adapter ?: return
        
        val (baseCardHeightDp, cardSizePercent) = when (adapter) {
            is AllAppsAdapter -> Pair(100f, allAppsSize(adapter, prefs).toFloat())
            is FavoritesAdapter -> Pair(71f, prefs.favoriteCardSize.toFloat())
            is CollectionAdapter -> Pair(100f, adapter.cardSize.toFloat())
            is WatchNextAdapter, is PreviewProgramsAdapter -> Pair(138f, prefs.watchNextCardSize.toFloat())
            else -> return
        }
        
        val scale = cardSizePercent / 100f
        val cardHeight = (baseCardHeightDp * scale * density).toInt()
        val focusScale = 1.1f
        val focusPadding = (cardHeight * (focusScale - 1f)).toInt()
        val basePadding = (8f * density).toInt()
        val edgePadding = (48f * density).toInt() + basePadding
        val topPadding = focusPadding + basePadding

        horizontalGridView.setPadding(edgePadding, topPadding, edgePadding, basePadding)
        
        val spacing = calculateCardSpacing(adapter)
        horizontalGridView.setItemSpacing(spacing)
        
        val newHeight = calculateRowHeight(adapter)
        val lp = horizontalGridView.layoutParams
        lp.height = newHeight
        horizontalGridView.layoutParams = lp
    }

    fun carouselAdapter(): androidx.recyclerview.widget.RecyclerView.Adapter<*>? =
        horizontalGridView.adapter

    private var sizeAnimator: ValueAnimator? = null

    fun animateCarouselSize(from: Int, to: Int) {
        val adapter = when (val a = horizontalGridView.adapter) {
            is CollectionAdapter, is AllAppsAdapter -> a
            else -> return
        }
        val gridMode = when (adapter) {
            is CollectionAdapter -> adapter.gridMode
            is AllAppsAdapter -> adapter.gridMode
            else -> false
        }
        if (gridMode || from == to) {
            setLiveSize(adapter, to)
            updateCarouselPadding()
            return
        }
        sizeAnimator?.cancel()
        sizeAnimator = ValueAnimator.ofInt(from, to).apply {
            duration = 170
            addUpdateListener { animator ->
                val size = animator.animatedValue as Int
                setLiveSize(adapter, size)
                applySizeToVisibleCards(adapter, size)
                updateCarouselPadding()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    setLiveSizeFinal(adapter, to)
                    applySizeToVisibleCards(adapter, to)
                    updateCarouselPadding()
                    // Converge the off-screen and recycled cards to the final size.
                    adapter.notifyDataSetChanged()
                }
            })
            start()
        }
    }

    private fun setLiveSize(adapter: RecyclerView.Adapter<*>, size: Int) {
        when (adapter) {
            is CollectionAdapter -> adapter.cardSize = size
            is AllAppsAdapter -> adapter.liveSizeOverride = size
            else -> {}
        }
    }

    private fun setLiveSizeFinal(adapter: RecyclerView.Adapter<*>, size: Int) {
        when (adapter) {
            is CollectionAdapter -> adapter.cardSize = size
            is AllAppsAdapter -> adapter.liveSizeOverride = null
            else -> {}
        }
    }

    private fun applySizeToVisibleCards(adapter: RecyclerView.Adapter<*>, size: Int) {
        val radius = (adapter as? CollectionAdapter)?.cornerRadius
        for (i in 0 until horizontalGridView.childCount) {
            (horizontalGridView.getChildAt(i) as? AppCard)?.applyCardSettings(
                size / 100f, radius, false
            )
        }
    }

    private fun allAppsSize(adapter: AllAppsAdapter, prefs: SharedPreferences): Int =
        adapter.liveSizeOverride ?: prefs.appCardSize

    private var appliedKey: String? = null

    fun setData(mainRowItem: MainRowItem) {
        titleView.text = mainRowItem.label
        val prefsNow = PreferenceManager.getDefaultSharedPreferences(context)
        val structural = when (val a = mainRowItem.adapter) {
            is CollectionAdapter -> mainRowItem.settingsKey
            is AllAppsAdapter -> "${prefsNow.allAppsGrid}|${prefsNow.allAppsGridColumns}"
            else -> ""
        }
        val key = structural + System.identityHashCode(mainRowItem.adapter)
        if (appliedKey == key) return
        // A structural rebuild below recycles views; doing that while a child of this
        // row holds focus detaches the focused view mid-focus-restore and crashes the
        // main grid's focus handling. Release the focus inside this row first.
        findFocus()?.clearFocus()
        appliedKey = key
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val isGrid = when (val adapter = mainRowItem.adapter) {
            is CollectionAdapter -> adapter.gridMode
            is AllAppsAdapter -> prefs.allAppsGrid
            else -> false
        }

        horizontalGridView.visibility = if (isGrid) View.GONE else View.VISIBLE
        verticalGridRecyclerView.visibility = if (isGrid) View.VISIBLE else View.GONE

        // Wrap content: a fixed (MATCH_PARENT) row clips the carousel when the
        // card size grows past the row's allocated height.
        layoutParams = layoutParams.apply {
            height = ViewGroup.LayoutParams.WRAP_CONTENT
        }

        if (isGrid) {
            setupGridView(mainRowItem.adapter)
        } else {
            if (mainRowItem.adapter is AllAppsAdapter) {
                mainRowItem.adapter.gridMode = false
            }
            horizontalGridView.adapter = mainRowItem.adapter
            updateCarouselPadding()
        }
    }

    private fun setupGridView(adapter: RecyclerView.Adapter<*>) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val density = context.resources.displayMetrics.density
        val columns = when (adapter) {
            is CollectionAdapter -> adapter.gridColumns
            else -> prefs.allAppsGridColumns
        }.coerceIn(4, 10)
        val edgePadding = (48f * density).toInt()
        val spacing = (12f * density).toInt()

        if (adapter is AllAppsAdapter) adapter.gridMode = true
        if (adapter is CollectionAdapter) adapter.gridMode = true
        adapter.notifyDataSetChanged()

        verticalGridRecyclerView.adapter = adapter
        verticalGridRecyclerView.layoutManager = GridLayoutManager(context, columns)
        verticalGridRecyclerView.isNestedScrollingEnabled = false
        verticalGridRecyclerView.clipToPadding = false
        verticalGridRecyclerView.clipChildren = false
        while (verticalGridRecyclerView.itemDecorationCount > 0) {
            verticalGridRecyclerView.removeItemDecorationAt(0)
        }

        verticalGridRecyclerView.addItemDecoration(
            GridSpacingItemDecoration(columns, spacing, spacing)
        )

        verticalGridRecyclerView.setPadding(edgePadding, 0, edgePadding, 0)
        verticalGridRecyclerView.overScrollMode = View.OVER_SCROLL_NEVER
    }

    private fun calculateCardSpacing(adapter: androidx.recyclerview.widget.RecyclerView.Adapter<*>): Int {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val cardSizePercent = when (adapter) {
            is AllAppsAdapter -> allAppsSize(adapter, prefs)
            is FavoritesAdapter -> prefs.favoriteCardSize
            is CollectionAdapter -> adapter.cardSize
            is WatchNextAdapter, is PreviewProgramsAdapter -> prefs.watchNextCardSize
            else -> 100
        }
        val density = context.resources.displayMetrics.density
        val baseSpacingDp = 12f
        val scale = cardSizePercent / 100f
        return (baseSpacingDp * scale * density).toInt()
    }

    private fun calculateRowHeight(adapter: androidx.recyclerview.widget.RecyclerView.Adapter<*>): Int {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val (baseCardHeightDp, cardSizePercent, textAreaDp) = when (adapter) {
            is AllAppsAdapter -> Triple(100f, allAppsSize(adapter, prefs).toFloat(), 20f)
            is FavoritesAdapter -> Triple(71f, prefs.favoriteCardSize.toFloat(), 20f)
            is CollectionAdapter -> Triple(100f, adapter.cardSize.toFloat(), 20f)
            is WatchNextAdapter, is PreviewProgramsAdapter -> Triple(138f, prefs.watchNextCardSize.toFloat(), 28f)
            else -> Triple(100f, 100f, 20f)
        }
        val density = context.resources.displayMetrics.density
        val scale = cardSizePercent / 100f
        val cardHeight = (baseCardHeightDp * scale * density).toInt()
        val textArea = (textAreaDp * scale * density).toInt()
        val focusScale = 1.1f
        val focusedTotal = ((cardHeight + textArea) * focusScale).toInt()
        val padding = (8f * density).toInt()
        return focusedTotal + padding * 2
    }

    init {
        inflate(context, R.layout.vertical_grid_row, this)
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        PreferenceManager.getDefaultSharedPreferences(context)
            .registerOnSharedPreferenceChangeListener(prefsListener)
        // Recycled rows miss the preference events that happened while they were
        // detached, so refresh the metrics as soon as the row is back.
        post { updateCarouselPadding() }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        sizeAnimator?.cancel()
        PreferenceManager.getDefaultSharedPreferences(context)
            .unregisterOnSharedPreferenceChangeListener(prefsListener)
    }
}
