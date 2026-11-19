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

/**
 * Picks a collection for an app launched from the card popup menu: the app is added
 * to the chosen collection, or to a brand new one created on the spot.
 */
class AddToCollectionActivity : ModalActivity(R.layout.activity_add_to_collection) {
    private val collectionGrid by lazy { findViewById<VerticalGridView>(R.id.add_collection_grid)!! }

    private val collectionAdapter by lazy { CollectionsAdapter() }

    private val prefs by lazy { PreferenceManager.getDefaultSharedPreferences(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME)
        if (packageName == null) {
            finish()
            return
        }

        collectionGrid.adapter = collectionAdapter
        collectionAdapter.onEntryClicked = { entry ->
            if (entry.id == CollectionsAdapter.NEW_COLLECTION_ID) {
                // Open the editor right away so the new collection can be named;
                // it already contains the app.
                startActivity(
                    Intent(this, CollectionEditActivity::class.java)
                        .putExtra(
                            CollectionEditActivity.EXTRA_COLLECTION_ID,
                            addToNewCollection(packageName)
                        )
                )
            } else {
                addToCollection(packageName, entry.id)
            }
            finish()
        }

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                LauncherRepository.collections(this@AddToCollectionActivity).collectLatest {
                    collectionAdapter.submitList(
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

    private fun addToCollection(packageName: String, collectionId: Long) {
        val collections = prefs.collections.map { collection ->
            if (collection.id == collectionId && packageName !in collection.packages) {
                collection.copy(packages = collection.packages + packageName)
            } else {
                collection
            }
        }
        prefs.edit { putString(COLLECTIONS_KEY, UserCollection.listToJson(collections)) }
    }

    private fun addToNewCollection(packageName: String): Long {
        val existing = prefs.collections
        val collection = UserCollection(
            id = -System.currentTimeMillis(),
            title = getString(R.string.new_collection) + " " + (existing.size + 1),
            packages = listOf(packageName),
        )
        prefs.edit {
            putString(COLLECTIONS_KEY, UserCollection.listToJson(existing + collection))
        }
        return collection.id
    }

    companion object {
        const val EXTRA_PACKAGE_NAME = "package_name"
    }
}
