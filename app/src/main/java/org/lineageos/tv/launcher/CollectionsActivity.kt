/*
 * SPDX-FileCopyrightText: 2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.tv.launcher

import android.content.Intent
import android.os.Bundle
import androidx.core.content.edit
import androidx.leanback.widget.VerticalGridView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.PreferenceManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.lineageos.tv.launcher.adapter.CollectionsAdapter
import org.lineageos.tv.launcher.ext.COLLECTIONS_KEY
import org.lineageos.tv.launcher.ext.collections
import org.lineageos.tv.launcher.model.UserCollection
import org.lineageos.tv.launcher.repository.LauncherRepository

class CollectionsActivity : ModalActivity(R.layout.activity_collections) {
    private val collectionsGrid by lazy { findViewById<VerticalGridView>(R.id.collections_grid)!! }

    private val collectionsAdapter by lazy { CollectionsAdapter() }

    private val prefs by lazy { PreferenceManager.getDefaultSharedPreferences(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        collectionsGrid.adapter = collectionsAdapter

        collectionsAdapter.onEntryClicked = { entry ->
            val id = if (entry.id == CollectionsAdapter.NEW_COLLECTION_ID) {
                createCollection()
            } else {
                entry.id
            }
            startActivity(
                Intent(this, CollectionEditActivity::class.java)
                    .putExtra(CollectionEditActivity.EXTRA_COLLECTION_ID, id)
            )
        }

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                LauncherRepository.collections(this@CollectionsActivity).collectLatest {
                    collectionsAdapter.submitList(
                        listOf(
                            CollectionsAdapter.Entry(
                                CollectionsAdapter.NEW_COLLECTION_ID, "", 0
                            )
                        ) + it.map { collection ->
                            CollectionsAdapter.Entry(
                                collection.id,
                                collection.title,
                                collection.packages.size
                            )
                        }
                    )
                }
            }
        }
    }

    private fun createCollection(): Long {
        val id = -System.currentTimeMillis()
        val existing = prefs.collections
        prefs.edit {
            putString(
                COLLECTIONS_KEY,
                UserCollection.listToJson(
                    existing + UserCollection(
                        id = id,
                        title = getString(R.string.new_collection) + " " + (existing.size + 1),
                        packages = listOf(),
                    )
                )
            )
        }
        return id
    }
}
