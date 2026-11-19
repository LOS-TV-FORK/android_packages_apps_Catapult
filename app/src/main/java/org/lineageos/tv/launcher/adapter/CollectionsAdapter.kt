/*
 * SPDX-FileCopyrightText: 2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.tv.launcher.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.lineageos.tv.launcher.R

/**
 * The "My collections" list. The id of the first entry is [NEW_COLLECTION_ID]: it
 * creates a new collection instead of opening an existing one.
 */
class CollectionsAdapter : ListAdapter<CollectionsAdapter.Entry, CollectionsAdapter.ViewHolder>(
    diffCallback
) {
    data class Entry(val id: Long, val title: String, val appCount: Int)

    var onEntryClicked: (Entry) -> Unit = {}

    inner class ViewHolder(itemView: ViewGroup) : RecyclerView.ViewHolder(itemView) {
        private val icon: ImageView = itemView.findViewById(R.id.collection_item_icon)!!
        private val title: TextView = itemView.findViewById(R.id.collection_item_title)!!
        private val subtitle: TextView = itemView.findViewById(R.id.collection_item_subtitle)!!

        init {
            itemView.setOnClickListener {
                onEntryClicked(getItem(bindingAdapterPosition))
            }
        }

        fun bind(entry: Entry) {
            if (entry.id == NEW_COLLECTION_ID) {
                icon.setImageDrawable(
                    ContextCompat.getDrawable(itemView.context, R.drawable.ic_add)
                )
                title.setText(R.string.new_collection)
                subtitle.visibility = android.view.View.GONE
            } else {
                icon.setImageDrawable(
                    ContextCompat.getDrawable(itemView.context, R.drawable.ic_apps)
                )
                title.text = entry.title
                subtitle.visibility = android.view.View.VISIBLE
                subtitle.text = itemView.resources
                    .getQuantityString(R.plurals.collection_app_count, entry.appCount, entry.appCount)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        LayoutInflater.from(parent.context)
            .inflate(R.layout.collection_list_item, parent, false) as ViewGroup
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        const val NEW_COLLECTION_ID = 0L

        private val diffCallback = object : DiffUtil.ItemCallback<Entry>() {
            override fun areItemsTheSame(oldItem: Entry, newItem: Entry) = oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Entry, newItem: Entry) =
                oldItem.title == newItem.title && oldItem.appCount == newItem.appCount
        }
    }
}
