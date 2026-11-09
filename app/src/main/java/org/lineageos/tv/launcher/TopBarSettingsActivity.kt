/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.tv.launcher

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.preference.PreferenceManager
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import org.lineageos.tv.launcher.ext.clockTextSize
import org.lineageos.tv.launcher.ext.showAssistantButtons
import org.lineageos.tv.launcher.ext.showClock
import org.lineageos.tv.launcher.ext.showVpnButton
import org.lineageos.tv.launcher.ext.topBarAutoHide

class TopBarSettingsActivity : ModalActivity(R.layout.activity_top_bar_settings) {

    private val prefs by lazy { PreferenceManager.getDefaultSharedPreferences(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupTopBarSettings()
    }

    private fun setupTopBarSettings() {
        val searchButtonsSwitch = findViewById<MaterialSwitch>(R.id.search_buttons_switch)!!
        val vpnSwitch = findViewById<MaterialSwitch>(R.id.vpn_switch)!!
        val clockSwitch = findViewById<MaterialSwitch>(R.id.clock_switch)!!
        val autoHideSwitch = findViewById<MaterialSwitch>(R.id.auto_hide_switch)!!

        searchButtonsSwitch.isChecked = prefs.showAssistantButtons
        searchButtonsSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.showAssistantButtons = isChecked
        }

        vpnSwitch.isChecked = prefs.showVpnButton
        vpnSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.showVpnButton = isChecked
        }

        autoHideSwitch.isChecked = prefs.topBarAutoHide
        autoHideSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.topBarAutoHide = isChecked
        }

        val clockSizeSection = findViewById<View>(R.id.clock_size_section)!!
        val clockSizeSlider = findViewById<Slider>(R.id.clock_size_slider)!!
        val clockSizeValue = findViewById<TextView>(R.id.clock_size_value)!!

        clockSwitch.isChecked = prefs.showClock
        updateClockSizeVisibility(prefs.showClock)

        clockSizeSlider.value = prefs.clockTextSize.coerceIn(24, 72).toFloat()
        clockSizeValue.text = getString(R.string.clock_size_sp, clockSizeSlider.value.toInt())

        clockSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.showClock = isChecked
            updateClockSizeVisibility(isChecked)
        }

        clockSizeSlider.addOnChangeListener { _, value, fromUser ->
            val intValue = value.toInt()
            clockSizeValue.text = getString(R.string.clock_size_sp, intValue)
            if (fromUser) {
                prefs.clockTextSize = intValue
            }
        }
    }

    private fun updateClockSizeVisibility(showClock: Boolean) {
        findViewById<View>(R.id.clock_size_section)!!.visibility =
            if (showClock) View.VISIBLE else View.GONE
    }
}
