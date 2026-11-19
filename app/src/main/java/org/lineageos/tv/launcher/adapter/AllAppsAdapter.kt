/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.tv.launcher.adapter

import android.view.ViewGroup
import androidx.preference.PreferenceManager
import org.lineageos.tv.launcher.model.LeanbackAppInfo
import org.lineageos.tv.launcher.view.AppCard
import org.lineageos.tv.launcher.ext.appCardSize

class AllAppsAdapter : TvAdapter<LeanbackAppInfo, AppCard>() {

    var gridMode = false

    // Transient card size used while the resize animation runs; null means the
    // value from preferences.
    var liveSizeOverride: Int? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val card = AppCard(parent.context).apply {
            setGridMode(gridMode)
            hideable = true
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        }
        return ViewHolder(card)
    }

    override fun handleLongClick(card: AppCard): Boolean {
        card.showPopupMenu()
        return true
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        // Re-read the card size/corner radius from prefs at bind time: recycled holders
        // were created with the old values and would otherwise keep them.
        (holder.itemView as AppCard).applyCardSettings(
            liveSizeOverride?.let { it / 100f }, null, gridMode
        )
        super.onBindViewHolder(holder, position)
    }
}
