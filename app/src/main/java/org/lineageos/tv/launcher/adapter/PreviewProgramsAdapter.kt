/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.tv.launcher.adapter

import android.view.Gravity
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import org.lineageos.tv.launcher.R
import org.lineageos.tv.launcher.view.WatchNextCard

class PreviewProgramsAdapter(
    private val channelTitle: String = "",
) : ListAdapter<PreviewProgram, PreviewProgramsAdapter.ViewHolder>(diffCallback) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        WatchNextCard(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        }
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val watchNextCard: WatchNextCard
    ) : RecyclerView.ViewHolder(watchNextCard) {
        init {
            watchNextCard.setOnClickListener {
                val context = watchNextCard.context

                context.startActivity(watchNextCard.launchIntent)
            }
            watchNextCard.setOnLongClickListener {
                showContextMenu(watchNextCard)
                true
            }
        }

        private fun showContextMenu(card: WatchNextCard) {
            val position = bindingAdapterPosition
            if (position == RecyclerView.NO_POSITION) {
                return
            }
            val program = getItem(position)
            val popupMenu = PopupMenu(card.context, card, Gravity.START, 0, R.style.PopupMenu)
            popupMenu.menuInflater.inflate(R.menu.preview_program_long_press, popupMenu.menu)
            popupMenu.menu.findItem(R.id.menu_remove_program).title =
                card.context.getString(R.string.remove_from_channel, channelTitle)
            popupMenu.setForceShowIcon(true)
            popupMenu.setOnMenuItemClickListener { menuItem ->
                if (menuItem.itemId == R.id.menu_remove_program) {
                    removeProgram(card, program)
                    true
                } else {
                    false
                }
            }
            popupMenu.show()
        }

        // Fully deletes the row from the TV provider. The owning app may publish it
        // again later. On success the reactive query removes the card automatically.
        private fun removeProgram(card: WatchNextCard, program: PreviewProgram) {
            val resolver = card.context.contentResolver
            Thread {
                val result = runCatching {
                    resolver.delete(
                        TvContractCompat.buildPreviewProgramUri(program.id),
                        null,
                        null,
                    )
                }
                card.post {
                    result.exceptionOrNull()?.let {
                        Toast.makeText(
                            card.context, R.string.remove_program_failed, Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }.start()
        }

        fun bind(previewProgram: PreviewProgram) {
            watchNextCard.applyCardSizeScaling()
            watchNextCard.applyCornerRadius()
            watchNextCard.setInfo(previewProgram)
        }
    }

    companion object {
        private val diffCallback = object : DiffUtil.ItemCallback<PreviewProgram>() {
            @Suppress("RestrictedApi")
            override fun areItemsTheSame(
                oldItem: PreviewProgram,
                newItem: PreviewProgram
            ) = oldItem.id == newItem.id

            override fun areContentsTheSame(
                oldItem: PreviewProgram,
                newItem: PreviewProgram
            ) = oldItem.hasAnyUpdatedValues(newItem)
        }
    }
}
