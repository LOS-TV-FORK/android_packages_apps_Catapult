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
import androidx.preference.PreferenceManager
import androidx.appcompat.content.res.AppCompatResources
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.lineageos.tv.launcher.adapter.AllAppsAdapter
import org.lineageos.tv.launcher.adapter.CollectionAdapter
import org.lineageos.tv.launcher.adapter.FavoritesAdapter
import org.lineageos.tv.launcher.adapter.MainVerticalAdapter
import org.lineageos.tv.launcher.adapter.PreviewProgramsAdapter
import org.lineageos.tv.launcher.adapter.WatchNextAdapter
import org.lineageos.tv.launcher.ext.favoriteApps
import org.lineageos.tv.launcher.ext.homeRoleRequestDialogDismissed
import org.lineageos.tv.launcher.ext.roleCanBeRequested
import org.lineageos.tv.launcher.ext.COLLECTIONS_KEY
import org.lineageos.tv.launcher.ext.collections
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
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationSet
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
import org.lineageos.tv.launcher.view.MainRowItemView

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
        // Lens center follows the focused card in the background container's
        // coordinate space; the flow radius is a bit wider than the card.
        val cardLoc = IntArray(2).also { card.getLocationOnScreen(it) }
        val contLoc = IntArray(2).also { dynamicBackgroundContainer.getLocationOnScreen(it) }
        val lensRadius = kotlin.math.hypot(
            card.width.toDouble(), card.height.toDouble()
        ).toFloat() * 1.8f
        dynamicBackground.setLensCenter(
            cardLoc[0] + card.width / 2f - contLoc[0],
            cardLoc[1] + card.height / 2f - contLoc[1],
            lensRadius
        )
    }

    // Top bar visibility is alpha-only: it must not move the grid.
    private val mainFocusScrollListener = ViewTreeObserver.OnGlobalFocusChangeListener { _, newFocus ->
        if (newFocus != null && isTopBarDescendant(newFocus)) {
            if (!mainVerticalGridView.navScrollActive) {
                syncTopBarVisibility()
            }
        }
        if (newFocus != null && isMainDescendant(newFocus)) {
            updateDynamicBackground(newFocus)
            if (!mainVerticalGridView.navScrollActive) {
                syncTopBarVisibility()
            }
            if (assistantButtonsContainer.isVisible) {
                collapseAssistantPanel()
            }
        }
    }

    // Hide the bar only once the top-row card's top has scrolled above the top
    // edge of the screen; while its top is still on screen the bar stays shown.
    private fun isGridAtTop(): Boolean {
        val card = mainVerticalGridView.matrixFirstCard() ?: return false
        val rect = Rect(0, 0, 1, 1)
        mainVerticalGridView.offsetDescendantRectToMyCoords(card, rect)
        return rect.top >= 0
    }

    // Focus is the matrix cell (0,0): its card view exists once row 0 is
    // bound and the matrix is built, so retry until the cell resolves.
    private fun matrixFirstCellFocus(retries: Int = 25) {
        val target = mainVerticalGridView.matrixFirstCard()
        if (target != null) {
            target.requestFocus()
            return
        }
        if (retries > 0) {
            mainVerticalGridView.postDelayed({ matrixFirstCellFocus(retries - 1) }, 200)
        }
    }

    private var topBarVisible = true
    private var assistantPanelExpanding = false

    private fun setTopBarVisible(visible: Boolean) {
        topBarContainer.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        if (topBarVisible == visible) return
        topBarVisible = visible
        topBarContainer.animate()
            .alpha(if (visible) 1f else 0f)
            .setDuration(150)
            .start()
    }

    private fun syncTopBarVisibility() {
        val focusInBar = currentFocus?.let { isTopBarDescendant(it) } == true
        val visible = focusInBar || !sharedPreferences.topBarAutoHide || isGridAtTop()
        setTopBarVisible(visible)
    }

    private val topBarScrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            if (newState == RecyclerView.SCROLL_STATE_IDLE && !mainVerticalGridView.navScrollActive) {
                syncTopBarVisibility()
            }
        }
    }

    private fun applyTopBarAutoHide() {
        syncTopBarVisibility()
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

        mainVerticalGridView.upEscapeAction = {
            setTopBarVisible(true)
            val items = ArrayList<View>()
            collectTopBarFocusables(topBarContainer, items)
            val focused = currentFocus
            val nearest = if (focused == null || items.isEmpty()) {
                findTopBarFocusable()
            } else {
                val focusedLoc = IntArray(2)
                focused.getLocationOnScreen(focusedLoc)
                val focusedX = focusedLoc[0] + focused.width / 2
                items.minByOrNull { item ->
                    val loc = IntArray(2)
                    item.getLocationOnScreen(loc)
                    kotlin.math.abs(loc[0] + item.width / 2 - focusedX)
                }
            }
            if (nearest === assistantHintImageView) {
                expandAssistantPanel()
                voiceAssistantButton
            } else {
                nearest
            }
        }
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
    private val collectionAdapters = mutableMapOf<Long, CollectionAdapter>()
    private var currentRows: List<Pair<Long, MainRowItem>>? = null

    private val sharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(this)
    }

    private val permissionsGatedCallback = PermissionsGatedCallback(this) {
                lifecycleScope.launch {
                    lifecycle.repeatOnLifecycle(Lifecycle.State.CREATED) {
                        combine(
                            model.watchNextPrograms,
                            model.channelsToPrograms,
                            model.collections
                        ) { watchNextPrograms, channels, collections ->
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
                                    val collection = collections.find {
                                        it.id == channel.first.id
                                    }
                                    if (collection != null) {
                                        channel.first.id to MainRowItem(
                                            channel.first.title,
                                            collectionAdapters.getOrPut(collection.id) {
                                                CollectionAdapter().apply {
                                                    onOrderChanged = { order ->
                                                        persistCollectionOrder(collection.id, order)
                                                    }
                                                }.also { mainVerticalGridView.registerMatrixModel(it) }
                                            }.apply {
                                                gridMode = collection.grid
                                                gridColumns = collection.gridColumns
                                                cardSize = collection.cardSize
                                                cornerRadius = collection.cornerRadius
                                            },
                                            collection.settingsKey
                                        )
                                    } else {
                                        channel.first.id to MainRowItem(
                                            channel.first.title,
                                            when (channel.first.id) {
                                                InternalChannel.FAVORITE_APPS.id -> favoritesAdapter
                                                InternalChannel.WATCH_NEXT.id -> watchNextAdapter
                                                InternalChannel.ALL_APPS.id -> allAppsAdapter
                                                else -> previewChannelAdapters.getOrPut(
                                                    channel.first.id
                                                ) {
                                                    PreviewProgramsAdapter(channel.first.title)
                                                        .also { mainVerticalGridView.registerMatrixModel(it) }
                                                }.apply {
                                                    channel.second?.let { previewPrograms ->
                                                        submitList(previewPrograms)
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                            } to watchNextPrograms
                        }.collectLatest { (updatedList, watchNextPrograms) ->
                            currentRows = updatedList
                            // DiffUtil would emit notifyItemMoved; leanback GridLayoutManager
                            // corrupts its grid state on moves during pending updates (NPE).
                            // Full replace (remove-all + insert-all) also blanks the whole
                            // grid for a frame, so use it only when existing rows actually
                            // swap places. Growing/shrinking row sets (providers publishing
                            // programs at startup) go through the diff and never flicker.
                            val newIds = updatedList.map { it.first }
                            val prevIds = mainVerticalAdapter.currentList.map { it.first }
                            val commonInPrev = prevIds.filter { it in newIds }
                            val commonInNew = newIds.filter { it in prevIds }
                            if (commonInPrev == commonInNew) {
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
                        combine(model.collections, model.installedApps) {
                            collections, installedApps ->
                            collections to installedApps.map { it.packageName }.toSet()
                        }.collectLatest { (collections, installedPackages) ->
                            collectionAdapters.keys.retainAll(collections.map { it.id }.toSet())
                            collections.forEach { collection ->
                                val items = collection.packages
                                    .filter { it in installedPackages }
                                    .mapNotNull { pkg ->
                                        runCatching {
                                            AppInfo.create(this@MainActivity, pkg)
                                        }.getOrNull()
                                    }
                                // The "add apps" card is only needed for empty collections;
                                // non-empty rows are managed through the app card menus.
                                collectionAdapters.getOrPut(collection.id) {
                                    CollectionAdapter().also { mainVerticalGridView.registerMatrixModel(it) }
                                }
                                    .submitList(
                                        if (items.isEmpty()) {
                                            listOf(
                                                CollectionAdapter.createAddToCollectionEntry(
                                                    this@MainActivity, collection.id
                                                )
                                            )
                                        } else {
                                            items
                                        }
                                    )
                            }
                        }
                    }
                }

                lifecycleScope.launch {
                    lifecycle.repeatOnLifecycle(Lifecycle.State.CREATED) {
                        // Hidden apps are only filtered out of the all-apps row; favorites
                        // and collections are curated by the user and stay untouched.
                        model.favoriteApps.collectLatest {
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
                mainVerticalGridView.adapter = mainVerticalAdapter
                mainVerticalGridView.attachMatrixSource(mainVerticalAdapter)
                mainVerticalGridView.registerMatrixModel(allAppsAdapter)
                mainVerticalGridView.registerMatrixModel(favoritesAdapter)
                mainVerticalGridView.registerMatrixModel(watchNextAdapter)
                mainVerticalGridView.scrollToTop()
                mainVerticalGridView.viewTreeObserver.addOnGlobalFocusChangeListener(mainFocusScrollListener)
                mainVerticalGridView.addOnScrollListener(topBarScrollListener)
                // The FINAL inset, not the animating one: a fit measured
                // mid-unfold would compute delta against a moving bound.
                mainVerticalGridView.fitTopBound = {
                    topBarContainer.height
                }
                mainVerticalGridView.navScrollEndAction = {
                    syncTopBarVisibility()
                }
                setupTopBarScrim()
                topBarContainer.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
                topBarContainer.addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, oldBottom ->
                    if (bottom != oldBottom) {
                        applyMainGridTopInset()
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

                assistantHintImageView.setOnClickListener {
                    expandAssistantPanel()
                }
                assistantHintImageView.setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus && !assistantHintImageView.isInTouchMode()) {
                        expandAssistantPanel()
                    }
                }

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
        matrixFirstCellFocus()
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
            mainVerticalGridView.post {
                val lm = mainVerticalGridView.layoutManager as?
                    androidx.recyclerview.widget.LinearLayoutManager
                // scrollBy is immediate and loops until row 0 is really
                // attached; scrollToTop then lands it on the inset line, and
                // reaching the top fires the visibility sync on scroll.
                var guard = 0
                while (lm != null && lm.findViewByPosition(0) == null && guard < 24) {
                    mainVerticalGridView.scrollBy(0, -mainVerticalGridView.height.coerceAtLeast(1))
                    guard++
                }
                mainVerticalGridView.scrollToTop()
                mainVerticalGridView.post {
                    matrixFirstCellFocus()
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
                COLLECTIONS_KEY ->
                    scheduleCollectionRowsChange()
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
    private fun persistCollectionOrder(collectionId: Long, packages: List<String>) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        prefs.collections = prefs.collections.map {
            if (it.id == collectionId) it.copy(packages = packages) else it
        }
    }

    private fun scheduleLiveCardResize() {
        cardSizeResizeRunnable?.let { cardSizeHandler.removeCallbacks(it) }
        cardSizeResizeRunnable = Runnable {
            val oldAppSize = lastAppCardSize
            lastAppCardSize = sharedPreferences.appCardSize
            lastFavoriteCardSize = sharedPreferences.favoriteCardSize
            lastWatchNextCardSize = sharedPreferences.watchNextCardSize
            lastCardCornerRadius = sharedPreferences.cardCornerRadius
            // Rebind at the old size first so the animation grows from it instead
            // of flashing the new size for one frame.
            if (oldAppSize != sharedPreferences.appCardSize) {
                allAppsAdapter.liveSizeOverride = oldAppSize
            }
            refreshAllRows()
            if (oldAppSize != lastAppCardSize) {
                for (i in 0 until mainVerticalGridView.childCount) {
                    val rowView = mainVerticalGridView.getChildAt(i) as? MainRowItemView ?: continue
                    if (rowView.carouselAdapter() === allAppsAdapter) {
                        rowView.animateCarouselSize(oldAppSize, lastAppCardSize)
                    }
                }
            }
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
        checkCardSizeChanges()
        dismissStaleModalTasks()
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
                mainVerticalGridView.scrollToTop()
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
        collectionAdapters.values.forEach { it.notifyDataSetChanged() }
        mainVerticalAdapter.notifyDataSetChanged()
        for (i in 0 until mainVerticalGridView.childCount) {
            (mainVerticalGridView.getChildAt(i) as? MainRowItemView)?.updateCarouselPadding()
        }
    }

    // The settings editor runs in its own window: editing a collection must not
    // rebind its row on every slider tick (the row view caches its applied settings
    // key), so grid/column changes are coalesced here into one full row-list replace
    // and plain card-size/radius edits rebind cards lazily via refreshAllRows on resume.
    private var collectionSignature = listOf<Pair<Long, Pair<Boolean, Int>>>()
    private var collectionCardSignature = listOf<Pair<Long, Pair<Int, Int>>>()
    private var collectionRowsRunnable: Runnable? = null

    private fun scheduleCollectionRowsChange() {
        collectionRowsRunnable?.let { cardSizeHandler.removeCallbacks(it) }
        collectionRowsRunnable = Runnable {
            val collections = sharedPreferences.collections
            val signature = collections.map { it.id to (it.grid to it.gridColumns) }
            if (signature != collectionSignature) {
                collectionSignature = signature
                collectionCardSignature = collections.map { it.id to (it.cardSize to it.cornerRadius) }
                currentRows?.let { rows ->
                    // The row being replaced may host the grid's focused view: releasing
                    // the focus before dropping the rows keeps focus handling sane.
                    mainVerticalGridView.clearFocus()
                    mainVerticalAdapter.submitList(null)
                    mainVerticalAdapter.submitList(rows)
                }
            } else {
                // Size/radius edits: no row rebuild, just rebind the cards of the
                // collection rows. Focus is released first because rebinding recycles
                // views and a focused card being recycled mid-rebind crashes the grid.
                val cardSignature = collections.map { it.id to (it.cardSize to it.cornerRadius) }
                if (cardSignature != collectionCardSignature) {
                    val previous = collectionCardSignature.toMap()
                    collectionCardSignature = cardSignature
                    mainVerticalGridView.clearFocus()
                    val animatedIds = mutableSetOf<Long>()
                    for (i in 0 until mainVerticalGridView.childCount) {
                        val rowView = mainVerticalGridView.getChildAt(i) as? MainRowItemView
                            ?: continue
                        val adapter = rowView.carouselAdapter() as? CollectionAdapter ?: continue
                        val entry = collectionAdapters.entries.firstOrNull { it.value === adapter }
                            ?: continue
                        val oldSize = previous[entry.key]?.first ?: adapter.cardSize
                        if (oldSize != adapter.cardSize) {
                            animatedIds += entry.key
                            rowView.animateCarouselSize(oldSize, adapter.cardSize)
                        } else {
                            adapter.notifyDataSetChanged()
                            rowView.updateCarouselPadding()
                        }
                    }
                    // Rows that are currently off-screen converge without animation.
                    collectionAdapters.forEach { (id, adapter) ->
                        if (id !in animatedIds) adapter.notifyDataSetChanged()
                    }
                }
            }
        }.also { cardSizeHandler.postDelayed(it, 150) }
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

    // The bar is an isolated island: LEFT/RIGHT must clamp at its edges
    // instead of falling through to the default sequential focus search
    // (which jumps into the card rows).
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (event.action == KeyEvent.ACTION_DOWN && isInsideTopBar(currentFocus ?: return super.dispatchKeyEvent(event))) {
            if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
                // Re-entry into the grid is the first visible card, never a
                // geometric guess of the pixel FocusFinder.
                if (mainVerticalGridView.focusFirstVisibleCard()) {
                    return true
                }
            }
        }
        if (event.action == KeyEvent.ACTION_DOWN &&
            (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT)
        ) {
            val focused = currentFocus
            if (focused != null && isInsideTopBar(focused)) {
                val items = ArrayList<View>()
                collectTopBarFocusables(topBarContainer, items)
                items.sortBy { item ->
                    val loc = IntArray(2)
                    item.getLocationOnScreen(loc)
                    loc[0]
                }
                val index = items.indexOf(focused)
                if (index >= 0) {
                    if (code == KeyEvent.KEYCODE_DPAD_LEFT) {
                        if (index == 0) return true
                        val previous = items[index - 1]
                        if (previous === assistantHintImageView) {
                            expandAssistantPanel()
                            voiceAssistantButton.requestFocus()
                        } else {
                            previous.requestFocus(View.FOCUS_LEFT)
                        }
                        return true
                    } else {
                        if (index == items.size - 1) return true
                        items[index + 1].requestFocus(View.FOCUS_RIGHT)
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun isInsideTopBar(view: View): Boolean {
        var current = view.parent
        while (current != null) {
            if (current === topBarContainer) return true
            current = current.parent
        }
        return false
    }

    private fun collectTopBarFocusables(view: View, result: MutableList<View>) {
        if (view.visibility != View.VISIBLE) return
        if (view.isFocusable || view.isClickable) {
            result.add(view)
            return
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                if (child != null) collectTopBarFocusables(child, result)
            }
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
        if (assistantButtonsContainer.isVisible) return
        assistantPanelExpanding = true
        setTopBarVisible(true)
        assistantTransition.removeTarget(assistantHintImageView)
        assistantTransition.addTarget(assistantButtonsContainer)
        TransitionManager.beginDelayedTransition(topBarContainer, assistantTransition)
        assistantHintImageView.isVisible = false
        assistantButtonsContainer.isVisible = true
        voiceAssistantButton.isFocusable = true
        keyboardAssistantButton.isFocusable = true
        voiceAssistantButton.requestFocus()
        voiceAssistantButton.post {
            assistantPanelExpanding = false
        }
    }

    private fun collapseAssistantPanel() {
        if (assistantPanelExpanding) return
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

        val assistantButtonFocusListener = View.OnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val focus = currentFocus
                if (focus != null && !isTopBarDescendant(focus)) {
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
) : androidx.recyclerview.widget.RecyclerView(context, attrs, defStyleAttr) {
    private val mainFocusSearchUp = View.FOCUS_UP
    private val mainFocusSearchDown = View.FOCUS_DOWN

    // Called on the row-0 UP escape: reveals the top bar and returns its
    // focusable target (the bar may be auto-hidden at that moment).
    var upEscapeAction: (() -> View?)? = null

    init {
        // A standard LinearLayoutManager always re-derives positions from the actual
        // measured child heights; leanback's GridLayoutManager reuses stale cached row
        // sizes after a row's height changes, which stacks rows on each other.
        // onRequestChildFocus returning true suppresses RecyclerView's instant
        // scroll-to-child; the fit is animated by fitFocusedCard instead.
        layoutManager = object : androidx.recyclerview.widget.LinearLayoutManager(context, VERTICAL, false) {
            override fun onRequestChildFocus(
                parent: RecyclerView,
                state: RecyclerView.State,
                child: View,
                focused: View?
            ): Boolean = true
        }
        val spacing = (8f * resources.displayMetrics.density).toInt()
        addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(
                outRect: android.graphics.Rect,
                view: View,
                parent: RecyclerView,
                state: RecyclerView.State,
            ) {
                outRect.top = if (parent.getChildAdapterPosition(view) == 0) 0 else spacing
            }
        })
    }

    fun scrollToTop() {
        (layoutManager as? androidx.recyclerview.widget.LinearLayoutManager)
            ?.scrollToPositionWithOffset(0, paddingTop)
    }

    // Re-entry target from the top bar: the leftmost card of the first
    // visible row, focused downward and fitted with the usual animation.
    fun focusFirstVisibleCard(): Boolean {
        val lm = layoutManager as? androidx.recyclerview.widget.LinearLayoutManager ?: return false
        val rowPos = lm.findFirstVisibleItemPosition()
        if (rowPos == RecyclerView.NO_POSITION) {
            return false
        }
        val row = lm.findViewByPosition(rowPos) ?: return false
        val cards = ArrayList<View>()
        fun collect(v: View) {
            if (v.isFocusable && v.visibility == View.VISIBLE && v.isEnabled) {
                cards.add(v)
            }
            if (v is ViewGroup) {
                for (i in 0 until v.childCount) {
                    collect(v.getChildAt(i))
                }
            }
        }
        collect(row)
        val target = cards.minByOrNull { it.x } ?: return false
        target.requestFocus(View.FOCUS_DOWN)
        fitFocusedCard(target)
        return true
    }

    // Address of the cell holding the current grid focus. A data update
    // re-binds rows and can detach the focused view; this cell is where
    // focus is re-anchored, so a background refresh can never strand it.
    private var lastCell: Pair<Int, Int>? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnGlobalFocusChangeListener(globalFocusListener)
    }

    override fun onDetachedFromWindow() {
        viewTreeObserver.removeOnGlobalFocusChangeListener(globalFocusListener)
        super.onDetachedFromWindow()
    }


    // Glides the viewport one measured row step at a time: the caller names
    // the row the viewport sits on NOW, so a press can never travel more
    // than the single matrix step to its neighbor.
    private var pendingOffset = 0

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        if (lastCell != null) {
            post { tryLandPending() }
        }
    }

    var navScrollActive = false
    var navScrollEndAction: (() -> Unit)? = null
    private var navScrollToken = 0

    private fun finishNavScroll() {
        navScrollActive = false
        navScrollEndAction?.invoke()
    }

    private fun animateScroll(delta: Int) {
        val token = ++navScrollToken
        fitAnimator?.cancel()
        if (delta == 0) {
            finishNavScroll()
            return
        }
        navScrollActive = true
        val duration = travelDuration(delta)
        var applied = 0
        fitAnimator = android.animation.ValueAnimator.ofInt(0, delta).apply {
            this.duration = duration
            interpolator = DecelerateInterpolator(1.2f)
            addUpdateListener { animator ->
                val value = animator.animatedValue as Int
                scrollBy(0, value - applied)
                applied = value
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (token != navScrollToken) return
                    finishNavScroll()
                    tryLandPending()
                }
            })
            start()
        }
    }

    private val globalFocusListener = android.view.ViewTreeObserver.OnGlobalFocusChangeListener { _, newFocus ->
        if (newFocus != null && isInMainGrid(newFocus)) {
            lastCell = coordOf(newFocus)
            return@OnGlobalFocusChangeListener
        }
        if (lastCell != null) tryLandPending()
    }

    // Landing is event-driven, never polled: cards materialize in layout
    // passes and adapter data events, so those are the only places that
    // re-check the pending anchor. The pending target is set by the press
    // path; while it is pending, repeat presses are no-ops (single gate in
    // targetFromCoordinate).
    private fun tryLandPending() {
        val coord = lastCell ?: return
        val cell = matrixCell(coord.first, coord.second) ?: return
        val target = resolveCell(cell)
        if (target != null) {
            if (findFocus() !== target) {
                target.requestFocus()
                fitFocusedCard(target)
            }
            return
        }
        val lm = layoutManager as? androidx.recyclerview.widget.LinearLayoutManager ?: return
        if (scrollState == RecyclerView.SCROLL_STATE_IDLE &&
            fitAnimator?.isRunning != true && lm.findViewByPosition(cell.rowPos) == null
        ) {
            lm.scrollToPositionWithOffset(cell.rowPos, pendingOffset)
        }
    }

    // Scroll speed law for every nav motion: fixed ~0.67 px per ms
    // (2 px travel = 3 ms), so duration grows linearly with the distance.
    private fun travelDuration(distance: Int): Long = kotlin.math.abs(distance) * 2L / 3L

    var fitTopBound: (() -> Int)? = null

    private var fitAnimator: android.animation.ValueAnimator? = null

    private fun fitFocusedCard(focused: View) {
        if (width == 0 || height == 0) {
            finishNavScroll()
            return
        }
        if (isLayoutRequested || focused.isLayoutRequested) {
            post { findFocus()?.let { if (isInMainGrid(it)) fitFocusedCard(it) } }
            return
        }
        animateScroll(fitDeltaFor(focused))
    }

    // Scroll delta needed for the given card to fully fit the usable viewport
    // (label and final 1.1x focus size included); 0 when already inside.
    private fun fitDeltaFor(card: View): Int {
        if (width == 0 || height == 0) return 0
        val rect = Rect()
        card.getFocusedRect(rect)
        (card.findViewById<View>(R.id.app_name) ?: card.findViewById(R.id.title))?.let { name ->
            rect.bottom += name.height + (8f * resources.displayMetrics.density).toInt()
        }
        val sx = maxOf(card.scaleX, 1.1f)
        val sy = maxOf(card.scaleY, 1.1f)
        if (sx != 1f || sy != 1f) {
            val px = card.pivotX
            val py = card.pivotY
            rect.left = (px + (rect.left - px) * sx).toInt()
            rect.right = (px + (rect.right - px) * sx).toInt()
            rect.top = (py + (rect.top - py) * sy).toInt()
            rect.bottom = (py + (rect.bottom - py) * sy).toInt()
        }
        offsetDescendantRectToMyCoords(card, rect)
        val topBound = fitTopBound?.invoke() ?: paddingTop
        return when {
            rect.top < topBound -> rect.top - topBound
            rect.bottom > height - paddingBottom -> rect.bottom - (height - paddingBottom)
            else -> 0
        }
    }

    // Vertical D-pad inside the main grid is resolved by the address matrix
    // only: androidx RecyclerView.dispatchKeyEvent would otherwise run its own
    // performFocusSearch (pixel FocusFinder) for DPAD_UP/DOWN.
    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        val code = event?.keyCode ?: return super.dispatchKeyEvent(event)
        if (event.action != KeyEvent.ACTION_DOWN ||
            (code != KeyEvent.KEYCODE_DPAD_DOWN && code != KeyEvent.KEYCODE_DPAD_UP)
        ) {
            return super.dispatchKeyEvent(event)
        }
        val direction = if (code == KeyEvent.KEYCODE_DPAD_UP) mainFocusSearchUp else mainFocusSearchDown
        var focused = findFocus()
        if (focused == null || !isInMainGrid(focused)) {
            // Focus vanished (detached by a nested re-bind) or is mid-transition
            // in the container: continue the press from the anchor cell; the
            // pending transition itself is completed by layout/data events.
            // Focus parked on the bar is a
            // deliberate location (lastCell cleared) - untouched.
            val cell = lastCell?.let { matrixCell(it.first, it.second) } ?: return super.dispatchKeyEvent(event)
            val reentry = resolveCell(cell) ?: run {
                tryLandPending()
                return true
            }
            reentry.requestFocus()
            focused = reentry
        }
        val target = verticalFocusSearch(focused, direction)
        if (target != null) {
            target.requestFocus(direction)
            stopScroll()
            animateScroll(fitDeltaFor(target))
        } else if (code == KeyEvent.KEYCODE_DPAD_UP && isFirstMainRow(focused)) {
            lastCell = null
            val barTarget = upEscapeAction?.invoke()
            if (barTarget?.requestFocus(mainFocusSearchUp) == true) return true
            return super.dispatchKeyEvent(event)
        }
        // Consume every DPAD inside the grid: passing it to RecyclerView
        // would run the pixel FocusFinder and tear focus off the matrix.
        return true
    }

    // The mouse wheel must scroll exactly like a finger gesture. RecyclerView's
    // default wheel path scrolls while the state stays IDLE, so the focus
    // re-anchor (tryLandPending) yanks the viewport back to the focused card.
    // A gesture scrolls in SETTLING state, where that re-anchor is suppressed;
    // routing the wheel through smoothScrollBy reproduces the gesture path with
    // the framework's own scroll factor, so speed and direction are unchanged
    // (wheel up scrolls up, wheel down scrolls down).
    override fun onGenericMotionEvent(e: MotionEvent): Boolean {
        if (e.action == MotionEvent.ACTION_SCROLL) {
            val vScroll = -e.getAxisValue(MotionEvent.AXIS_VSCROLL)
            if (vScroll != 0f) {
                fitAnimator?.cancel()
                val factor = androidx.core.view.ViewConfigurationCompat
                    .getScaledVerticalScrollFactor(android.view.ViewConfiguration.get(context), context)
                smoothScrollBy(0, (vScroll * factor).toInt())
                return true
            }
        }
        return super.onGenericMotionEvent(e)
    }

    override fun focusSearch(focused: View?, direction: Int): View? {
        val target = focused ?: return null
        if (!isInMainGrid(target)) {
            return super.focusSearch(target, direction)
        }
        return if (direction == mainFocusSearchUp || direction == mainFocusSearchDown) {
            verticalFocusSearch(target, direction)
        } else {
            super.focusSearch(target, direction)
        }
    }

    fun isFirstMainRow(target: View): Boolean {
        val row = findMainGridChild(target) ?: return false
        // Adapter position 0, not the first attached child: when scrolled deep,
        // child index 0 is just the top visible row and must not escape to the top bar.
        return getChildAdapterPosition(row) == 0
    }

    private fun isInMainGrid(view: View): Boolean {
        var current = view.parent
        while (current != null) {
            if (current === this) return true
            current = current.parent
        }
        return false
    }

    // The matrix covers the whole main grid at once, built straight from the
    // row models: every card of every carousel and grid row has global
    // coordinates computed from adapter contents, no per-view bookkeeping.
    // Y counts every laid line of every row (grid rows contribute one Y per
    // line, a carousel one Y in total); X is the position inside the line.
    // Vertical navigation moves Y +-1 keeping X.
    private data class RowShape(val isGrid: Boolean, val span: Int, val count: Int)

    private fun rowShape(rowPos: Int): RowShape {
        val main = adapter as? MainVerticalAdapter ?: return RowShape(false, 1, 0)
        if (rowPos < 0 || rowPos >= main.itemCount) return RowShape(false, 1, 0)
        val a = main.currentList[rowPos].second.adapter
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val isGrid = when (a) {
            is CollectionAdapter -> a.gridMode
            is AllAppsAdapter -> prefs.allAppsGrid
            else -> false
        }
        if (!isGrid) return RowShape(false, 1, a.itemCount)
        val span = when (a) {
            is CollectionAdapter -> a.gridColumns
            else -> prefs.allAppsGridColumns
        }.coerceIn(4, 10)
        return RowShape(true, span, a.itemCount)
    }

    private fun lineCount(rowPos: Int): Int {
        val shape = rowShape(rowPos)
        return if (shape.isGrid) ((shape.count + shape.span - 1) / shape.span).coerceAtLeast(1) else 1
    }

    private fun globalYStart(rowPos: Int): Int {
        var y = 0
        for (i in 0 until rowPos) y += lineCount(i)
        return y
    }

    // Card view with the adapter index inside the row view of rowPos.
    // The matrix: every card of the main grid in its own cell, built once
    // from the row models (adapters), not from views. (Y, X) -> cell with the
    // row position and the card index inside the row. Rebuilt only when the
    // models change (adapter data observer), never per key press.
    private class NavCell(val rowPos: Int, val idx: Int)

    private val navMatrix = HashMap<Pair<Int, Int>, NavCell>()

    private val modelObserver = object : RecyclerView.AdapterDataObserver() {
        override fun onChanged() { post { rebuildMatrix(); tryLandPending() } }
        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
            post { rebuildMatrix(); tryLandPending() }
        }

        override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
            post { rebuildMatrix(); tryLandPending() }
        }

        override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
            post { rebuildMatrix(); tryLandPending() }
        }
    }

    fun attachMatrixSource(main: MainVerticalAdapter) {
        registerMatrixModel(main)
        rebuildMatrix()
    }

    // Row content lives in the nested adapters; their updates never reach
    // the main adapter's observer, so each nested adapter is registered too
    // - the matrix always covers every card of every row.
    fun registerMatrixModel(a: RecyclerView.Adapter<*>) {
        a.registerAdapterDataObserver(modelObserver)
    }

    private fun rebuildMatrix() {
        navMatrix.clear()
        val main = adapter as? MainVerticalAdapter ?: return
        for (rowPos in 0 until main.itemCount) {
            val shape = rowShape(rowPos)
            val span = if (shape.isGrid) shape.span else shape.count.coerceAtLeast(1)
            val start = globalYStart(rowPos)
            for (idx in 0 until shape.count) {
                navMatrix[Pair(start + idx / span, idx % span)] = NavCell(rowPos, idx)
            }
        }
    }

    private fun matrixCell(y: Int, x: Int): NavCell? = navMatrix[Pair(y, x)]

    fun matrixFirstCard(): View? = matrixCell(0, 0)?.let { resolveCell(it) }

    fun collectAttachedCards(view: View, result: MutableList<View>) {
        if (view.visibility != View.VISIBLE) return
        if (view is Card) {
            result.add(view)
            return
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                if (child != null) collectAttachedCards(child, result)
            }
        }
    }

    // Card view of a cell inside its row view. The nearest-card fallback is
    // restricted to the SAME matrix line: picking the closest index across
    // lines jumps focus to another line of a grid row (a different Y) and
    // drags the viewport several cards. A missing line stays pending until
    // its cards bind.
    private fun cardInRow(row: View, idx: Int, span: Int): View? {
        val cards = mutableListOf<View>()
        collectAttachedCards(row, cards)
        val line = idx / span
        var best: View? = null
        var bestDistance = Int.MAX_VALUE
        for (card in cards) {
            val owner = card.parent as? RecyclerView ?: continue
            val i = owner.getChildAdapterPosition(card)
            if (i == RecyclerView.NO_POSITION || i / span != line) continue
            if (i == idx) return card
            val distance = kotlin.math.abs(i - idx)
            if (distance < bestDistance) {
                bestDistance = distance
                best = card
            }
        }
        return best
    }

    // Address of a focused card: structural lookup (its row view and adapter
    // index), resolved to the matrix cell that stores exactly that card.
    private fun coordOf(view: View): Pair<Int, Int>? {
        val row = findMainGridChild(view) ?: return null
        val rowPos = getChildAdapterPosition(row)
        if (rowPos == RecyclerView.NO_POSITION) return null
        val owner = view.parent as? RecyclerView ?: return null
        val idx = owner.getChildAdapterPosition(view)
        if (idx == RecyclerView.NO_POSITION) return null
        for (entry in navMatrix.entries) {
            if (entry.value.rowPos == rowPos && entry.value.idx == idx) return entry.key
        }
        return null
    }

    private fun resolveCell(cell: NavCell): View? {
        val lm = layoutManager as? androidx.recyclerview.widget.LinearLayoutManager ?: return null
        val row = lm.findViewByPosition(cell.rowPos) ?: return null
        val shape = rowShape(cell.rowPos)
        // A carousel is a single matrix line: every attached card is a valid
        // edge fallback there. Only grid rows must stay inside their line.
        return cardInRow(row, cell.idx, if (shape.isGrid) shape.span else Int.MAX_VALUE)
    }

    private fun targetFromCoordinate(coord: Pair<Int, Int>, direction: Int, focused: View): View? {
        val nextY = coord.first + if (direction == mainFocusSearchUp) -1 else 1
        var x = coord.second
        while (x >= 0 && matrixCell(nextY, x) == null) x--
        val cell = matrixCell(nextY, x) ?: return null
        val lm = layoutManager as? androidx.recyclerview.widget.LinearLayoutManager ?: return null
        val card = if (lm.findViewByPosition(cell.rowPos) == null) null else resolveCell(cell)
        if (card != null) return card
        // Target not materialized: one exact matrix step toward it, address
        // parked; layout/data events land focus. Repeat presses on the same
        // pending target travel nothing.
        if (lastCell?.let { matrixCell(it.first, it.second) } != cell) {
            val focusedRow = findMainGridChild(focused) ?: return null
            lastCell = Pair(nextY, x)
            pendingOffset = focusedRow.top
            lm.scrollToPositionWithOffset(cell.rowPos, pendingOffset)
        }
        return null
    }

    private fun verticalFocusSearch(focused: View, direction: Int): View? {
        val coord = coordOf(focused) ?: return null
        return targetFromCoordinate(coord, direction, focused)
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
}
