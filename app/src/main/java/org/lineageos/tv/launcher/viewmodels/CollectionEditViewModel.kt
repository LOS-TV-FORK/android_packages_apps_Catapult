/*
 * SPDX-FileCopyrightText: 2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.tv.launcher.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.lineageos.tv.launcher.ext.collections
import org.lineageos.tv.launcher.ext.context
import org.lineageos.tv.launcher.ext.deleteCollection
import org.lineageos.tv.launcher.ext.updateCollection
import org.lineageos.tv.launcher.model.UserCollection
import org.lineageos.tv.launcher.repository.LauncherRepository

@OptIn(ExperimentalCoroutinesApi::class)
class CollectionEditViewModel(application: Application) : AndroidViewModel(application) {
    private val collectionId = MutableStateFlow(-1L)

    fun setCollection(id: Long) {
        collectionId.value = id
    }

    val collection = collectionId.flatMapLatest { id ->
        LauncherRepository.collections(context).map { collections ->
            collections.find { it.id == id }
        }
    }.flowOn(Dispatchers.IO)
        .stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = null,
        )

    val appsToSelected = combine(LauncherRepository.installedApps(context), collection) {
        installedApps, collection ->
        installedApps.map { it to (collection?.packages?.contains(it.packageName) == true) }
    }.flowOn(Dispatchers.IO)
        .stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = listOf(),
        )

    private fun mutate(block: (UserCollection) -> UserCollection) {
        val id = collectionId.value
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.collections = prefs.collections.map {
            if (it.id == id) block(it) else it
        }
    }

    fun setTitle(title: String) = mutate { it.copy(title = title) }

    fun toggleApp(packageName: String, selected: Boolean) = mutate {
        val packages = it.packages.toMutableList()
        if (selected) {
            if (!packages.contains(packageName)) packages.add(packageName)
        } else {
            packages.remove(packageName)
        }
        it.copy(packages = packages)
    }

    fun setGrid(grid: Boolean) = mutate { it.copy(grid = grid) }

    fun setGridColumns(columns: Int) = mutate { it.copy(gridColumns = columns) }

    fun setCardSize(sizePercent: Int) = mutate { it.copy(cardSize = sizePercent) }

    fun setCornerRadius(radiusDp: Int) = mutate { it.copy(cornerRadius = radiusDp) }

    fun delete() {
        val id = collectionId.value
        PreferenceManager.getDefaultSharedPreferences(context).deleteCollection(id)
    }

    fun updateCollection(collection: UserCollection) {
        PreferenceManager.getDefaultSharedPreferences(context).updateCollection(collection)
    }
}
