/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.tv.launcher.ext

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

fun <T> SharedPreferences.valueFlow(
    key: String,
    valueGetter: SharedPreferences.(key: String) -> T,
) = callbackFlow {
    val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
        changedKey?.takeIf { it == key }?.let {
            trySend(valueGetter(it))
        }
    }

    registerOnSharedPreferenceChangeListener(listener)

    // Emit the latest value
    trySend(valueGetter(key))

    awaitClose {
        unregisterOnSharedPreferenceChangeListener(listener)
    }
}

const val FAVORITE_APPS_KEY = "favorite_apps"

/**
 * The list of apps the user added to favorites.
 */
var SharedPreferences.favoriteApps: List<String>
    get() = getString(FAVORITE_APPS_KEY, null)?.split(",") ?: listOf()
    set(value) = edit {
        putString(FAVORITE_APPS_KEY, value.joinToString(","))
    }

const val HIDDEN_APPS_KEY = "hidden_apps"

/**
 * The set of apps hidden by the user from the favorites carousel and all apps.
 */
var SharedPreferences.hiddenApps: Set<String>
    get() = getStringSet(HIDDEN_APPS_KEY, setOf()) ?: setOf()
    set(value) = edit {
        putStringSet(HIDDEN_APPS_KEY, value)
    }

const val KNOWN_CHANNELS_KEY = "known_channels"

/**
 * The list of known channels, used for ordering.
 */
var SharedPreferences.knownChannels: List<Long>
    get() = getString(KNOWN_CHANNELS_KEY, null)?.split(",")?.map {
        it.toLong()
    } ?: listOf()
    set(value) = edit {
        putString(KNOWN_CHANNELS_KEY, value.joinToString(","))
    }

const val HIDDEN_CHANNELS_KEY = "hidden_channels"

/**
 * The list of internal channels' IDs hidden by the user.
 */
var SharedPreferences.hiddenChannels: Set<Long>
    get() = getStringSet(HIDDEN_CHANNELS_KEY, setOf())?.map {
        it.toLong()
    }?.toSet() ?: setOf()
    set(value) = edit {
        putStringSet(HIDDEN_CHANNELS_KEY, value.map { it.toString() }.toSet())
    }

const val ENABLED_CHANNELS_KEY = "enabled_channels"

/**
 * The list of content provider channels' IDs explicitly enabled by the user.
 * Provider channels are opt-in: hidden until enabled in the channel settings.
 */
var SharedPreferences.enabledChannels: Set<Long>
    get() = getStringSet(ENABLED_CHANNELS_KEY, setOf())?.map {
        it.toLong()
    }?.toSet() ?: setOf()
    set(value) = edit {
        putStringSet(ENABLED_CHANNELS_KEY, value.map { it.toString() }.toSet())
    }

const val HOME_ROLE_REQUEST_DIALOG_DISMISSED = "home_role_request_dialog_dismissed"

/**
 * Whether the user asked to never show again the home role request dialog.
 */
var SharedPreferences.homeRoleRequestDialogDismissed: Boolean
    get() = getBoolean(HOME_ROLE_REQUEST_DIALOG_DISMISSED, false)
    set(value) = edit {
        putBoolean(HOME_ROLE_REQUEST_DIALOG_DISMISSED, value)
    }

const val BACKGROUND_TYPE_KEY = "background_type"

var SharedPreferences.backgroundType: Int
    get() = getInt(BACKGROUND_TYPE_KEY, 3)
    set(value) = edit {
        putInt(BACKGROUND_TYPE_KEY, value)
    }

const val BACKGROUND_COLOR_KEY = "background_color"

var SharedPreferences.backgroundColor: Int
    get() = getInt(BACKGROUND_COLOR_KEY, -1)
    set(value) = edit {
        putInt(BACKGROUND_COLOR_KEY, value)
    }

const val BACKGROUND_IMAGE_URI_KEY = "background_image_uri"

var SharedPreferences.backgroundImageUri: String?
    get() = getString(BACKGROUND_IMAGE_URI_KEY, null)
    set(value) = edit {
        putString(BACKGROUND_IMAGE_URI_KEY, value)
    }

const val BACKGROUND_BLUR_KEY = "background_blur"

var SharedPreferences.backgroundBlur: Int
    get() = getInt(BACKGROUND_BLUR_KEY, 7)
    set(value) = edit {
        putInt(BACKGROUND_BLUR_KEY, value.coerceIn(0, 30))
    }

const val BACKGROUND_AUTO_PERF_KEY = "background_auto_perf"

var SharedPreferences.backgroundAutoPerf: Boolean
    get() = getBoolean(BACKGROUND_AUTO_PERF_KEY, true)
    set(value) = edit {
        putBoolean(BACKGROUND_AUTO_PERF_KEY, value)
    }

const val BACKGROUND_AUTO_TIER_KEY = "background_auto_tier"

var SharedPreferences.backgroundAutoTier: Int
    get() = getInt(BACKGROUND_AUTO_TIER_KEY, 0)
    set(value) = edit {
        putInt(BACKGROUND_AUTO_TIER_KEY, value.coerceIn(0, 3))
    }

const val BACKGROUND_KB_LOW_FPS_KEY = "background_kb_low_fps"

var SharedPreferences.backgroundKbLowFps: Boolean
    get() = getBoolean(BACKGROUND_KB_LOW_FPS_KEY, false)
    set(value) = edit {
        putBoolean(BACKGROUND_KB_LOW_FPS_KEY, value)
    }

const val BACKGROUND_KB_PAUSE_KEY = "background_kb_pause"

var SharedPreferences.backgroundKbPause: Boolean
    get() = getBoolean(BACKGROUND_KB_PAUSE_KEY, false)
    set(value) = edit {
        putBoolean(BACKGROUND_KB_PAUSE_KEY, value)
    }

const val BACKGROUND_BLUR_PREBAKED_KEY = "background_blur_prebaked"

var SharedPreferences.backgroundBlurPrebaked: Boolean
    get() = getBoolean(BACKGROUND_BLUR_PREBAKED_KEY, false)
    set(value) = edit {
        putBoolean(BACKGROUND_BLUR_PREBAKED_KEY, value)
    }

const val APP_CARD_SIZE_KEY = "app_card_size"

var SharedPreferences.appCardSize: Int
    get() = getInt(APP_CARD_SIZE_KEY, 100)
    set(value) = edit {
        putInt(APP_CARD_SIZE_KEY, value)
    }

const val FAVORITE_CARD_SIZE_KEY = "favorite_card_size"

var SharedPreferences.favoriteCardSize: Int
    get() = getInt(FAVORITE_CARD_SIZE_KEY, 100)
    set(value) = edit {
        putInt(FAVORITE_CARD_SIZE_KEY, value)
    }

const val WATCH_NEXT_CARD_SIZE_KEY = "watch_next_card_size"

var SharedPreferences.watchNextCardSize: Int
    get() = getInt(WATCH_NEXT_CARD_SIZE_KEY, 100)
    set(value) = edit {
        putInt(WATCH_NEXT_CARD_SIZE_KEY, value)
    }

const val ALL_APPS_GRID_KEY = "all_apps_grid"

var SharedPreferences.allAppsGrid: Boolean
    get() = getBoolean(ALL_APPS_GRID_KEY, true)
    set(value) = edit {
        putBoolean(ALL_APPS_GRID_KEY, value)
    }

const val ALL_APPS_GRID_COLUMNS_KEY = "all_apps_grid_columns"

var SharedPreferences.allAppsGridColumns: Int
    get() = getInt(ALL_APPS_GRID_COLUMNS_KEY, 5)
    set(value) = edit {
        putInt(ALL_APPS_GRID_COLUMNS_KEY, value)
    }

const val CARD_CORNER_RADIUS_KEY = "card_corner_radius"

var SharedPreferences.cardCornerRadius: Int
    get() = getInt(CARD_CORNER_RADIUS_KEY, 8)
    set(value) = edit {
        putInt(CARD_CORNER_RADIUS_KEY, value)
    }

const val CARD_BACKGROUND_TYPE_KEY = "card_background_type"

var SharedPreferences.cardBackgroundType: Int
    get() = getInt(CARD_BACKGROUND_TYPE_KEY, 0)
    set(value) = edit {
        putInt(CARD_BACKGROUND_TYPE_KEY, value)
    }

const val CARD_BACKGROUND_COLOR_KEY = "card_background_color"

var SharedPreferences.cardBackgroundColor: Int
    get() = getInt(CARD_BACKGROUND_COLOR_KEY, 0xFF424242.toInt())
    set(value) = edit {
        putInt(CARD_BACKGROUND_COLOR_KEY, value)
    }

const val CARD_BACKGROUND_GRADIENT_START_KEY = "card_background_gradient_start"

var SharedPreferences.cardBackgroundGradientStart: Int
    get() = getInt(CARD_BACKGROUND_GRADIENT_START_KEY, 0xFF1976D2.toInt())
    set(value) = edit {
        putInt(CARD_BACKGROUND_GRADIENT_START_KEY, value)
    }

const val CARD_BACKGROUND_GRADIENT_END_KEY = "card_background_gradient_end"

var SharedPreferences.cardBackgroundGradientEnd: Int
    get() = getInt(CARD_BACKGROUND_GRADIENT_END_KEY, 0xFF7B1FA2.toInt())
    set(value) = edit {
        putInt(CARD_BACKGROUND_GRADIENT_END_KEY, value)
    }

const val CARD_BACKGROUND_GRADIENT_MODE_KEY = "card_background_gradient_mode"

var SharedPreferences.cardBackgroundGradientMode: Int
    get() = getInt(CARD_BACKGROUND_GRADIENT_MODE_KEY, 0)
    set(value) = edit {
        putInt(CARD_BACKGROUND_GRADIENT_MODE_KEY, value)
    }

const val CARD_BACKGROUND_ICON_GRADIENT_MODE_KEY = "card_background_icon_gradient_mode"

var SharedPreferences.cardBackgroundIconGradientMode: Int
    get() = getInt(CARD_BACKGROUND_ICON_GRADIENT_MODE_KEY, 0)
    set(value) = edit {
        putInt(CARD_BACKGROUND_ICON_GRADIENT_MODE_KEY, value)
    }

const val RECENT_APPS_ENABLED_KEY = "recent_apps_enabled"

var SharedPreferences.recentAppsEnabled: Boolean
    get() = getBoolean(RECENT_APPS_ENABLED_KEY, true)
    set(value) = edit {
        putBoolean(RECENT_APPS_ENABLED_KEY, value)
    }

const val RECENT_APPS_CARD_SIZE_KEY = "recent_apps_card_size"

var SharedPreferences.recentAppsCardSize: Int
    get() = getInt(RECENT_APPS_CARD_SIZE_KEY, 35)
    set(value) = edit {
        putInt(RECENT_APPS_CARD_SIZE_KEY, value)
    }

const val RECENT_APPS_SHOW_THUMBNAILS_KEY = "recent_apps_show_thumbnails"

var SharedPreferences.recentAppsShowThumbnails: Boolean
    get() = getBoolean(RECENT_APPS_SHOW_THUMBNAILS_KEY, true)
    set(value) = edit {
        putBoolean(RECENT_APPS_SHOW_THUMBNAILS_KEY, value)
    }

const val EXTERNAL_LAUNCHER_PKG_KEY = "external_launcher_pkg"

var SharedPreferences.externalLauncherPkg: String?
    get() = getString(EXTERNAL_LAUNCHER_PKG_KEY, null)
    set(value) = edit {
        putString(EXTERNAL_LAUNCHER_PKG_KEY, value)
    }

const val SHOW_ASSISTANT_BUTTONS_KEY = "show_assistant_buttons"

var SharedPreferences.showAssistantButtons: Boolean
    get() = getBoolean(SHOW_ASSISTANT_BUTTONS_KEY, false)
    set(value) = edit {
        putBoolean(SHOW_ASSISTANT_BUTTONS_KEY, value)
    }

const val SHOW_VPN_BUTTON_KEY = "show_vpn_button"

var SharedPreferences.showVpnButton: Boolean
    get() = getBoolean(SHOW_VPN_BUTTON_KEY, true)
    set(value) = edit {
        putBoolean(SHOW_VPN_BUTTON_KEY, value)
    }

const val TOP_BAR_AUTO_HIDE_KEY = "top_bar_auto_hide"

var SharedPreferences.topBarAutoHide: Boolean
    get() = getBoolean(TOP_BAR_AUTO_HIDE_KEY, true)
    set(value) = edit {
        putBoolean(TOP_BAR_AUTO_HIDE_KEY, value)
    }

const val SHOW_CLOCK_KEY = "show_clock"

var SharedPreferences.showClock: Boolean
    get() = getBoolean(SHOW_CLOCK_KEY, true)
    set(value) = edit {
        putBoolean(SHOW_CLOCK_KEY, value)
    }

const val CLOCK_TEXT_SIZE_KEY = "clock_text_size"

var SharedPreferences.clockTextSize: Int
    get() = getInt(CLOCK_TEXT_SIZE_KEY, 32)
    set(value) = edit {
        putInt(CLOCK_TEXT_SIZE_KEY, value)
    }
