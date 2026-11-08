/*
 * SPDX-FileCopyrightText: The LineageOS Project
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
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import org.lineageos.tv.launcher.R
import org.lineageos.tv.launcher.view.WatchNextCard

class WatchNextAdapter : ListAdapter<WatchNextProgram, WatchNextAdapter.ViewHolder>(DIFF_UTIL) {
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

    inner class ViewHolder(private val card: WatchNextCard) : RecyclerView.ViewHolder(card) {
        init {
            card.setOnClickListener {
                val context = card.context

                context.startActivity(card.launchIntent)
            }
            card.setOnLongClickListener {
                showContextMenu(card)
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

        private fun removeProgram(card: WatchNextCard, program: WatchNextProgram) {
            val resolver = card.context.contentResolver
            Thread {
                val result = runCatching {
                    resolver.delete(
                        TvContractCompat.buildWatchNextProgramUri(program.id), null, null
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

        fun bind(watchNextProgram: WatchNextProgram) {
            card.setInfo(watchNextProgram)
        }
    }

    companion object {
        @Suppress("RestrictedApi")
        private val DIFF_UTIL = object : DiffUtil.ItemCallback<WatchNextProgram>() {
            override fun areItemsTheSame(
                oldItem: WatchNextProgram,
                newItem: WatchNextProgram
            ) = oldItem.id == oldItem.id

            override fun areContentsTheSame(
                oldItem: WatchNextProgram,
                newItem: WatchNextProgram
            ) = oldItem.hasAnyUpdatedValues(newItem)
        }
    }
}
