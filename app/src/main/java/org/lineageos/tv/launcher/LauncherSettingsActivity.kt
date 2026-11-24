/*
 * SPDX-FileCopyrightText: 2024-2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.tv.launcher

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings

class LauncherSettingsActivity : ModalActivity(R.layout.activity_launcher_settings) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        findViewById<android.view.View>(R.id.appearance_item)?.setOnClickListener {
            startActivity(Intent(this, AppearanceActivity::class.java))
        }

        findViewById<android.view.View>(R.id.recent_apps_item)?.setOnClickListener {
            startActivity(Intent(this, RecentAppsSettingsActivity::class.java))
        }

        findViewById<android.view.View>(R.id.additional_item)?.setOnClickListener {
            // Prefer the system "Default apps" screen (standard intent, works across ROMs);
            // fall back to the in-app picker when the firmware doesn't provide it.
            val systemIntent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                .addCategory(Intent.CATEGORY_DEFAULT)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                startActivity(systemIntent)
            } catch (e: ActivityNotFoundException) {
                startActivity(Intent(this, ExternalLauncherActivity::class.java))
            }
        }

        findViewById<android.view.View>(R.id.android_settings_item)?.setOnClickListener {
            startActivity(
                Intent(android.provider.Settings.ACTION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}