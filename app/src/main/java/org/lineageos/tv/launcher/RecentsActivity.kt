/*
 * SPDX-FileCopyrightText: 2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.tv.launcher

import android.app.Activity
import android.app.ActivityManager
import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.DecelerateInterpolator
import android.view.animation.RotateAnimation
import android.view.animation.ScaleAnimation
import android.view.animation.TranslateAnimation
import android.widget.LinearLayout
import android.widget.TextView
import androidx.leanback.widget.HorizontalGridView
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import android.view.ViewGroup
import org.lineageos.tv.launcher.ext.externalLauncherPkg
import org.lineageos.tv.launcher.ext.recentAppsCardSize
import org.lineageos.tv.launcher.ext.recentAppsEnabled
import org.lineageos.tv.launcher.ext.recentAppsShowThumbnails
import org.lineageos.tv.launcher.model.LeanbackAppInfo
import org.lineageos.tv.launcher.view.AppCard

/**
 * Transparent host for the recents overlay. It carries no UI: the translucent
 * window keeps the activity below visible and unpaused while the menu is up,
 * which is why the overlay is opened from here and not from MainActivity -
 * bringing the home activity forward would pause the app the menu was called
 * from. Manifest: Theme.Translucent.NoTitleBar, excludeFromRecents, noHistory.
 */
class RecentsActivity : Activity() {

    companion object {
        private const val TAG = "RecentsActivity"

        // The overlay is a window attached outside the activity lifecycle, and
        // noHistory means the instance may be replaced between presses, so the
        // reference consulted by the close paths is static.
        @Volatile
        private var currentOverlay: View? = null

        fun isShowing(): Boolean = currentOverlay != null
    }

    private val sharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleRecentsRequest()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleRecentsRequest()
    }

    /**
     * BACK/ESC that bypasses the overlay window (focus never entered it, or
     * already left) must still only close the overlay. No overlay open means
     * the default behavior: the empty translucent activity finishes.
     */
    override fun onBackPressed() {
        if (overlayView != null || currentOverlay != null) {
            hideOverlay()
        } else {
            super.onBackPressed()
        }
    }

    // ---- Recents overlay window ----
    private val overlayWindowManager by lazy {
        getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }
    private var overlayView: View? = null
    private var recentGrid: HorizontalGridView? = null
    private var recentAdapter: RecentCardsAdapter? = null
    private val taskSnapshots = mutableMapOf<Int, Bitmap>()

    /**
     * Starting this activity IS the recents request: nothing else launches it
     * (no launcher entry, no intent filter) - the double tap on HOME reaches
     * it through SystemUI's TvStatusBar and Alt+Tab through GlobalHotkeyService.
     * The request is a toggle: with the menu up it closes it.
     *
     * There is no double tap detection here on purpose. The platform detects
     * it in PhoneWindowManager, which sees both presses in the input stream;
     * by the time a request reaches here the decision is already made. This
     * activity only draws the result.
     */
    private fun handleRecentsRequest() {
        GlobalHotkeyService.clearOverlayFlag()
        if (currentOverlay != null) {
            hideOverlay()
            return
        }
        openRecentsOrHome()
    }

    private fun openRecentsOrHome() {
        if (sharedPreferences.recentAppsEnabled) {
            showOverlay()
        } else {
            finish()
        }
    }

    private fun dismissOverlay() {
        try {
            // A fresh instance may be asked to dismiss an overlay opened by the
            // previous one: fall back to the shared reference.
            (overlayView ?: currentOverlay)?.let {
                overlayWindowManager.removeViewImmediate(it)
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to dismiss overlay", e)
        }
        setOverlayView(null)
        GlobalHotkeyService.clearOverlayFlag()
    }

    /**
     * Keeps the instance field and the static one in sync, so a HOME press that
     * lands on a fresh instance can still find an overlay opened by the
     * previous one.
     */
    private fun setOverlayView(view: View?) {
        overlayView = view
        currentOverlay = view
    }

    private fun showOverlay() {
        // Never stack overlays: a second window on top of the first swallows all
        // touch and focus, and BACK only ever reaches the newest one.
        if (overlayView != null || currentOverlay != null) {
            dismissOverlay()
        }

        val recentTasks = getRecentTasks()

        val root = object : LinearLayout(this) {
            private var lastHomeDownTime = 0L
            private var closeOnHomeUp = false

            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                // Close on RELEASE, not on press: closing on DOWN hands focus
                // to the layer below while the UP of the same press is still
                // in flight, and it triggers there (launcher jumps). Swallow
                // the press, act on the release, swallow that too.
                if (event.keyCode == KeyEvent.KEYCODE_HOME) {
                    when (event.action) {
                        KeyEvent.ACTION_DOWN -> {
                            // Only the second press of a double tap closes the
                            // menu; a held key repeats DOWN and must not count.
                            if (event.repeatCount == 0) {
                                closeOnHomeUp = event.eventTime - lastHomeDownTime <=
                                    ViewConfiguration.getDoubleTapTimeout()
                                lastHomeDownTime = event.eventTime
                            }
                            return true
                        }
                        KeyEvent.ACTION_UP -> {
                            if (closeOnHomeUp) {
                                closeOnHomeUp = false
                                hideOverlay()
                            }
                            return true
                        }
                    }
                    return true
                }
                if (event.action == KeyEvent.ACTION_DOWN) {
                    when (event.keyCode) {
                        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                            return true
                        }
                    }
                }
                if (event.action == KeyEvent.ACTION_UP) {
                    when (event.keyCode) {
                        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                            hideOverlay()
                            return true
                        }
                    }
                }
                return super.dispatchKeyEvent(event)
            }
        }.apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xF5000000.toInt())
            isFocusable = true
            isFocusableInTouchMode = true
        }

        val headerContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                (32 * resources.displayMetrics.density).toInt(),
                (24 * resources.displayMetrics.density).toInt(),
                (32 * resources.displayMetrics.density).toInt(),
                (16 * resources.displayMetrics.density).toInt()
            )
        }

        val titleText = TextView(this).apply {
            text = getString(R.string.recent_tasks_title, recentTasks.size)
            setTextColor(0xFFFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        headerContainer.addView(titleText)
        root.addView(headerContainer)

        val themedContext = ContextThemeWrapper(this, R.style.Theme_Catapult)

        val cardSize = sharedPreferences.recentAppsCardSize
        val verticalPadding =
            ((80.0 * (50 - cardSize) / 30.0) * resources.displayMetrics.density).toInt()
        val horizontalPadding = (32 * resources.displayMetrics.density).toInt()

        // Same widget the desktop rows scroll with, so left/right focus search,
        // centering and edge behavior match the desktop carousel exactly. The
        // scaled cards sat inside a HorizontalScrollView before, whose focus
        // and scroll math ignores the card scale transform.
        // The weighted frame owns all the leftover height; leanback centers its
        // single row inside it, so the frame's bottom half is exactly the empty
        // space under the carousel. The button floats in that space via a frame
        // margin and never consumes layout height (no layout feedback loops).
        val gridFrame = android.widget.FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        val grid = HorizontalGridView(themedContext).apply {
            isHorizontalScrollBarEnabled = false
            clipChildren = false
            clipToPadding = false
            setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        recentAdapter = RecentCardsAdapter(recentTasks.toMutableList(), themedContext)
        grid.adapter = recentAdapter
        recentGrid = grid
        gridFrame.addView(grid)

        // Add "Close All" button only if there are tasks
        var closeAllButton: android.widget.Button? = null
        if (recentTasks.isNotEmpty()) {
            val density = resources.displayMetrics.density
            val corner = 24f * density
            fun rounded(color: Int, stroke: Int = 0x00000000): android.graphics.drawable.Drawable {
                val d = android.graphics.drawable.GradientDrawable()
                d.shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                d.cornerRadius = corner
                d.setColor(color)
                if (stroke != 0x00000000) {
                    d.setStroke((1f * density).toInt(), stroke)
                }
                return d
            }
            val buttonBackground = android.graphics.drawable.StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused), rounded(0xFFFFFFFF.toInt()))
                addState(intArrayOf(android.R.attr.state_selected), rounded(0xFFFFFFFF.toInt()))
                addState(intArrayOf(android.R.attr.state_pressed), rounded(0x80FFFFFF.toInt()))
                addState(intArrayOf(), rounded(0x33FFFFFF.toInt(), 0x55FFFFFF.toInt()))
            }
            val buttonTextColor = android.content.res.ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_focused),
                    intArrayOf(android.R.attr.state_selected),
                    intArrayOf()
                ),
                intArrayOf(
                    0xFF202124.toInt(),
                    0xFF202124.toInt(),
                    0xFFFFFFFF.toInt()
                )
            )
            closeAllButton = android.widget.Button(this).apply {
                text = getString(R.string.close_all)
                textSize = 16f
                letterSpacing = 0.04f
                isAllCaps = true
                setTextColor(buttonTextColor)
                background = buttonBackground
                setPadding(
                    (28 * density).toInt(),
                    (12 * density).toInt(),
                    (28 * density).toInt(),
                    (12 * density).toInt()
                )
                stateListAnimator = null
                layoutParams = android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                }
                setOnClickListener {
                    closeAllTasksWithAnimation()
                }
            }
            gridFrame.addView(closeAllButton)

            // Center the button in the empty band between the carousel bottom
            // and the screen bottom: band = frameHeight/2 - renderedRowHalf,
            // margin = band/2 - buttonHeight/2, floored at 12dp.
            gridFrame.post {
                val rowHalf = (resources.displayMetrics.heightPixels *
                    sharedPreferences.recentAppsCardSize / 100f) / 2f
                val band = gridFrame.height / 2f - rowHalf
                val lp = closeAllButton.layoutParams as android.widget.FrameLayout.LayoutParams
                lp.bottomMargin = ((band - closeAllButton.height) / 2f)
                    .toInt()
                    .coerceAtLeast((12 * density).toInt())
                closeAllButton.layoutParams = lp
            }
        }
        root.addView(gridFrame)

        val windowParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            title = "RecentAppsOverlay"
        }

        overlayWindowManager.addView(root, windowParams)
        setOverlayView(root)
        android.util.Log.d(TAG, "Recents overlay shown with ${recentTasks.size} tasks")

        root.viewTreeObserver.addOnWindowAttachListener(object : ViewTreeObserver.OnWindowAttachListener {
            override fun onWindowAttached() {
                root.post {
                    root.isFocusable = true
                    root.isFocusableInTouchMode = true
                    root.requestFocus()
                    focusRecentPosition(0)
                }
            }

            override fun onWindowDetached() {
                root.viewTreeObserver.removeOnWindowAttachListener(this)
                setOverlayView(null)
            }
        })
    }

    private fun removeTaskWithAnimation(card: View, taskId: Int) {
        val density = resources.displayMetrics.density
        
        var removed = false
        
        // Try ActivityTaskManager.removeTask via reflection
        try {
            val atmClass = Class.forName("android.app.ActivityTaskManager")
            val method = atmClass.getMethod("removeTask", Int::class.javaPrimitiveType)
            method.invoke(null, taskId)
            android.util.Log.d(TAG, "Removed task $taskId via ActivityTaskManager.removeTask")
            removed = true
        } catch (e: Exception) {
            android.util.Log.d(TAG, "ActivityTaskManager.removeTask failed: ${e.message}")
        }
        
        // Try IActivityTaskManager via ServiceManager
        if (!removed) {
            try {
                val serviceManagerClass = Class.forName("android.os.ServiceManager")
                val getServiceMethod = serviceManagerClass.getMethod("getService", String::class.java)
                val service = getServiceMethod.invoke(null, "activity_task")
                
                val stubClass = Class.forName("android.app.IActivityTaskManager\$Stub")
                val asInterfaceMethod = stubClass.getMethod("asInterface", Class.forName("android.os.IBinder"))
                val iatm = asInterfaceMethod.invoke(null, service)
                
                // Try removeTask method
                val removeMethod = iatm.javaClass.getMethod("removeTask", Int::class.javaPrimitiveType)
                removeMethod.invoke(iatm, taskId)
                android.util.Log.d(TAG, "Removed task $taskId via IActivityTaskManager.removeTask")
                removed = true
            } catch (e: Exception) {
                android.util.Log.d(TAG, "IATM removeTask failed: ${e.message}")
            }
        }
        
        if (!removed) {
            // Try to use the underlying activity task manager directly
            try {
                val atm = android.app.ActivityTaskManager.getInstance()
                val method = atm.javaClass.getMethod("removeTask", Int::class.javaPrimitiveType)
                method.invoke(atm, taskId)
                android.util.Log.d(TAG, "Removed task $taskId via ActivityTaskManager.getInstance().removeTask")
                removed = true
            } catch (e: Exception) {
                android.util.Log.e(TAG, "All removeTask methods failed", e)
            }
        }
        
        // Always animate and remove card from UI regardless of actual task removal
        card.animate()
            .translationY(-500f * density)
            .alpha(0f)
            .setDuration(300)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                val adapter = recentAdapter ?: return@withEndAction
                val position = adapter.positionOf(taskId)
                if (position >= 0) {
                    adapter.removeAt(position)
                }
                if (adapter.itemCount == 0) {
                    hideOverlay()
                } else {
                    recentGrid?.post {
                        focusRecentPosition(position.coerceAtLeast(0)
                            .coerceAtMost(adapter.itemCount - 1))
                    }
                }
            }
    }

    private fun hideOverlay() {
        // Idempotent: a second BACK with no overlay must not touch tasks.
        if (overlayView == null && currentOverlay == null) {
            return
        }
        try {
            (overlayView ?: currentOverlay)?.let {
                overlayWindowManager.removeViewImmediate(it)
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to hide overlay", e)
        }
        setOverlayView(null)

        GlobalHotkeyService.clearOverlayFlag()
        finish()
    }

    private fun closeAllTasksWithAnimation() {
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val centerX = screenWidth / 2f
        val centerY = screenHeight / 2f
        
        val cc = recentGrid ?: return
        val cardCount = cc.childCount
        
        if (cardCount == 0) {
            hideOverlay()
            return
        }
        
        // Get all task IDs and remove them via ActivityTaskManager
        val taskIds = recentAdapter?.taskIds() ?: return
        
        for (taskId in taskIds) {
            try {
                val atm = android.app.ActivityTaskManager.getInstance()
                try {
                    val method = atm.javaClass.getMethod("removeTask", Int::class.javaPrimitiveType)
                    method.invoke(atm, taskId)
                } catch (e: Exception) {
                    try {
                        val removeMethod = atm.javaClass.getMethod("removeTask", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                        removeMethod.invoke(atm, taskId, 0)
                    } catch (e2: Exception) {
                        android.util.Log.w(TAG, "Failed to remove task $taskId", e2)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Failed to remove task $taskId", e)
            }
        }
        
        // Phase 1: Move ALL to center together (no delay)
        for (i in 0 until cardCount) {
            val card = cc.getChildAt(i) as? View ?: continue
            
            val location = IntArray(2)
            card.getLocationOnScreen(location)
            val cardCenterX = location[0] + card.width / 2f
            val cardCenterY = location[1] + card.height / 2f
            
            val deltaX = centerX - cardCenterX
            val deltaY = centerY - cardCenterY
            
            val translationX = android.animation.ObjectAnimator.ofFloat(card, View.TRANSLATION_X, card.translationX, deltaX)
            val translationY = android.animation.ObjectAnimator.ofFloat(card, View.TRANSLATION_Y, card.translationY, deltaY)
            val scaleX = android.animation.ObjectAnimator.ofFloat(card, View.SCALE_X, 1f, 0.7f)
            val scaleY = android.animation.ObjectAnimator.ofFloat(card, View.SCALE_Y, 1f, 0.7f)
            
            translationX.duration = 300
            translationY.duration = 300
            scaleX.duration = 300
            scaleY.duration = 300
            
            // NO delay - all move together!
            
            translationX.start()
            translationY.start()
            scaleX.start()
            scaleY.start()
        }
        
        // Phase 2: Fly away together as ONE stack (all at same time, after phase 1)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            for (i in 0 until cardCount) {
                val card = cc.getChildAt(i) as? View ?: continue
                
                val location = IntArray(2)
                card.getLocationOnScreen(location)
                val cardCenterX = location[0] + card.width / 2f
                val cardCenterY = location[1] + card.height / 2f
                
                val centerTransX = centerX - cardCenterX
                val centerTransY = centerY - cardCenterY
                
                // All stacked on same position
                card.translationX = centerTransX
                card.translationY = centerTransY
                card.scaleX = 0.7f
                card.scaleY = 0.7f
                
                // All fly together to top-right corner (same trajectory)
                val rotation = android.animation.ObjectAnimator.ofFloat(card, View.ROTATION, 0f, 360f)
                val flyX = android.animation.ObjectAnimator.ofFloat(card, View.TRANSLATION_X, centerTransX, screenWidth + 100f)
                val flyY = android.animation.ObjectAnimator.ofFloat(card, View.TRANSLATION_Y, centerTransY, -screenHeight * 0.25f)
                val finalScaleX = android.animation.ObjectAnimator.ofFloat(card, View.SCALE_X, 0.7f, 0.02f)
                val finalScaleY = android.animation.ObjectAnimator.ofFloat(card, View.SCALE_Y, 0.7f, 0.02f)
                val alpha = android.animation.ObjectAnimator.ofFloat(card, View.ALPHA, 1f, 0f)
                
                rotation.duration = 400
                flyX.duration = 400
                flyY.duration = 400
                finalScaleX.duration = 400
                finalScaleY.duration = 400
                alpha.duration = 400
                
                val interpolator = android.view.animation.AccelerateInterpolator(1.5f)
                rotation.interpolator = interpolator
                flyX.interpolator = interpolator
                flyY.interpolator = interpolator
                
                rotation.start()
                flyX.start()
                flyY.start()
                finalScaleX.start()
                finalScaleY.start()
                alpha.start()
            }
        }, 450)
        
        // Close overlay after animation completes
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            openHomeLauncher()
        }, 700)
    }

    private fun openHomeLauncher() {
        overlayView?.let {
            try {
                overlayWindowManager.removeViewImmediate(it)
            } catch (e: Exception) {
                // ignore
            }
        }
        setOverlayView(null)
        GlobalHotkeyService.clearOverlayFlag()
        // Whoever resolves as HOME (the launcher or a picked replacement)
        // comes back; the overlay window is gone by now either way.
        try {
            val home = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(home)
        } catch (e: Exception) {
            android.util.Log.w(TAG, "No home activity to start", e)
        }
    }

    private data class RecentTask(
        val packageName: String,
        val taskId: Int,
        val displayName: String,
        val taskLabel: String? = null,
        val customIcon: android.graphics.drawable.Drawable? = null,
        val thumbnail: Bitmap? = null,
        val baseIntent: Intent? = null
    )

    private fun getRecentTasks(excludeExternal: Boolean = true,
                               excludeSelf: Boolean = true): List<RecentTask> {
        val tasks = LinkedHashMap<Int, RecentTask>()
        val myPackage = packageName
        taskSnapshots.clear()
        
        android.util.Log.d(TAG, "Getting recent tasks...")
        
        // 1. Try IActivityTaskManager via Reflection (PRIMARY)
        try {
            android.util.Log.d(TAG, "Trying IActivityTaskManager via Reflection...")
            
            val serviceManagerClass = Class.forName("android.os.ServiceManager")
            val getServiceMethod = serviceManagerClass.getMethod("getService", String::class.java)
            val service = getServiceMethod.invoke(null, "activity_task")
            
            val stubClass = Class.forName("android.app.IActivityTaskManager\$Stub")
            val asInterfaceMethod = stubClass.getMethod("asInterface", Class.forName("android.os.IBinder"))
            val iatm = asInterfaceMethod.invoke(null, service)
            
            android.util.Log.d(TAG, "Got IActivityTaskManager: $iatm")
            
            // API 34 signature: getTasks(int maxNum, boolean filterOnlyVisibleRecents, boolean keepIntentExtra, int displayId)
            try {
                val getTasksMethod = iatm.javaClass.getMethod(
                    "getTasks",
                    Int::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                )
                
                @Suppress("UNCHECKED_CAST")
                val result = getTasksMethod.invoke(iatm, 30, false, true, 0) as? List<*>
                
                if (result != null && result.isNotEmpty()) {
                    android.util.Log.d(TAG, "Reflection getTasks returned ${result.size} tasks")
                            processTaskListReflection(tasks, result, myPackage, excludeExternal,
                                excludeSelf)
                }
            } catch (e: Exception) {
                android.util.Log.d(TAG, "API 34 getTasks signature failed: ${e.message}")
                
                // Fallback: try generic getTasks
                val methods = iatm.javaClass.methods.filter { it.name == "getTasks" }
                for (method in methods) {
                    try {
                        val params = method.parameterTypes.map { paramType ->
                            when {
                                paramType == Int::class.javaPrimitiveType -> 30
                                paramType == Boolean::class.javaPrimitiveType -> false
                                else -> null
                            }
                        }.toTypedArray()
                        
                        @Suppress("UNCHECKED_CAST")
                        val result = method.invoke(iatm, *params) as? List<*>
                        
                        if (result != null && result.isNotEmpty()) {
                            android.util.Log.d(TAG, "Fallback getTasks returned ${result.size} tasks")
                    processTaskListReflection(tasks, result, myPackage, excludeExternal,
                        excludeSelf)
                            break
                        }
                    } catch (e2: Exception) {
                        android.util.Log.d(TAG, "${method.name} failed: ${e2.message}")
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Reflection approach failed", e)
        }
        
        // 2. Fallback: getRunningTasks
        if (tasks.isEmpty()) {
            try {
                android.util.Log.d(TAG, "Trying getRunningTasks as fallback...")
                val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                @Suppress("DEPRECATION")
                val runningTasks = activityManager.getRunningTasks(30)
                android.util.Log.d(TAG, "getRunningTasks: count=${runningTasks.size}")
                
                for (taskInfo in runningTasks) {
                    try {
                        val pkgName = taskInfo.baseActivity?.packageName ?: continue
                        if (excludeSelf && pkgName == myPackage) continue
                        if (excludeExternal && pkgName == sharedPreferences.externalLauncherPkg) continue
                        
                        val taskId = taskInfo.id
                        if (taskId <= 0) continue
                        
                        val topActivity = taskInfo.topActivity
                        if (topActivity == null) {
                            android.util.Log.d(TAG, "Skipping dead task (no topActivity): $pkgName")
                            continue
                        }
                        
                        android.util.Log.d(TAG, "Running task: $pkgName, id=$taskId")
                        
                        val displayName = try {
                            val appInfo = packageManager.getApplicationInfo(pkgName, 0)
                            packageManager.getApplicationLabel(appInfo).toString()
                        } catch (e: Exception) {
                            pkgName
                        }
                        
                        tasks[taskId] = RecentTask(pkgName, taskId, displayName)
                    } catch (e: Exception) {
                        android.util.Log.w(TAG, "Failed to process running task", e)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "getRunningTasks failed", e)
            }
        }
        
        android.util.Log.d(TAG, "getRecentTasks returning ${tasks.size} tasks")
        return tasks.values.toList()
    }
    
    private fun processTaskListReflection(tasks: LinkedHashMap<Int, RecentTask>, taskList: List<*>, myPackage: String, excludeExternal: Boolean = true, excludeSelf: Boolean = true) {
        for (taskInfo in taskList) {
            try {
                if (taskInfo == null) continue
                
                val taskClass = taskInfo.javaClass
                android.util.Log.d(TAG, "Task class: ${taskClass.name}")
                
                // Log all available fields (including inherited from TaskInfo)
                val allFields = mutableListOf<String>()
                var currentClass: Class<*>? = taskClass
                while (currentClass != null && currentClass != Any::class.java) {
                    val className = currentClass.simpleName
                    for (field in currentClass.declaredFields) {
                        allFields.add("$className.${field.name}")
                    }
                    currentClass = currentClass.superclass
                }
                android.util.Log.d(TAG, "All fields (including inherited): $allFields")
                
                val pkgName: String?
                val taskId: Int
                val baseIntent: Intent?
                
                // Get taskId - Android 14 uses taskId instead of id
                taskId = try {
                    taskClass.getField("taskId").getInt(taskInfo)
                } catch (e: Exception) {
                    try {
                        taskClass.getField("persistentId").getInt(taskInfo)
                    } catch (e2: Exception) {
                        try {
                            taskClass.getField("id").getInt(taskInfo)
                        } catch (e3: Exception) {
                            -1
                        }
                    }
                }
                
                // Get baseIntent (through inheritance from TaskInfo)
                baseIntent = try {
                    taskClass.getField("baseIntent").get(taskInfo) as? Intent
                } catch (e: Exception) {
                    android.util.Log.d(TAG, "No baseIntent field: ${e.message}")
                    null
                }
                
                // Try to get baseActivity directly if available
                val baseActivity = try {
                    taskClass.getField("baseActivity").get(taskInfo) as? android.content.ComponentName
                } catch (e: Exception) {
                    android.util.Log.d(TAG, "No baseActivity field: ${e.message}")
                    null
                }
                
                // Try to get topActivity (for filtering dead tasks)
                val topActivity = try {
                    taskClass.getField("topActivity").get(taskInfo) as? android.content.ComponentName
                } catch (e: Exception) {
                    android.util.Log.d(TAG, "No topActivity field: ${e.message}")
                    null
                }
                
                // Determine package name from available sources
                pkgName = baseIntent?.component?.packageName
                    ?: baseActivity?.packageName
                    ?: topActivity?.packageName
                
                // Try to get TaskDescription for custom task label and icon
                var taskLabel: String? = null
                var customIcon: android.graphics.drawable.Drawable? = null
                try {
                    val taskDescField = taskClass.getField("taskDescription")
                    val taskDescription = taskDescField.get(taskInfo)
                    if (taskDescription != null) {
                        val getLabelMethod = taskDescription.javaClass.getMethod("getLabel")
                        val labelResult = getLabelMethod.invoke(taskDescription)
                        taskLabel = labelResult?.toString()
                        android.util.Log.d(TAG, "TaskDescription label: $taskLabel")
                        
                        // Try to get custom icon from TaskDescription
                        try {
                            val getIconMethod = taskDescription.javaClass.getMethod("getInMemoryIcon")
                            val iconObj = getIconMethod.invoke(taskDescription)
                            if (iconObj != null && iconObj is android.graphics.drawable.Drawable) {
                                customIcon = iconObj
                                android.util.Log.d(TAG, "Got custom icon from TaskDescription")
                            }
                        } catch (e: Exception) {
                            android.util.Log.d(TAG, "No custom icon in TaskDescription: ${e.message}")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.d(TAG, "No taskDescription or label: ${e.message}")
                }
                
                // Try to get activity-specific icon from ActivityInfo
                val component = baseIntent?.component
                if (customIcon == null && component != null && pkgName != null) {
                    try {
                        // Get ActivityInfo to check icon resource ID
                        val activityInfo = packageManager.getActivityInfo(component, 0)
                        val appInfo = packageManager.getApplicationInfo(pkgName, 0)
                        
                        // Compare icon resource IDs
                        val activityIconRes = activityInfo.icon
                        val appIconRes = appInfo.icon
                        
                        android.util.Log.d(TAG, "Icon comparison: activityIconRes=$activityIconRes, appIconRes=$appIconRes")
                        
                        // If activity has different icon resource, use it
                        if (activityIconRes != 0 && activityIconRes != appIconRes) {
                            val drawable = packageManager.getDrawable(pkgName, activityIconRes, appInfo)
                            android.util.Log.d(TAG, "getDrawable result: ${drawable != null}")
                            customIcon = drawable
                            android.util.Log.d(TAG, "Activity has different icon resource: $component (res=$activityIconRes)")
                        } else {
                            android.util.Log.d(TAG, "Activity uses same icon as app: $component - will use banner")
                        }
                    } catch (e: Exception) {
                        android.util.Log.d(TAG, "Icon comparison failed: ${e.message}")
                    }
                }
                
                android.util.Log.d(TAG, "Resolved: pkgName=$pkgName, taskId=$taskId, baseIntent=$baseIntent, baseActivity=$baseActivity, topActivity=$topActivity")
                
                if (pkgName == null) continue
                if (excludeSelf && pkgName == myPackage) continue
                if (excludeExternal && pkgName == sharedPreferences.externalLauncherPkg) continue
                if (taskId <= 0) continue
                
                // Use three-layer fallback to resolve task title
                val displayName = resolveTaskTitle(taskInfo, pkgName)
                
                android.util.Log.d(TAG, "Adding (refl): $pkgName, taskId=$taskId, displayName=$displayName (taskLabel=$taskLabel, hasCustomIcon=${customIcon != null})")
                tasks[taskId] = RecentTask(pkgName, taskId, displayName, taskLabel, customIcon, null, baseIntent)
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Failed to process task via reflection: ${e.message}", e)
            }
        }
    }
    
    private fun debugDumpFields(obj: Any) {
        android.util.Log.d("DEBUG_PARSER", "=== Dumping Fields for ${obj.javaClass.name} ===")
        val fields = obj.javaClass.fields
        for (field in fields) {
            try {
                val value = field.get(obj)
                android.util.Log.d("DEBUG_PARSER", "Field: ${field.name} | Type: ${field.type.simpleName} | Value: $value")
            } catch (e: Exception) {
                android.util.Log.d("DEBUG_PARSER", "Field: ${field.name} | Error reading value")
            }
        }
        android.util.Log.d("DEBUG_PARSER", "Superclass: ${obj.javaClass.superclass?.name}")
    }
    
    private fun resolveTaskTitle(taskObj: Any, packageName: String): String {
        // 1. Попытка через TaskDescription (динамическое имя)
        try {
            val tdField = taskObj.javaClass.getField("taskDescription")
            val td = tdField.get(taskObj)
            if (td != null) {
                val label = td.javaClass.getMethod("getLabel").invoke(td)?.toString()
                if (!label.isNullOrEmpty()) {
                    android.util.Log.d(TAG, "Layer 1 (TaskDescription): $label")
                    return label
                }
            }
        } catch (e: Exception) {
            android.util.Log.d(TAG, "Layer 1 failed: ${e.message}")
        }
        
        // 2. Попытка получить Activity Label через ComponentName
        try {
            val baseIntentField = taskObj.javaClass.getField("baseIntent")
            val baseIntent = baseIntentField.get(taskObj) as? Intent
            val componentName = baseIntent?.component
            
            if (componentName != null) {
                val className = componentName.className
                android.util.Log.d(TAG, "Activity className: $className")
                
                // Проверяем nonLocalizedLabel и loadLabel
                try {
                    val activityInfo = packageManager.getActivityInfo(componentName, 0)
                    val nonLocalizedLabel = activityInfo.nonLocalizedLabel?.toString()
                    if (!nonLocalizedLabel.isNullOrEmpty() && nonLocalizedLabel != packageName) {
                        android.util.Log.d(TAG, "Layer 2a (nonLocalizedLabel): $nonLocalizedLabel")
                        return nonLocalizedLabel
                    }
                    
                    val activityLabel = activityInfo.loadLabel(packageManager).toString()
                    if (activityLabel.isNotEmpty() && activityLabel != packageName) {
                        android.util.Log.d(TAG, "Layer 2b (loadLabel): $activityLabel")
                        return activityLabel
                    }
                } catch (e: Exception) {
                    android.util.Log.d(TAG, "ActivityInfo lookup failed: ${e.message}")
                }
                
                // 3. Попытка извлечь имя из className (heuristic)
                val derivedName = deriveNameFromClassName(className, packageName)
                if (derivedName != null) {
                    android.util.Log.d(TAG, "Layer 3 (derived): $derivedName")
                    return derivedName
                }
            }
        } catch (e: Exception) {
            android.util.Log.d(TAG, "Layer 2 failed: ${e.message}")
        }
        
        // 4. Финальный Fallback: Имя приложения
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            val appLabel = packageManager.getApplicationLabel(appInfo).toString()
            android.util.Log.d(TAG, "Layer 4 (AppInfo): $appLabel")
            appLabel
        } catch (e: Exception) {
            android.util.Log.d(TAG, "Layer 4 failed: ${e.message}")
            packageName
        }
    }
    
    private fun deriveNameFromClassName(className: String, packageName: String): String? {
        // Извлекаем короткое имя класса (без пакета и суффикса Activity)
        val simpleName = className.substringAfterLast('.')
            .replace("Activity", "")
            .replace("activity", "")
        
        if (simpleName.isEmpty()) return null
        
        // Пытаемся перевести или использовать понятные имена
        val appName = try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            null
        }
        
        // Определяем тип activity по имени
        return when {
            simpleName.contains("Editor", ignoreCase = true) ||
            simpleName.contains("TextEdit", ignoreCase = true) ||
            simpleName.contains("Writer", ignoreCase = true) ||
            simpleName.contains("Note", ignoreCase = true) -> "$appName блокнот"
            
            simpleName.contains("Explorer", ignoreCase = true) ||
            simpleName.contains("Files", ignoreCase = true) ||
            simpleName.contains("FileMan", ignoreCase = true) ||
            simpleName.contains("Browser", ignoreCase = true) -> "$appName файлы"
            
            simpleName.contains("Player", ignoreCase = true) ||
            simpleName.contains("Video", ignoreCase = true) ||
            simpleName.contains("Media", ignoreCase = true) -> "$appName плеер"
            
            simpleName.contains("Settings", ignoreCase = true) -> "$appName настройки"
            
            simpleName.contains("Main", ignoreCase = true) -> appName
            
            else -> {
                // Используем простое имя activity если оно информативное
                val cleanName = simpleName.replaceFirstChar { it.uppercaseChar() }
                if (cleanName.length > 3 && cleanName != packageName.substringAfterLast('.')) {
                    "$appName $cleanName"
                } else null
            }
        }
    }
    
    /**
     * Task preview via the system's task snapshot cache.
     *
     * RAM-only by design (this ROM never persists task snapshots to disk):
     * 1. IActivityTaskManager.getTaskSnapshotManager() → ITaskSnapshotManager
     *    (platform-signed launcher, manifest holds MANAGE_ACTIVITY_TASKS + READ_FRAME_BUFFER).
     * 2. getTaskSnapshot(taskId, latestCaptureTime = 0, RESOLUTION_ANY) — reads the
     *    WindowManager in-memory snapshot cache first; the disk fallback is a no-op here
     *    because recording never persists (ROM patch in TaskSnapshotController).
     * 3. Fallback for currently visible tasks: takeTaskSnapshot(updateCache=false) takes a
     *    fresh real-content capture that is never stored in the SnapshotPersister.
     *
     * Bitmaps reference the shared gralloc buffers via TaskSnapshot#wrapToBitmap() — no pixel
     * copy in this process. Returns null → the caller keeps the icon-only card.
     */
    private fun getTaskSnapshot(taskId: Int): Bitmap? {
        taskSnapshots[taskId]?.let { return it }

        val snapshotManager = try {
            val serviceManagerClass = Class.forName("android.os.ServiceManager")
            val getServiceMethod = serviceManagerClass.getMethod("getService", String::class.java)
            val service = getServiceMethod.invoke(null, "activity_task")

            val stubClass = Class.forName("android.app.IActivityTaskManager\$Stub")
            val asInterfaceMethod = stubClass.getMethod("asInterface", Class.forName("android.os.IBinder"))
            val iatm = asInterfaceMethod.invoke(null, service) ?: return null

            val getManagerMethod = iatm.javaClass.getMethod("getTaskSnapshotManager")
            getManagerMethod.invoke(iatm)
        } catch (e: Exception) {
            android.util.Log.w(TAG, "getTaskSnapshot: no ITaskSnapshotManager: ${e.message}")
            return null
        }
        if (snapshotManager == null) {
            android.util.Log.w(TAG, "getTaskSnapshot: snapshot manager binder is null")
            return null
        }

        fun snapshotToBitmap(snapshot: Any?): Bitmap? = try {
            snapshot?.javaClass?.getMethod("wrapToBitmap")?.invoke(snapshot) as? Bitmap
        } catch (e: Exception) {
            android.util.Log.w(TAG, "wrapToBitmap failed: ${e.message}")
            null
        }

        try {
            val getMethod = snapshotManager.javaClass.getMethod(
                "getTaskSnapshot",
                Int::class.javaPrimitiveType,
                Long::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            // latestCaptureTime = 0 → always return the cached copy; 3 = RESOLUTION_ANY
            val snapshot = getMethod.invoke(snapshotManager, taskId, 0L, 3)
            val bitmap = snapshotToBitmap(snapshot)
            if (bitmap != null) {
                taskSnapshots[taskId] = bitmap
                return bitmap
            }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "getTaskSnapshot (cache) failed: ${e.message}")
        }

        // Fresh capture. updateCache = false → real content, never put into the persister.
        // Works only while the task is still visible on screen.
        try {
            val takeMethod = snapshotManager.javaClass.getMethod(
                "takeTaskSnapshot",
                Int::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType
            )
            val snapshot = takeMethod.invoke(snapshotManager, taskId, false, false)
            val bitmap = snapshotToBitmap(snapshot)
            if (bitmap != null) {
                taskSnapshots[taskId] = bitmap
            }
            return bitmap
        } catch (e: SecurityException) {
            android.util.Log.w(TAG, "takeTaskSnapshot denied (platform signature build?): ${e.message}")
        } catch (e: Exception) {
            android.util.Log.w(TAG, "takeTaskSnapshot failed: ${e.message}")
        }

        return null
    }

    private fun focusRecentPosition(position: Int) {
        val grid = recentGrid ?: return
        val view = grid.findViewHolderForAdapterPosition(position)?.itemView
            ?: grid.getChildAt(position)
        view?.requestFocus()
    }

    private fun findRecentTask(taskId: Int): RecentTask? =
        recentAdapter?.taskById(taskId)

    private fun launchTask(taskId: Int) {
        try {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val options = ActivityOptions.makeBasic()
            options.setPendingIntentBackgroundActivityStartMode(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            )
            activityManager.moveTaskToFront(
                taskId,
                ActivityManager.MOVE_TASK_WITH_HOME,
                options.toBundle()
            )
            root_post_hide()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to move task to front", e)
            try {
                findRecentTask(taskId)?.baseIntent?.let { intent ->
                    intent.addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or
                        Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY
                    )
                    startActivity(intent)
                }
            } catch (e2: Exception) {
                android.util.Log.e(TAG, "Failed to start activity", e2)
            }
            hideOverlay()
        }
    }

    private fun root_post_hide() {
        recentGrid?.postDelayed({ hideOverlay() }, 200)
            ?: hideOverlay()
    }

    private fun configureRecentCard(card: AppCard, task: RecentTask) {
        val taskId = task.taskId
        card.tag = taskId

        val appInfoForCard = try {
            if (packageManager.getApplicationInfo(task.packageName, 0) != null) {
                val intent = packageManager.getLaunchIntentForPackage(task.packageName)
                    ?: task.baseIntent
                val resolveInfo = intent?.let {
                    packageManager.resolveActivity(it, PackageManager.MATCH_DEFAULT_ONLY)
                }
                if (resolveInfo != null) LeanbackAppInfo(resolveInfo, card.context) else null
            } else null
        } catch (e: Exception) {
            null
        }
        appInfoForCard?.let { card.setCardInfo(it) }

        if (task.customIcon != null) {
            card.setCustomIcon(task.customIcon)
        }
        if (!task.displayName.isNullOrEmpty()) {
            card.setCustomTitle(task.displayName)
        }

        val density = resources.displayMetrics.density
        val cardHeightPx = resources.displayMetrics.heightPixels *
            sharedPreferences.recentAppsCardSize / 100f
        card.setCardScale(cardHeightPx / (100f * density))

        card.setPreview(if (sharedPreferences.recentAppsShowThumbnails) getTaskSnapshot(taskId) else null)

        val descText = if (!task.taskLabel.isNullOrEmpty()) task.taskLabel else task.displayName
        card.contentDescription = descText ?: ""
    }

    private inner class RecentCardsAdapter(
        private val tasks: MutableList<RecentTask>,
        private val themed: Context,
    ) : RecyclerView.Adapter<RecentCardsAdapter.VH>() {

        inner class VH(val card: AppCard) : RecyclerView.ViewHolder(card)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val density = parent.resources.displayMetrics.density
            val card = AppCard(themed).apply {
                isFocusable = true
                isClickable = true
                isFocusableInTouchMode = true
                clipChildren = false
                clipToPadding = false
                setPadding(
                    (8 * density).toInt(), (16 * density).toInt(),
                    (8 * density).toInt(), (16 * density).toInt()
                )
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = (20 * density).toInt() }
                setOnClickListener { launchTask(tag as Int) }
                setOnKeyListener { v, keyCode, event ->
                    if (keyCode == KeyEvent.KEYCODE_DPAD_UP && event.action == KeyEvent.ACTION_DOWN) {
                        removeTaskWithAnimation(v, v.tag as Int)
                        true
                    } else {
                        false
                    }
                }
            }
            return VH(card)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val task = tasks[position]
            if (holder.card.tag != task.taskId) {
                configureRecentCard(holder.card, task)
            }
        }

        override fun getItemCount(): Int = tasks.size

        fun positionOf(taskId: Int): Int = tasks.indexOfFirst { it.taskId == taskId }

        fun taskById(taskId: Int): RecentTask? = tasks.firstOrNull { it.taskId == taskId }

        fun taskIds(): MutableList<Int> = tasks.mapTo(mutableListOf()) { it.taskId }

        fun removeAt(position: Int) {
            tasks.removeAt(position)
            notifyItemRemoved(position)
        }
    }
}
