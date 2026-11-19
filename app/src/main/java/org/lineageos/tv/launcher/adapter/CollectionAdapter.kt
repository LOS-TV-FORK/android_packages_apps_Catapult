/*
 * SPDX-FileCopyrightText: 2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.tv.launcher.adapter

import android.content.Context
import android.content.Intent
import android.view.ViewGroup
import androidx.appcompat.content.res.AppCompatResources
import org.lineageos.tv.launcher.CollectionEditActivity
import org.lineageos.tv.launcher.R
import org.lineageos.tv.launcher.model.ActivityLauncher
import org.lineageos.tv.launcher.model.Launchable
import org.lineageos.tv.launcher.view.AppCard

/**
 * One user collection row. The cards are the same app cards as the all-apps row:
 * grid mode derives the card size from the column count exactly like the all-apps
 * grid does; carousel mode uses the global app-card size unless overridden.
 */
class CollectionAdapter : TvAdapter<Launchable, AppCard>() {
    var gridMode = false
    var gridColumns = 5
    var cardSize = 100
    var cornerRadius = 8

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        AppCard(parent.context).apply {
            hideable = true
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        }
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        // Applied before the bind so the icon-derived background built by setCardInfo
        // already uses this collection's corner radius.
        val card = holder.itemView as AppCard
        card.applyCardSettings(
            if (gridMode) null else cardSize / 100f,
            if (gridMode) null else cornerRadius,
            gridMode,
        )
        super.onBindViewHolder(holder, position)
    }

    override fun handleLongClick(card: AppCard) = true

    companion object {
        fun createAddToCollectionEntry(context: Context, collectionId: Long): Launchable {
            return ActivityLauncher(
                context.getString(R.string.collection_add_apps),
                AppCompatResources.getDrawable(context, R.drawable.ic_add)!!,
                context,
                Intent(context, CollectionEditActivity::class.java)
                    .putExtra(CollectionEditActivity.EXTRA_COLLECTION_ID, collectionId)
            )
        }
    }
}
