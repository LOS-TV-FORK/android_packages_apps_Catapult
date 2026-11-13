/*
 * SPDX-FileCopyrightText: 2024-2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.tv.launcher

import android.app.ActivityManager
import android.app.role.RoleManager
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.animation.ObjectAnimator
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.os.Handler
import android.util.AttributeSet
import android.content.Context
import android.view.ViewGroup

import android.transition.Slide
import android.transition.TransitionManager
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import androidx.palette.graphics.Palette
import androidx.leanback.widget.VerticalGridView
import androidx.preference.PreferenceManager
import androidx.appcompat.content.res.AppCompatResources
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.lineageos.tv.launcher.adapter.AllAppsAdapter
import org.lineageos.tv.launcher.adapter.FavoritesAdapter
import org.lineageos.tv.launcher.adapter.MainVerticalAdapter
import org.lineageos.tv.launcher.adapter.PreviewProgramsAdapter
import org.lineageos.tv.launcher.adapter.WatchNextAdapter
import org.lineageos.tv.launcher.ext.favoriteApps
import org.lineageos.tv.launcher.ext.homeRoleRequestDialogDismissed
import org.lineageos.tv.launcher.ext.roleCanBeRequested
import org.lineageos.tv.launcher.ext.backgroundType
import org.lineageos.tv.launcher.ext.backgroundColor
import org.lineageos.tv.launcher.ext.backgroundBlur
import org.lineageos.tv.launcher.ext.backgroundBlurPrebaked
import org.lineageos.tv.launcher.ext.backgroundAutoPerf
import org.lineageos.tv.launcher.ext.backgroundAutoTier
import org.lineageos.tv.launcher.ext.backgroundKbLowFps
import org.lineageos.tv.launcher.ext.backgroundKbPause
import org.lineageos.tv.launcher.ext.backgroundImageUri
import org.lineageos.tv.launcher.ext.BACKGROUND_BLUR_KEY
import org.lineageos.tv.launcher.ext.BACKGROUND_IMAGE_URI_KEY
import org.lineageos.tv.launcher.ext.BACKGROUND_TYPE_KEY
import org.lineageos.tv.launcher.ext.allAppsGrid
import org.lineageos.tv.launcher.ext.allAppsGridColumns
import org.lineageos.tv.launcher.ext.APP_CARD_SIZE_KEY
import org.lineageos.tv.launcher.ext.CARD_CORNER_RADIUS_KEY
import org.lineageos.tv.launcher.ext.FAVORITE_CARD_SIZE_KEY
import org.lineageos.tv.launcher.ext.WATCH_NEXT_CARD_SIZE_KEY
import org.lineageos.tv.launcher.ext.appCardSize
import org.lineageos.tv.launcher.ext.favoriteCardSize
import org.lineageos.tv.launcher.ext.watchNextCardSize
import org.lineageos.tv.launcher.ext.cardCornerRadius
import org.lineageos.tv.launcher.ext.cardBackgroundType
import org.lineageos.tv.launcher.ext.cardBackgroundColor
import org.lineageos.tv.launcher.ext.cardBackgroundGradientStart
import org.lineageos.tv.launcher.ext.cardBackgroundGradientEnd
import org.lineageos.tv.launcher.ext.cardBackgroundGradientMode
import org.lineageos.tv.launcher.ext.cardBackgroundIconGradientMode
import org.lineageos.tv.launcher.ext.SHOW_ASSISTANT_BUTTONS_KEY
import org.lineageos.tv.launcher.ext.SHOW_VPN_BUTTON_KEY
import org.lineageos.tv.launcher.ext.TOP_BAR_AUTO_HIDE_KEY
import org.lineageos.tv.launcher.ext.SHOW_CLOCK_KEY
import org.lineageos.tv.launcher.ext.CLOCK_TEXT_SIZE_KEY
import org.lineageos.tv.launcher.ext.showAssistantButtons
import org.lineageos.tv.launcher.ext.showVpnButton
import org.lineageos.tv.launcher.ext.topBarAutoHide
import org.lineageos.tv.launcher.ext.showClock
import org.lineageos.tv.launcher.ext.clockTextSize
import org.lineageos.tv.launcher.model.AppInfo
import org.lineageos.tv.launcher.model.InternalChannel
import org.lineageos.tv.launcher.model.MainRowItem
import org.lineageos.tv.launcher.view.Card
import org.lineageos.tv.launcher.notification.NotificationUtils
import org.lineageos.tv.launcher.notification.ServiceConnectionState
import org.lineageos.tv.launcher.utils.AppManager
import org.lineageos.tv.launcher.utils.DynamicBackgroundHelper
import org.lineageos.tv.launcher.utils.PermissionsGatedCallback
import org.lineageos.tv.launcher.viewmodels.LauncherViewModel
import org.lineageos.tv.launcher.viewmodels.NotificationViewModel
import java.util.Locale
import android.app.ActivityOptions
import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.util.Log
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.DecelerateInterpolator
import android.view.animation.RotateAnimation
import android.view.animation.ScaleAnimation
import android.view.animation.TranslateAnimation
import androidx.leanback.widget.HorizontalGridView
import org.lineageos.tv.launcher.ext.externalLauncherPkg
import org.lineageos.tv.launcher.ext.recentAppsCardSize
import org.lineageos.tv.launcher.ext.recentAppsEnabled
import org.lineageos.tv.launcher.ext.recentAppsShowThumbnails
import org.lineageos.tv.launcher.model.LeanbackAppInfo
import org.lineageos.tv.launcher.view.AppCard

class MainActivity : AppCompatActivity(R.layout.activity_main) {
    companion object {
        @Volatile
        private var instance: MainActivity? = null

        var isMainActivityVisible = false
        private var lastResumedTime = 0L
        private const val VPN_PACKAGE_KEY = "vpn_package"

        private const val TAG = "MainActivity"

        fun getLastResumedTime() = lastResumedTime

        fun finishSelf() {
            instance?.finish()
        }

        fun finishWithOverlay() {
            instance?.finish()
        }
    }

    // Card size tracking
    private var lastAppCardSize = 100
    private var lastFavoriteCardSize = 100
    private var lastWatchNextCardSize = 100
    private var lastAllAppsGrid = false
    private var lastAllAppsGridColumns = 4
    private var lastCardCornerRadius = 8
    private var lastCardBackgroundType = 0
    private var lastCardBackgroundColor = 0xFF424242.toInt()
    private var lastCardBackgroundGradientStart = 0xFF1976D2.toInt()
    private var lastCardBackgroundGradientEnd = 0xFF7B1FA2.toInt()
    private var lastCardBackgroundGradientMode = 0
    private var lastCardBackgroundIconGradientMode = 0


    // View models
    private val model: LauncherViewModel by viewModels()
    private val notificationViewModel: NotificationViewModel by viewModels()

    // Views
    private val assistantButtonsContainer by lazy { findViewById<LinearLayout>(R.id.assistant_buttons)!! }
    private val assistantHintImageView by lazy { findViewById<ImageView>(R.id.assistantHintImageView)!! }
    private val keyboardAssistantButton by lazy { findViewById<ImageButton>(R.id.keyboard_assistant)!! }
    private val settingButton by lazy { findViewById<ImageButton>(R.id.settingsMaterialButton)!! }
    private val vpnButton by lazy { findViewById<ImageButton>(R.id.vpnButton)!! }
    private val notificationCountTextView by lazy { findViewById<TextView>(R.id.notificationCountTextView)!! }
    private val notificationContainer by lazy { findViewById<LinearLayout>(R.id.notificationContainer)!! }
    private val topBarContainer by lazy { findViewById<LinearLayout>(R.id.top_bar)!! }
    private val clockTextView by lazy { findViewById<TextClock>(R.id.clockTextView)!! }
    private val assistantTransition by lazy {
        Slide().apply {
            slideEdge = Gravity.START
            duration = 400
        }
    }
    private val mainVerticalGridView by lazy { findViewById<MainVerticalGridView>(R.id.main_vertical_grid)!! }
    private val voiceAssistantButton by lazy { findViewById<ImageButton>(R.id.voice_assistant)!! }

    private val dynamicBackgroundContainer by lazy {
        findViewById<FrameLayout>(R.id.dynamicBackgroundContainer)!!
    }
    private val dynamicBackground by lazy {
        DynamicBackgroundHelper(
            dynamicBackgroundContainer,
            findViewById(R.id.dynamicBackgroundLayer),
            findViewById(R.id.dynamicBgA),
            findViewById(R.id.dynamicBgB),
            findViewById(R.id.dynamicBgC),
            resources.displayMetrics.density,
            ::applyPillTint
        ).also {
            it.setBlurRadius(sharedPreferences.backgroundBlur.toFloat())
            it.setAppCardPool(::collectAppIconDrawables)
        }
    }

    private fun applyBackgroundPerfPreferences() {
        dynamicBackground.setBlurRadius(sharedPreferences.backgroundBlur.toFloat())
        dynamicBackground.setPreBakedBlur(sharedPreferences.backgroundBlurPrebaked)
        dynamicBackground.setAutoTierListener { tier ->
            sharedPreferences.backgroundAutoTier = tier
        }
        dynamicBackground.setAutoPerf(
            sharedPreferences.backgroundAutoPerf,
            sharedPreferences.backgroundAutoTier
        )
        dynamicBackground.setLowFpsMode(sharedPreferences.backgroundKbLowFps)
        dynamicBackground.setPauseDuringTransitions(sharedPreferences.backgroundKbPause)
    }

    private fun collectAppIconDrawables(): List<Pair<String, Drawable>> {
        val out = ArrayList<Pair<String, Drawable>>()
        collectAppIconDrawablesInto(mainVerticalGridView, out)
        return out
    }

    private fun collectAppIconDrawablesInto(view: View, out: ArrayList<Pair<String, Drawable>>) {
        if (view is Card) {
            val bannerView = view.findViewById<ImageView>(R.id.app_banner)
            val banner = bannerView?.drawable?.takeIf {
                bannerView.visibility == View.VISIBLE && it !is ColorDrawable
            }
            val drawable = banner ?: view.findViewById<ImageView>(R.id.app_icon)?.drawable
            if (drawable != null && drawable !is ColorDrawable) {
                out.add("${view.packageName}/${view.label}" to drawable)
            }
            return
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) collectAppIconDrawablesInto(view.getChildAt(i), out)
        }
    }

    // Blurred background of the focused tile, behind the grid
    private fun updateDynamicBackground(focusView: View) {
        if (sharedPreferences.backgroundType != 3) return
        var view: View? = focusView
        while (view != null && view !is Card) {
            view = view.parent as? View
        }
        val card = view ?: return
        val key = "${card.packageName}/${card.label}@${System.identityHashCode(card)}"
        val iconView = card.findViewById<ImageView>(R.id.app_icon)
        if (iconView != null) {
            // TV banner (when the app ships/loads one) is the primary color
            // source for app cards; the phone icon is the fallback.
            dynamicBackground.setColorImage(key) {
                val bannerView = card.findViewById<ImageView>(R.id.app_banner)
                val banner = bannerView?.drawable?.takeIf {
                    bannerView.visibility == View.VISIBLE && it !is ColorDrawable
                }
                banner ?: iconView.drawable
            }
        } else {
            dynamicBackground.setImage(key) {
                val banner = card.findViewById<ImageView>(R.id.app_banner)
                if (banner != null && banner.visibility == View.VISIBLE && banner.drawable != null) {
                    banner.drawable
                } else {
                    card.findViewById<ImageView>(R.id.app_banner_background)?.drawable
                }
            }
        }
    }

    // Return to first row when focus moves to the top bar
    private val mainFocusScrollListener = ViewTreeObserver.OnGlobalFocusChangeListener { old, newFocus ->
        if (newFocus != null && isTopBarDescendant(newFocus)) {
            showTopBar()
            // Jump to row 0 only when the user scrolled up from the grid into
            // the bar. A focus restore after resume (no previous focus, or
            // focus coming from the bar itself) must not move the grid.
            if (old != null && isMainDescendant(old)) {
                // Row 0 must sit exactly at the bar bottom; otherwise it stays
                // clipped under the bar and focus coming back down skips it.
                mainVerticalGridView.setSelectedPosition(0)
                snapGridToTop()
                mainVerticalGridView.post { snapGridToTop() }
            }
        }
        if (newFocus != null && isMainDescendant(newFocus)) {
            updateDynamicBackground(newFocus)
        }
    }

    // Row 0 is the true top of the desktop: it can only be attached once
    // nothing is scrolled above it.
    private fun isGridAtTop(): Boolean {
        val lm = mainVerticalGridView.layoutManager
            as? androidx.leanback.widget.GridLayoutManager ?: return true
        return lm.findViewByPosition(0) != null
    }

    // Push a clipped row 0 back down to the inset line under the bar.
    private fun snapGridToTop() {
        val lm = mainVerticalGridView.layoutManager
            as? androidx.leanback.widget.GridLayoutManager ?: return
        val row0 = lm.findViewByPosition(0) ?: return
        val delta = row0.top - mainVerticalGridView.paddingTop
        if (delta < 0) {
            mainVerticalGridView.scrollBy(0, delta)
        }
    }

    // Auto-hide: slide the top bar out when the user scrolls deeper into the rows
    private var topBarAutoHidden = false
    private var topBarScrollAccum = 0

    private val topBarScrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            if (!sharedPreferences.topBarAutoHide) {
                return
            }
            if (dy > 0) {
                topBarScrollAccum += dy
                if (topBarScrollAccum > 40) {
                    hideTopBar()
                }
            } else if (dy < 0 && isGridAtTop()) {
                snapGridToTop()
                showTopBar()
            }
        }

        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            topBarScrollAccum = 0
            if (newState == RecyclerView.SCROLL_STATE_IDLE && isGridAtTop()) {
                snapGridToTop()
                showTopBar()
            }
        }
    }

    private fun showTopBar() {
        if (!topBarAutoHidden) {
            return
        }
        topBarAutoHidden = false
        topBarContainer.descendantFocusability = ViewGroup.FOCUS_BEFORE_DESCENDANTS
        topBarContainer.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(150)
            .start()
    }

    private fun hideTopBar() {
        if (topBarAutoHidden || topBarContainer.height == 0) {
            return
        }
        topBarAutoHidden = true
        topBarContainer.animate()
            .translationY(-topBarContainer.height.toFloat())
            .alpha(0f)
            .setDuration(150)
            .withEndAction {
                if (topBarAutoHidden) {
                    topBarContainer.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                }
            }
            .start()
    }

    private fun applyTopBarAutoHide() {
        if (!sharedPreferences.topBarAutoHide) {
            showTopBar()
        }
    }

    private var assistAvailable = false

    private fun applyTopBarPreferences() {
        val showAssistant = sharedPreferences.showAssistantButtons && assistAvailable
        if (showAssistant) {
            if (assistantHintImageView.visibility == View.GONE) {
                assistantHintImageView.isVisible = true
                assistantButtonsContainer.isVisible = false
            }
        } else {
            assistantHintImageView.visibility = View.GONE
            assistantButtonsContainer.visibility = View.GONE
        }

        vpnButton.isVisible = sharedPreferences.showVpnButton

        clockTextView.isVisible = sharedPreferences.showClock
        if (sharedPreferences.showClock) {
            clockTextView.textSize = sharedPreferences.clockTextSize.toFloat()
        }

        mainVerticalGridView.upFocusFallback = findTopBarFocusable()
    }

    // System services
    private val roleManager by lazy { getSystemService(RoleManager::class.java)!! }
    private val connectivityManager by lazy { getSystemService(ConnectivityManager::class.java)!! }

    // VPN status tracking
    private var vpnNetworkCallback: ConnectivityManager.NetworkCallback? = null
    private var vpnDefaultCallback: ConnectivityManager.NetworkCallback? = null

    // Activity request launchers
    private val homeRoleActivityRequestLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Do nothing
    }

    // Adapters
    private val allAppsAdapter by lazy { AllAppsAdapter() }
    private val favoritesAdapter by lazy { FavoritesAdapter() }
    private val mainVerticalAdapter by lazy { MainVerticalAdapter(this) }
    private val watchNextAdapter by lazy { WatchNextAdapter() }
    private val previewChannelAdapters = mutableMapOf<Long, PreviewProgramsAdapter>()

    private val sharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(this)
    }

    private val permissionsGatedCallback = PermissionsGatedCallback(this) {
                lifecycleScope.launch {
                    lifecycle.repeatOnLifecycle(Lifecycle.State.CREATED) {
                        combine(
                            model.watchNextPrograms,
                            model.channelsToPrograms
                        ) { watchNextPrograms, channels ->
                            channels.mapNotNull { channel ->
                                // Check if "Watch Next" should be skipped
                                if (channel.first.id == InternalChannel.WATCH_NEXT.id && watchNextPrograms.isEmpty()) {
                                    null
                                } else if (channel.first.previewChannel != null
                                    && channel.second.isNullOrEmpty()
                                ) {
                                    // Don't show a row until the provider publishes programs
                                    null
                                } else {
                                    channel.first.id to MainRowItem(
                                        channel.first.title,
                                        when (channel.first.id) {
                                            InternalChannel.FAVORITE_APPS.id -> favoritesAdapter
                                            InternalChannel.WATCH_NEXT.id -> watchNextAdapter
                                            InternalChannel.ALL_APPS.id -> allAppsAdapter
                                            else -> previewChannelAdapters.getOrPut(channel.first.id) {
                                                PreviewProgramsAdapter(channel.first.title)
                                            }.apply {
                                                channel.second?.let { previewPrograms ->
                                                    submitList(previewPrograms)
                                                }
                                            }
                                        }
                                    )
                                }
                            } to watchNextPrograms
                        }.collectLatest { (updatedList, watchNextPrograms) ->
                            // DiffUtil would emit notifyItemMoved; leanback GridLayoutManager
                            // corrupts its grid state on moves during pending updates (NPE).
                            // On order change do a full replace (remove-all + insert-all);
                            // content-only updates keep using the diff (no move ops there).
                            val previousIds = mainVerticalAdapter.currentList.map { it.first }
                            if (previousIds == updatedList.map { it.first }) {
                                mainVerticalAdapter.submitList(updatedList)
                            } else {
                                mainVerticalAdapter.submitList(null)
                                mainVerticalAdapter.submitList(updatedList)
                            }
                            watchNextAdapter.submitList(watchNextPrograms)
                        }
                    }
                }

                lifecycleScope.launch {
                    lifecycle.repeatOnLifecycle(Lifecycle.State.CREATED) {
                        model.installedApps.collectLatest {
                            if (it.isNotEmpty()) {
                                AppManager.updateFavoriteApps(this@MainActivity, it)
                            }
                        }
                    }
                }

                lifecycleScope.launch {
                    lifecycle.repeatOnLifecycle(Lifecycle.State.CREATED) {
                        combine(model.installedApps, model.hiddenApps) { apps, hidden ->
                            apps.filterNot { it.packageName in hidden }
                        }.collectLatest {
                            allAppsAdapter.submitList(it)
                        }
                    }
                }

                lifecycleScope.launch {
                    lifecycle.repeatOnLifecycle(Lifecycle.State.CREATED) {
                        combine(model.favoriteApps, model.hiddenApps) { favorites, hidden ->
                            favorites.filterNot { it in hidden }
                        }.collectLatest {
                            favoritesAdapter.submitList(
                                it.mapNotNull {
                                    runCatching {
                                        AppInfo.create(this@MainActivity, it)
                                    }.getOrNull()
                                } + listOf(
                                    FavoritesAdapter.createAddFavoriteEntry(this@MainActivity),
                                    FavoritesAdapter.createModifyChannelsEntry(this@MainActivity),
                                )
                            )
                        }
                    }
                }

                askForHomeRoleIfNeeded()
            }

            @Suppress("RestrictedApi")
            override fun onCreate(savedInstanceState: Bundle?) {
                super.onCreate(savedInstanceState)
                instance = this

                // Start global hotkey service
                startService(Intent(this, GlobalHotkeyService::class.java))

                applyBackground()
                mainVerticalGridView.setFocusScrollStrategy(androidx.leanback.widget.BaseGridView.FOCUS_SCROLL_ITEM)
                mainVerticalGridView.adapter = mainVerticalAdapter
                mainVerticalGridView.setSelectedPosition(0)
                mainVerticalGridView.viewTreeObserver.addOnGlobalFocusChangeListener(mainFocusScrollListener)
                mainVerticalGridView.addOnScrollListener(topBarScrollListener)
                mainVerticalGridView.upFocusFallback = findTopBarFocusable()
                setupTopBarScrim()
                topBarContainer.addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, oldBottom ->
                    if (bottom != oldBottom) {
                        applyMainGridTopInset()
                        // Keep row 0 snug under the new bar height when the list
                        // rests at the top (bar grew/shrank under the assistant,
                        // clock or search toggles).
                        if (isGridAtTop()) {
                            mainVerticalGridView.post { snapGridToTop() }
                        }
                    }
                }
                topBarContainer.post { applyMainGridTopInset() }

                lastAppCardSize = sharedPreferences.appCardSize
                lastFavoriteCardSize = sharedPreferences.favoriteCardSize
                lastWatchNextCardSize = sharedPreferences.watchNextCardSize
                lastAllAppsGrid = sharedPreferences.allAppsGrid
                lastAllAppsGridColumns = sharedPreferences.allAppsGridColumns
                lastCardCornerRadius = sharedPreferences.cardCornerRadius
                lastCardBackgroundType = sharedPreferences.cardBackgroundType
                lastCardBackgroundColor = sharedPreferences.cardBackgroundColor
                lastCardBackgroundGradientStart = sharedPreferences.cardBackgroundGradientStart
                lastCardBackgroundGradientEnd = sharedPreferences.cardBackgroundGradientEnd
                lastCardBackgroundGradientMode = sharedPreferences.cardBackgroundGradientMode
                lastCardBackgroundIconGradientMode = sharedPreferences.cardBackgroundIconGradientMode

                settingButton.setOnClickListener {
                    startActivity(Intent(this@MainActivity, LauncherSettingsActivity::class.java))
                }

                notificationContainer.setOnClickListener {
                    startActivity(Intent(this@MainActivity, SystemOptionsActivity::class.java))
                }

                setupVpnButton()

                val assistIntent = Intent(Intent.ACTION_ASSIST)
                assistAvailable = assistIntent.resolveActivity(packageManager) != null
                if (assistAvailable) {
                    setupAssistantButtons(assistIntent)
                }
                applyTopBarPreferences()

                favoritesAdapter.onFavoritesChangedCallback = {
                    sharedPreferences.favoriteApps = it
                }

                settingButton.requestFocus()

                permissionsGatedCallback.runAfterPermissionsCheck()

                onBackPressedDispatcher.addCallback(object: OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() {
                    }
                })

                // Redraw the wallpaper the moment the background settings write it,
                // so the change is visible behind the still-open settings panel.
                sharedPreferences.registerOnSharedPreferenceChangeListener(backgroundPrefsListener)

                lifecycleScope.launch {
                    repeatOnLifecycle(Lifecycle.State.STARTED) {
                        notificationViewModel.state.collect { state ->
                            when (state) {
                                ServiceConnectionState.Connected -> {}
                                is ServiceConnectionState.Notifications -> {
                                    if (state.notifications.isNotEmpty()) {
                                        notificationCountTextView.text = String.format(
                                            Locale.getDefault(),
                                            "%d",
                                            state.notifications.count()
                                        )
                                    } else {
                                        notificationCountTextView.text = ""
                                    }
                                }
                                ServiceConnectionState.Disconnected -> {
                                    notificationCountTextView.text = ""
                                }
                            }
                        }
                    }
                }
        handleHomePress()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val homeIntent = intent.hasCategory(Intent.CATEGORY_HOME)
        handleHomePress()
        // HOME while the desktop is already in the foreground is a deliberate
        // "go to the top" gesture. Pressing HOME also runs pause/resume on the
        // launcher, so visibility flags cannot tell the cases apart: only a
        // real stop (another app took over) happens when returning from an
        // app. Never stopped means the desktop was on screen the whole time.
        if (homeIntent && !activityStopped && !RecentsActivity.isShowing()) {
            showTopBar()
            @Suppress("RestrictedApi")
            mainVerticalGridView.post {
                val lm = mainVerticalGridView.layoutManager as?
                    androidx.leanback.widget.GridLayoutManager
                // Plain scrollToPosition gets re-anchored by leanback's focus
                // machinery to the old focused row on long distances; scrollBy
                // is immediate and loops until row 0 is really attached.
                if (lm != null) {
                    var guard = 0
                    while (lm.findViewByPosition(0) == null && guard < 24) {
                        mainVerticalGridView.scrollBy(0, -mainVerticalGridView.height.coerceAtLeast(1))
                        guard++
                    }
                }
                mainVerticalGridView.setSelectedPosition(0)
                snapGridToTop()
                mainVerticalGridView.post {
                    snapGridToTop()
                    (lm?.findViewByPosition(0) ?: mainVerticalGridView).requestFocus()
                    mainVerticalGridView.post { snapGridToTop() }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Re-apply the background: while stopped (e.g. a photo picker was on top),
        // view changes are not rendered; re-setting it here forces a fresh frame
        // as soon as the launcher becomes visible behind the settings panel again.
        applyBackground()
        dynamicBackground.resume()
        if (NotificationUtils.notificationPermissionGranted(this)) {
            notificationViewModel.bindService(this)
        }
    }

    private val backgroundPrefsListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                BACKGROUND_TYPE_KEY, BACKGROUND_IMAGE_URI_KEY -> applyBackground()
                BACKGROUND_BLUR_KEY -> dynamicBackground.setBlurRadius(
                    sharedPreferences.backgroundBlur.toFloat()
                )
                APP_CARD_SIZE_KEY, FAVORITE_CARD_SIZE_KEY, WATCH_NEXT_CARD_SIZE_KEY,
                CARD_CORNER_RADIUS_KEY ->
                    scheduleLiveCardResize()
                SHOW_ASSISTANT_BUTTONS_KEY, SHOW_VPN_BUTTON_KEY, SHOW_CLOCK_KEY,
                CLOCK_TEXT_SIZE_KEY ->
                    applyTopBarPreferences()
                TOP_BAR_AUTO_HIDE_KEY ->
                    applyTopBarAutoHide()
            }
        }

    private val cardSizeHandler by lazy { android.os.Handler(mainLooper) }
    private var cardSizeResizeRunnable: Runnable? = null

    // Applies card size changes while the settings slider is still open,
    // coalescing the rapid slider updates into one rebinding.
    private fun scheduleLiveCardResize() {
        cardSizeResizeRunnable?.let { cardSizeHandler.removeCallbacks(it) }
        cardSizeResizeRunnable = Runnable {
            lastAppCardSize = sharedPreferences.appCardSize
            lastFavoriteCardSize = sharedPreferences.favoriteCardSize
            lastWatchNextCardSize = sharedPreferences.watchNextCardSize
            lastCardCornerRadius = sharedPreferences.cardCornerRadius
            refreshAllRows()
        }.also { cardSizeHandler.postDelayed(it, 120) }
    }

    private var activityStopped = true

    override fun onResume() {
        super.onResume()
        isMainActivityVisible = true
        activityStopped = false
        lastResumedTime = System.currentTimeMillis()
        applyBackground()
        applyBackgroundPerfPreferences()
        applyTopBarPreferences()
        showTopBar()
        checkCardSizeChanges()
        dismissStaleModalTasks()
        mainVerticalGridView.post {
            mainVerticalGridView.upFocusFallback = findTopBarFocusable()
        }
    }

    private fun dismissStaleModalTasks() {
        // The modal panels run as separate tasks of this app. Whenever home becomes
        // interactive again (HOME press, recents to home, restart after a crash),
        // drop them: no stale panel is restored over other apps, it cannot skew focus,
        // and the next settings entry starts fresh at the root screen.
        val activityManager = getSystemService(ActivityManager::class.java)
        activityManager.appTasks.forEach { task ->
            val top = task.taskInfo?.topActivity ?: return@forEach
            if (top.packageName == packageName &&
                top.className != MainActivity::class.java.name
            ) {
                runCatching { task.finishAndRemoveTask() }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyBackground()
        checkCardSizeChanges()
        mainVerticalGridView.post {
            applyMainGridTopInset()
            mainVerticalGridView.requestLayout()
            refreshAllRows()
            val currentFocus = currentFocus
            if (currentFocus == null || !isMainDescendant(currentFocus)) {
                mainVerticalGridView.setSelectedPosition(0)
            }
        }
    }


    override fun onStop() {
        super.onStop()
        activityStopped = true
        dynamicBackground.pause()
    }

    override fun onPause() {
        super.onPause()
        isMainActivityVisible = false
    }

    private fun checkCardSizeChanges() {
        val currentAppSize = sharedPreferences.appCardSize
        val currentFavoriteSize = sharedPreferences.favoriteCardSize
        val currentWatchNextSize = sharedPreferences.watchNextCardSize
        val currentAllAppsGrid = sharedPreferences.allAppsGrid
        val currentAllAppsGridColumns = sharedPreferences.allAppsGridColumns
        val currentCardCornerRadius = sharedPreferences.cardCornerRadius
        val currentCardBackgroundType = sharedPreferences.cardBackgroundType
        val currentCardBackgroundColor = sharedPreferences.cardBackgroundColor
        val currentCardBackgroundGradientStart = sharedPreferences.cardBackgroundGradientStart
        val currentCardBackgroundGradientEnd = sharedPreferences.cardBackgroundGradientEnd
        val currentCardBackgroundGradientMode = sharedPreferences.cardBackgroundGradientMode
        val currentCardBackgroundIconGradientMode = sharedPreferences.cardBackgroundIconGradientMode

        if (currentAppSize != lastAppCardSize ||
            currentFavoriteSize != lastFavoriteCardSize ||
            currentWatchNextSize != lastWatchNextCardSize ||
            currentAllAppsGrid != lastAllAppsGrid ||
            currentAllAppsGridColumns != lastAllAppsGridColumns ||
            currentCardCornerRadius != lastCardCornerRadius ||
            currentCardBackgroundType != lastCardBackgroundType ||
            currentCardBackgroundColor != lastCardBackgroundColor ||
            currentCardBackgroundGradientStart != lastCardBackgroundGradientStart ||
            currentCardBackgroundGradientEnd != lastCardBackgroundGradientEnd ||
            currentCardBackgroundGradientMode != lastCardBackgroundGradientMode ||
            currentCardBackgroundIconGradientMode != lastCardBackgroundIconGradientMode) {
            
            lastAppCardSize = currentAppSize
            lastFavoriteCardSize = currentFavoriteSize
            lastWatchNextCardSize = currentWatchNextSize
            lastAllAppsGrid = currentAllAppsGrid
            lastAllAppsGridColumns = currentAllAppsGridColumns
            lastCardCornerRadius = currentCardCornerRadius
            lastCardBackgroundType = currentCardBackgroundType
            lastCardBackgroundColor = currentCardBackgroundColor
            lastCardBackgroundGradientStart = currentCardBackgroundGradientStart
            lastCardBackgroundGradientEnd = currentCardBackgroundGradientEnd
            lastCardBackgroundGradientMode = currentCardBackgroundGradientMode
            lastCardBackgroundIconGradientMode = currentCardBackgroundIconGradientMode
            
            android.os.Handler(mainLooper).post {
                refreshAllRows()
            }
        }
    }

    private fun refreshAllRows() {
        allAppsAdapter.notifyDataSetChanged()
        favoritesAdapter.notifyDataSetChanged()
        watchNextAdapter.notifyDataSetChanged()
        previewChannelAdapters.values.forEach { it.notifyDataSetChanged() }
        mainVerticalAdapter.notifyDataSetChanged()
    }

    private fun setupVpnButton() {
        vpnButton.setOnClickListener { launchVpn() }
        // Long press: forget the remembered VPN client and re-open the chooser.
        vpnButton.setOnLongClickListener {
            sharedPreferences.edit().remove(VPN_PACKAGE_KEY).apply()
            if (vpnClientPackages().size > 1) {
                showVpnChooser(vpnClientPackages())
            } else {
                launchVpn()
            }
            true
        }

        // Idempotent: never stack duplicate callbacks if called again.
        vpnNetworkCallback?.let {
            runCatching { connectivityManager.unregisterNetworkCallback(it) }
            vpnNetworkCallback = null
        }
        vpnDefaultCallback?.let {
            runCatching { connectivityManager.unregisterNetworkCallback(it) }
            vpnDefaultCallback = null
        }

        // NetworkRequest.Builder() starts with default capabilities that include
        // NOT_VPN; a VPN network never has that capability. Remove it, otherwise a
        // TRANSPORT_VPN listener can never match any VPN (AOSP: ConnectivityService
        // createVpnRequest/createDefaultNetworkCapabilitiesForUidRangeSet do the same).
        vpnNetworkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                updateVpnIcon()
            }

            override fun onLost(network: Network) {
                updateVpnIcon()
            }
        }.also {
            val request = NetworkRequest.Builder()
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                .build()
            connectivityManager.registerNetworkCallback(request, it, Handler(mainLooper))
        }

        // Backup signal: a VPN that takes over the default route (any client) also
        // changes the default network; documented to fire for virtual networks.
        vpnDefaultCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                updateVpnIcon()
            }

            override fun onLost(network: Network) {
                updateVpnIcon()
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                updateVpnIcon()
            }
        }.also {
            connectivityManager.registerDefaultNetworkCallback(it, Handler(mainLooper))
        }
        updateVpnIcon()
    }

    private fun isVpnActive(): Boolean {
        return connectivityManager.allNetworks.any { network ->
            connectivityManager.getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        }
    }

    private fun updateVpnIcon() {
        vpnButton.imageTintList = if (isVpnActive()) {
            AppCompatResources.getColorStateList(this, R.color.search_button_icon_color)
        } else {
            ColorStateList.valueOf(Color.argb(0xB0, 128, 128, 128))
        }
    }

    private fun vpnClientPackages(): List<String> {
        return packageManager.queryIntentServices(
            Intent("android.net.VpnService"),
            0
        ).mapNotNull { it.serviceInfo?.packageName }.distinct()
    }

    private fun launchVpn() {
        val packages = vpnClientPackages()
        when {
            packages.isEmpty() -> {
                Toast.makeText(this, R.string.vpn_no_clients, Toast.LENGTH_SHORT).show()
            }
            packages.size == 1 -> launchVpnPackage(packages.first())
            else -> {
                val saved = sharedPreferences.getString(VPN_PACKAGE_KEY, null)?.takeIf { it in packages }
                if (saved == null) {
                    sharedPreferences.edit().remove(VPN_PACKAGE_KEY).apply()
                    showVpnChooser(packages)
                } else {
                    launchVpnPackage(saved)
                }
            }
        }
    }

    private fun showVpnChooser(packages: List<String>) {
        val labels = packages.map { pkg ->
            runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)) }
                .getOrDefault(pkg as CharSequence)
        }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.vpn)
            .setItems(labels) { _, which ->
                val pkg = packages[which]
                sharedPreferences.edit().putString(VPN_PACKAGE_KEY, pkg).apply()
                launchVpnPackage(pkg)
            }
            .show()
    }

    private fun launchVpnPackage(pkg: String) {
        val intent = packageManager.getLaunchIntentForPackage(pkg)
        if (intent != null) {
            startActivity(intent)
        } else {
            Toast.makeText(this, R.string.vpn_no_clients, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(backgroundPrefsListener)
        cardSizeResizeRunnable?.let { cardSizeHandler.removeCallbacks(it) }
        dynamicBackground.release()
        if (mainVerticalGridView.viewTreeObserver.isAlive) {
            mainVerticalGridView.viewTreeObserver.removeOnGlobalFocusChangeListener(mainFocusScrollListener)
        }
        super.onDestroy()

        vpnNetworkCallback?.let {
            runCatching { connectivityManager.unregisterNetworkCallback(it) }
            vpnNetworkCallback = null
        }
        vpnDefaultCallback?.let {
            runCatching { connectivityManager.unregisterNetworkCallback(it) }
            vpnDefaultCallback = null
        }

        if (NotificationUtils.notificationPermissionGranted(this)) {
            notificationViewModel.unbindService(this)
        }
    }

    private fun isMainDescendant(target: View): Boolean {
        var parent = target.parent
        while (parent != null) {
            if (parent === mainVerticalGridView) return true
            parent = parent.parent
        }
        return false
    }

    private fun isTopBarDescendant(target: View): Boolean {
        var parent = target.parent
        while (parent != null) {
            if (parent === topBarContainer) return true
            parent = parent.parent
        }
        return false
    }

    private fun setupTopBarScrim() {
        val background = getColor(R.color.default_background)
        val topColor = (background and 0x00FFFFFF) or (0xF2 shl 24)
        val bottomColor = background and 0x00FFFFFF
        topBarContainer.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(topColor, bottomColor)
        )
    }

    private fun applyMainGridTopInset() {
        val inset = topBarContainer.height
        if (mainVerticalGridView.paddingTop != inset) {
            mainVerticalGridView.setPadding(
                mainVerticalGridView.paddingLeft,
                inset,
                mainVerticalGridView.paddingRight,
                mainVerticalGridView.paddingBottom
            )
        }
    }

    private fun findTopBarFocusable(view: View = topBarContainer): View? {
        val preferred = listOf(
            settingButton,
            vpnButton,
            notificationContainer,
            keyboardAssistantButton,
            voiceAssistantButton,
            assistantHintImageView,
        ).firstOrNull { isTopBarCandidate(it) }
        if (preferred != null) return preferred

        return findTopBarFocusableDescendant(view)
    }

    private fun findTopBarFocusableDescendant(view: View): View? {
        if (!isTopBarCandidate(view)) return null
        if (view.isFocusable || view.isClickable) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                val child = view.getChildAt(index)
                val candidate = findTopBarFocusableDescendant(child)
                if (candidate != null) return candidate
            }
        }
        return null
    }

    private fun isTopBarCandidate(view: View): Boolean =
        view.isEnabled && view.visibility == View.VISIBLE

    private fun expandAssistantPanel() {
        assistantTransition.removeTarget(assistantHintImageView)
        assistantTransition.addTarget(assistantButtonsContainer)
        TransitionManager.beginDelayedTransition(topBarContainer, assistantTransition)
        assistantHintImageView.isVisible = false
        assistantButtonsContainer.isVisible = true

        voiceAssistantButton.post {
            voiceAssistantButton.requestFocus()
        }
    }

    private fun collapseAssistantPanel() {
        assistantTransition.removeTarget(assistantButtonsContainer)
        assistantTransition.addTarget(assistantHintImageView)
        TransitionManager.beginDelayedTransition(topBarContainer, assistantTransition)
        assistantButtonsContainer.isVisible = false
        assistantHintImageView.isVisible = true
    }

    private fun setupAssistantButtons(assistIntent: Intent) {
        voiceAssistantButton.setOnClickListener {
            startActivity(assistIntent)
        }

        val keyboardAssistantIntent = Intent(assistIntent).apply {
            putExtra(Intent.EXTRA_ASSIST_INPUT_HINT_KEYBOARD, true)
        }
        keyboardAssistantButton.setOnClickListener {
            startActivity(keyboardAssistantIntent)
        }

        assistantHintImageView.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                expandAssistantPanel()
            }
        }

        val assistantButtonFocusListener = View.OnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                if (!keyboardAssistantButton.hasFocus() && !voiceAssistantButton.hasFocus()) {
                    collapseAssistantPanel()
                }
            }
        }

        keyboardAssistantButton.onFocusChangeListener = assistantButtonFocusListener
        voiceAssistantButton.onFocusChangeListener = assistantButtonFocusListener
    }

    private fun askForHomeRoleIfNeeded() {
        if (!AppManager.isSystemApp(this) && roleManager.roleCanBeRequested(RoleManager.ROLE_HOME)
            && !sharedPreferences.homeRoleRequestDialogDismissed
        ) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.home_role_request_dialog_title)
                .setMessage(R.string.home_role_request_dialog_message)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    homeRoleActivityRequestLauncher.launch(
                        roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
                    )
                }
                .setNeutralButton(R.string.home_role_request_dialog_neutral) { _, _ ->
                    // Do nothing
                }
                .setNegativeButton(R.string.home_role_request_dialog_negative) { _, _ ->
                    sharedPreferences.homeRoleRequestDialogDismissed = true
                }
                .show().also {
                    it.getButton(DialogInterface.BUTTON_NEUTRAL).requestFocus()
                }
        }
    }

    private fun applyBackground() {
        val backgroundType = sharedPreferences.backgroundType
        val decorView = window.decorView
        applyPillTint(null)
        
        when (backgroundType) {
            0, 1 -> {
                dynamicBackground.hide()
                decorView.setBackgroundColor(getColor(R.color.default_background))
            }
            3 -> {
                decorView.setBackgroundColor(getColor(R.color.default_background))
                dynamicBackground.show()
                applyPillTint(null)
                currentFocus?.let { updateDynamicBackground(it) }
            }
            2 -> {
                dynamicBackground.hide()
                val uriString = sharedPreferences.backgroundImageUri
                if (!uriString.isNullOrEmpty()) {
                    try {
                        val file = java.io.File(uriString)
                        if (file.exists()) {
                            val options = android.graphics.BitmapFactory.Options().apply {
                                inJustDecodeBounds = true
                            }
                            BitmapFactory.decodeFile(file.absolutePath, options)
                            
                            val targetWidth = 1920
                            val targetHeight = 1080
                            val sampleSize = calculateInSampleSize(options, targetWidth, targetHeight)
                            
                            val decodeOptions = android.graphics.BitmapFactory.Options().apply {
                                inSampleSize = sampleSize
                            }
                            val bitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
                            
                            if (bitmap != null) {
                                val drawable = BitmapDrawable(resources, bitmap)
                                drawable.gravity = android.view.Gravity.FILL
                                decorView.background = drawable
                                applyPillTint(bitmap)
                            } else {
                                decorView.setBackgroundColor(getColor(R.color.default_background))
                            }
                        } else {
                            decorView.setBackgroundColor(getColor(R.color.default_background))
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        decorView.setBackgroundColor(getColor(R.color.default_background))
                    }
                } else {
                    decorView.setBackgroundColor(getColor(R.color.default_background))
                }
            }
        }
    }

    private val topBarButtonsGroup by lazy { findViewById<View>(R.id.topBarButtonsGroup)!! }

    private var pillTintGeneration = 0
    private var pillTintAnimator: android.animation.ValueAnimator? = null
    private var lastPillColor: Int? = null

    // Material-You-style pill background: take the wallpaper's hue from a vivid
    // swatch and build a dark, moderately saturated tone with it (fixed value in
    // HSV), so the pill reads as part of the wallpaper's palette yet stays dark
    // and legible over any background.
    private fun applyPillTint(wallpaper: android.graphics.Bitmap?) {
        val generation = ++pillTintGeneration
        if (wallpaper == null) {
            pillTintAnimator?.cancel()
            lastPillColor = null
            assistantButtonsContainer.backgroundTintList = null
            topBarButtonsGroup.backgroundTintList = null
            return
        }
        Thread {
            val default = getColor(R.color.toggleButtonBackground)
            val palette = Palette.from(wallpaper).generate()
            val seed = palette.mutedSwatch?.rgb
                ?: palette.vibrantSwatch?.rgb
                ?: palette.darkMutedSwatch?.rgb
                ?: palette.dominantSwatch?.rgb
                ?: default
            val hsv = FloatArray(3)
            Color.colorToHSV(seed, hsv)
            hsv[1] = hsv[1].coerceIn(0.3f, 0.6f)
            hsv[2] = 0.17f
            val pillColor = Color.HSVToColor(hsv)
            runOnUiThread {
                if (generation != pillTintGeneration) {
                    return@runOnUiThread
                }
                animatePillTint(pillColor)
            }
        }.start()
    }

    // Pill color crossfades with the wallpaper crossfade instead of snapping.
    private fun animatePillTint(color: Int) {
        pillTintAnimator?.cancel()
        val from = lastPillColor ?: color
        lastPillColor = color
        pillTintAnimator = ObjectAnimator.ofArgb(from, color).apply {
            duration = 1400L
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                val tint = ColorStateList.valueOf(animator.animatedValue as Int)
                assistantButtonsContainer.backgroundTintList = tint
                topBarButtonsGroup.backgroundTintList = tint
            }
            start()
        }
    }

    private fun calculateInSampleSize(options: android.graphics.BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    /**
     * Plain HOME presses land while MainActivity is the home task itself; the
     * only thing to do is forwarding to a HOME replacement picked in settings.
     * Recents requests never come here - the translucent RecentsActivity
     * receives them so the app below is not paused.
     */
    private fun handleHomePress() {
        if (intent?.hasCategory(Intent.CATEGORY_HOME) == true &&
            sharedPreferences.externalLauncherPkg != null
        ) {
            launchExternalLauncherIfSet()
        }
    }

    private fun launchExternalLauncherIfSet(): Boolean {
        val pkg = sharedPreferences.externalLauncherPkg ?: return false
        val intent = getHomeIntentForPackage(pkg) ?: return false
        // Reorder, don't recreate: a fresh instance always opens the default
        // tab, while the live one keeps where the user was.
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        return try {
            startActivity(intent)
            overridePendingTransition(0, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * TV home replacements (e.g. Google TV Launcher) expose MAIN+HOME, not
     * LAUNCHER, so getLaunchIntentForPackage() misses them. Resolve HOME
     * first, fall back to the regular launcher lookup.
     */
    private fun getHomeIntentForPackage(pkg: String): Intent? {
        val homeQuery = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            setPackage(pkg)
        }
        packageManager.resolveActivity(homeQuery, PackageManager.MATCH_DEFAULT_ONLY)?.let { ri ->
            return Intent().setComponent(ComponentName(pkg, ri.activityInfo.name))
        }
        return packageManager.getLaunchIntentForPackage(pkg)
    }
}

class MainVerticalGridView @JvmOverloads constructor(
    context: android.content.Context,
    attrs: android.util.AttributeSet? = null,
    defStyleAttr: Int = 0
) : androidx.leanback.widget.VerticalGridView(context, attrs, defStyleAttr) {
    private val mainFocusSearchUp = 0x00000004
    private val mainFocusSearchDown = 0x00000008

    var upFocusFallback: View? = null

    override fun requestChildFocus(child: View, focused: View) {
        val inGrid = isInMainGrid(focused)
        // Must be sampled before stopScroll(): while a leanback smooth-scroll pass is running
        // (state SETTLING) the rule must not scrollBy from inside it.
        val busy = inLayoutPass || scrollState != RecyclerView.SCROLL_STATE_IDLE
        if (inGrid) stopScroll()
        super.requestChildFocus(child, focused)
        if (inGrid) {
            stopScroll()
            if (busy) {
                post {
                    findFocus()?.let { if (isInMainGrid(it)) ensureFocusedVisible(it) }
                }
            } else {
                ensureFocusedVisible(focused)
            }
        }
    }

    private var inLayoutPass = false

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        inLayoutPass = true
        try {
            super.onLayout(changed, l, t, r, b)
        } finally {
            inLayoutPass = false
        }
    }

    private fun ensureFocusedVisible(focused: View) {
        if (width == 0 || height == 0) return
        val rect = Rect()
        focused.getFocusedRect(rect)
        (focused.findViewById<View>(R.id.app_name) ?: focused.findViewById(R.id.title))?.let { name ->
            rect.bottom += name.height + (8f * resources.displayMetrics.density).toInt()
        }
        val sx = focused.scaleX
        val sy = focused.scaleY
        if (sx != 1f || sy != 1f) {
            val px = focused.pivotX
            val py = focused.pivotY
            rect.left = (px + (rect.left - px) * sx).toInt()
            rect.right = (px + (rect.right - px) * sx).toInt()
            rect.top = (py + (rect.top - py) * sy).toInt()
            rect.bottom = (py + (rect.bottom - py) * sy).toInt()
        }
        offsetDescendantRectToMyCoords(focused, rect)
        val delta = when {
            rect.top < paddingTop -> rect.top - paddingTop
            rect.bottom > height - paddingBottom -> rect.bottom - (height - paddingBottom)
            else -> 0
        }
        if (delta != 0) {
            scrollBy(0, delta)
        }
    }

    override fun focusSearch(focused: View?, direction: Int): View? {
        val target = focused ?: return null
        if (!isInMainGrid(target)) {
            return super.focusSearch(target, direction)
        }

        return when (direction) {
            mainFocusSearchUp -> {
                verticalFocusSearch(target, direction)
                    ?: super.focusSearch(target, direction).takeUnless { it === target }
                    ?: if (isFirstMainRow(target)) mainFocusFallback(target, direction) else null
            }
            mainFocusSearchDown -> verticalFocusSearch(target, direction)
            else -> super.focusSearch(target, direction)
        }
    }

    private fun isFirstMainRow(target: View): Boolean {
        val row = findMainGridChild(target) ?: return false
        val lm = layoutManager as? androidx.leanback.widget.GridLayoutManager
        // Adapter position 0, not the first attached child: when scrolled deep,
        // child index 0 is just the top visible row and must not escape to the
        // top bar.
        return if (lm != null) lm.getPosition(row) == 0 else indexOfChild(row) == 0
    }

    private fun mainFocusFallback(target: View, direction: Int): View? {
        val fallback = upFocusFallback?.takeIf {
            it.visibility == View.VISIBLE && (it.isFocusable || it.isClickable)
        }
        return fallback ?: parent?.focusSearch(target, direction)
    }

    private fun isInMainGrid(view: View): Boolean {
        var current = view.parent
        while (current != null) {
            if (current === this) return true
            current = current.parent
        }
        return false
    }

    private fun verticalFocusSearch(focused: View, direction: Int): View? {
        val focusedRect = Rect()
        focused.getFocusedRect(focusedRect)
        this.offsetDescendantRectToMyCoords(focused, focusedRect)
        if (focusedRect.bottom <= focusedRect.top || focusedRect.right <= focusedRect.left) return null

        val row = findMainGridChild(focused) ?: return super.focusSearch(focused, direction)
        val rowIndexOf = indexOfMainGridChild(row) ?: return super.focusSearch(focused, direction)

        val sameRowCandidates = mutableListOf<View>()
        collectCardCandidates(row, focused, sameRowCandidates)
        val sameRow = chooseBestVerticalCandidate(sameRowCandidates, focusedRect, direction, allowRelaxed = false)
        if (sameRow != null) return sameRow

        val nextIndex = if (direction == mainFocusSearchUp) rowIndexOf - 1 else rowIndexOf + 1
        if (nextIndex >= 0 && nextIndex < childCount) {
            val neighbors = mutableListOf<View>()
            val neighbor = getChildAt(nextIndex)
            if (neighbor != null) {
                collectCardCandidates(neighbor, focused, neighbors)
            }
            val rowCandidate = chooseBestVerticalCandidate(neighbors, focusedRect, direction, allowRelaxed = true)
            if (rowCandidate != null) return rowCandidate
        }

        return null
    }

    private fun findMainGridChild(view: View): View? {
        var current = view
        while (current.parent != null) {
            val parent = current.parent ?: return null
            if (parent === this) return current
            current = parent as android.view.View
        }
        return null
    }

    private fun indexOfMainGridChild(child: View): Int? {
        var index = 0
        while (index < childCount) {
            if (getChildAt(index) === child) return index
            index++
        }
        return null
    }

    private fun collectCardCandidates(view: View, focused: View, result: MutableList<View>) {
        if (!view.isVisible || view.alpha == 0f) return
        if (view === focused) return

        if (view is Card) {
            result.add(view)
            return
        }

        if (view is ViewGroup) {
            var childIndex = 0
            while (childIndex < view.childCount) {
                val child = view.getChildAt(childIndex)
                if (child != null) {
                    collectCardCandidates(child, focused, result)
                }
                childIndex++
            }
        }
    }

    private fun chooseBestVerticalCandidate(
        candidates: MutableList<View>,
        focusedRect: Rect,
        direction: Int,
        allowRelaxed: Boolean
    ): View? {
        var best: View? = null
        var bestScore: Long = 0L

        for (candidate in candidates) {
            val rect = Rect()
            candidate.getFocusedRect(rect)
            this.offsetDescendantRectToMyCoords(candidate, rect)
            if (rect.bottom <= rect.top || rect.right <= rect.left) continue

            var valid = false
            var gap = 0
            if (direction == mainFocusSearchUp) {
                if (rect.bottom <= focusedRect.top) {
                    valid = true
                    gap = focusedRect.top - rect.bottom
                }
            } else if (direction == mainFocusSearchDown) {
                if (rect.top >= focusedRect.bottom) {
                    valid = true
                    gap = rect.top - focusedRect.bottom
                }
            }
            if (!valid) continue

            val focusedCenterX = (focusedRect.left + focusedRect.right) / 2
            val candidateCenterX = (rect.left + rect.right) / 2
            val centerDelta = focusedCenterX - candidateCenterX
            val centerDistance = if (centerDelta >= 0) centerDelta else -centerDelta
            val score = gap.toLong() * 100_000L + centerDistance

            if (best == null || score < bestScore) {
                bestScore = score
                best = candidate
            }
        }

        if (best != null) return best
        if (!allowRelaxed) return null

        val focusedCenterY = (focusedRect.top + focusedRect.bottom) / 2
        val focusedCenterX = (focusedRect.left + focusedRect.right) / 2
        var relaxedBest: View? = null
        var relaxedBestDistance = Int.MAX_VALUE

        for (candidate in candidates) {
            val rect = Rect()
            candidate.getFocusedRect(rect)
            this.offsetDescendantRectToMyCoords(candidate, rect)
            if (rect.bottom <= rect.top || rect.right <= rect.left) continue

            val candidateCenterY = (rect.top + rect.bottom) / 2
            if (direction == mainFocusSearchUp) {
                if (candidateCenterY > focusedCenterY) continue
            } else if (direction == mainFocusSearchDown) {
                if (candidateCenterY < focusedCenterY) continue
            } else {
                continue
            }

            val candidateCenterX = (rect.left + rect.right) / 2
            val centerDelta = focusedCenterX - candidateCenterX
            val centerDistance = if (centerDelta >= 0) centerDelta else -centerDelta
            if (centerDistance < relaxedBestDistance) {
                relaxedBestDistance = centerDistance
                relaxedBest = candidate
            }
        }

        return relaxedBest
    }
}
