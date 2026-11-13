/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.tv.launcher

import android.os.Bundle
import androidx.activity.viewModels
import androidx.leanback.widget.VerticalGridView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.lineageos.tv.launcher.adapter.ModifyFavoritesAdapter
import org.lineageos.tv.launcher.utils.AppManager
import org.lineageos.tv.launcher.utils.PermissionsGatedCallback
import org.lineageos.tv.launcher.viewmodels.HiddenAppsViewModel

class HiddenAppsActivity : ModalActivity(R.layout.activity_hidden_apps) {
    // View models
    private val model: HiddenAppsViewModel by viewModels()

    // Views
    private val hiddenAppsGridView by lazy { findViewById<VerticalGridView>(R.id.hidden_apps_grid)!! }

    // Adapters
    private val hiddenAppsAdapter by lazy { ModifyFavoritesAdapter() }

    private val permissionsGatedCallback = PermissionsGatedCallback(this) {
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.appsToHidden.collectLatest {
                    hiddenAppsAdapter.submitList(it)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        hiddenAppsGridView.adapter = hiddenAppsAdapter

        hiddenAppsAdapter.onFavoriteChanged = { packageName, hidden ->
            AppManager.toggleHiddenApp(this@HiddenAppsActivity, packageName, hidden)
        }

        permissionsGatedCallback.runAfterPermissionsCheck()
    }
}
