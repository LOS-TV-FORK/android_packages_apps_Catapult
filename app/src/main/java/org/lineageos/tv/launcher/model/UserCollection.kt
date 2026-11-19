/*
 * SPDX-FileCopyrightText: 2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.tv.launcher.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * A user-created row of app cards. Rendered as an internal pseudo-channel, so it
 * takes part in the channel visibility/order settings like favorites does.
 *
 * [cardSize] and [cornerRadius] are the collection's own values, independent of
 * the global app-card settings; grid mode derives the card size from the columns.
 */
data class UserCollection(
    val id: Long,
    val title: String,
    val packages: List<String>,
    val grid: Boolean = false,
    val gridColumns: Int = 5,
    val cardSize: Int = 100,
    val cornerRadius: Int = 8,
) {
    // Only the structural part: changing size/radius must not rebuild the row.
    val settingsKey: String
        get() = "$grid|$gridColumns"

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("packages", packages.joinToString(","))
        put("grid", grid)
        put("grid_columns", gridColumns)
        put("card_size", cardSize)
        put("corner_radius", cornerRadius)
    }

    companion object {
        fun fromJson(obj: JSONObject): UserCollection? {
            val id = if (obj.has("id")) obj.getLong("id") else return null
            val packages = obj.optString("packages")
            return UserCollection(
                id = id,
                title = obj.optString("title"),
                packages = if (packages.isEmpty()) listOf() else packages.split(","),
                grid = obj.optBoolean("grid", false),
                gridColumns = obj.optInt("grid_columns", 5),
                cardSize = obj.optInt("card_size", 100),
                cornerRadius = obj.optInt("corner_radius", 8),
            )
        }

        fun listFromJson(json: String?): List<UserCollection> {
            if (json.isNullOrEmpty()) return listOf()
            return runCatching {
                val array = JSONArray(json)
                (0 until array.length()).mapNotNull { fromJson(array.getJSONObject(it)) }
            }.getOrDefault(listOf())
        }

        fun listToJson(collections: List<UserCollection>): String {
            val array = JSONArray()
            collections.forEach { array.put(it.toJson()) }
            return array.toString()
        }
    }
}
