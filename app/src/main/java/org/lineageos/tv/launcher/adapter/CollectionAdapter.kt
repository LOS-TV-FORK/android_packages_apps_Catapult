/*
 * SPDX-FileCopyrightText: 2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.tv.launcher.adapter

import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import android.view.ViewGroup
import androidx.appcompat.content.res.AppCompatResources
import java.util.Collections
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

    /** Persists the new package order when the user finishes moving a card. */
    var onOrderChanged: ((List<String>) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        AppCard(parent.context).apply {
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
        // After the bind: packageName is only known once the item is set.
        // Reordering applies to both modes; the synthetic "add apps" card is not an
        // app and cannot be moved.
        card.movable = card.packageName.isNotEmpty()
    }

    override fun handleKey(
        card: AppCard,
        keyCode: Int,
        event: KeyEvent?,
        bindingAdapterPosition: Int,
    ): Boolean {
        // While reordering, the center key settles the card and leaves the mode.
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            if (card.moving && event?.action == KeyEvent.ACTION_DOWN) {
                settleMove(card)
                return true
            }
            return false
        }

        if (event == null) {
            return false
        }

        if (event.action != KeyEvent.ACTION_DOWN) {
            return card.moving
        }

        when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                if (card.moving) {
                    settleMove(card)
                    return true
                }
                return false
            }

            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (card.moving) {
                    if (bindingAdapterPosition > 0) {
                        submitList(
                            currentList.toMutableList().apply {
                                Collections.swap(
                                    this, bindingAdapterPosition, bindingAdapterPosition - 1
                                )
                            }
                        )
                    }
                    return true
                }
            }

            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (card.moving) {
                    if (bindingAdapterPosition < currentList.size - 1) {
                        submitList(
                            currentList.toMutableList().apply {
                                Collections.swap(
                                    this, bindingAdapterPosition, bindingAdapterPosition + 1
                                )
                            }
                        )
                    }
                    return true
                }
            }

            // In grid mode vertical keys move the card across rows (one row is as
            // many items as there are columns); in a carousel they are blocked.
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (card.moving) {
                    if (gridMode) {
                        val step = if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                            gridColumns
                        } else {
                            -gridColumns
                        }
                        val target = bindingAdapterPosition + step
                        if (target in currentList.indices) {
                            submitList(
                                currentList.toMutableList().apply {
                                    Collections.swap(this, bindingAdapterPosition, target)
                                }
                            )
                        }
                    }
                    return true
                }
                return false
            }
        }

        return false
    }

    private fun settleMove(card: AppCard) {
        card.setMoveDone()
        onOrderChanged?.invoke(currentList.map { it.packageName }.filter { it.isNotEmpty() })
    }

    override fun handleLongClick(card: AppCard): Boolean {
        // Same app menu as in the all-apps row; the synthetic "add apps" entry has
        // no package to act on.
        if (card.packageName.isNotEmpty()) {
            card.showPopupMenu()
        }
        return true
    }

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
