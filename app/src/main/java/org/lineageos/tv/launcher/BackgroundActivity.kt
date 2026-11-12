/*
 * SPDX-FileCopyrightText: 2024-2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.tv.launcher

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.PreferenceManager
import org.lineageos.tv.launcher.ext.backgroundImageUri
import org.lineageos.tv.launcher.ext.backgroundBlur
import org.lineageos.tv.launcher.ext.backgroundBlurPrebaked
import org.lineageos.tv.launcher.ext.backgroundAutoPerf
import org.lineageos.tv.launcher.ext.backgroundAutoTier
import org.lineageos.tv.launcher.ext.backgroundKbLowFps
import org.lineageos.tv.launcher.ext.backgroundKbPause
import org.lineageos.tv.launcher.ext.backgroundType
import java.io.File

class BackgroundActivity : ModalActivity(R.layout.activity_background) {

    private val prefs by lazy { PreferenceManager.getDefaultSharedPreferences(this) }
    
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val file = copyImageToInternalStorage(it)
            if (file != null) {
                prefs.backgroundImageUri = file.absolutePath
                findViewById<TextView>(R.id.select_image_button)?.text = getString(R.string.selected_image)
            }
        }
    }

    private fun copyImageToInternalStorage(uri: Uri): File? {
        return try {
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            // Unique file name per pick: SharedPreferences would not notify listeners
            // when the stored path string stays identical across picks.
            val file = File(filesDir, "background_image_${System.currentTimeMillis()}.jpg")
            file.outputStream().use { output ->
                inputStream.copyTo(output)
            }
            inputStream.close()
            filesDir.listFiles()?.forEach { old ->
                if (old.name.startsWith("background_image_") && old != file) old.delete()
            }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupBackgroundSettings()
    }

    private fun setupBackgroundSettings() {
        val radioGroup = findViewById<RadioGroup>(R.id.background_type_group)
        val selectImageButton = findViewById<TextView>(R.id.select_image_button)
        val blurSection = findViewById<View>(R.id.dynamic_blur_section)
        val perfSection = findViewById<View>(R.id.perf_section)
        val blurSlider = findViewById<com.google.android.material.slider.Slider>(
            R.id.background_blur_slider
        )
        val blurValue = findViewById<TextView>(R.id.background_blur_value)
        val autoPerfSwitch = findViewById<com.google.android.material.materialswitch.MaterialSwitch>(
            R.id.auto_perf_switch
        )
        val kbLowFpsSwitch = findViewById<com.google.android.material.materialswitch.MaterialSwitch>(
            R.id.kb_low_fps_switch
        )
        val kbPauseSwitch = findViewById<com.google.android.material.materialswitch.MaterialSwitch>(
            R.id.kb_pause_switch
        )
        val prebakeSwitch = findViewById<com.google.android.material.materialswitch.MaterialSwitch>(
            R.id.prebake_blur_switch
        )
        val autoPerfState = findViewById<TextView>(R.id.auto_perf_state)
        autoPerfState?.text = when (prefs.backgroundAutoTier) {
            1 -> getString(R.string.background_auto_perf_t1)
            2 -> getString(R.string.background_auto_perf_t2)
            3 -> getString(R.string.background_auto_perf_t3)
            else -> getString(R.string.background_auto_perf_t0)
        }

        val currentType = prefs.backgroundType
        when (currentType) {
            0 -> findViewById<RadioButton>(R.id.background_default)?.isChecked = true
            1 -> findViewById<RadioButton>(R.id.background_default)?.isChecked = true
            2 -> findViewById<RadioButton>(R.id.background_image)?.isChecked = true
            3 -> findViewById<RadioButton>(R.id.background_dynamic)?.isChecked = true
        }

        updateVisibility(currentType, selectImageButton, blurSection, perfSection)

        blurSlider?.value = prefs.backgroundBlur.toFloat()
        blurValue?.text = blurSlider?.value?.toInt()?.toString()
        blurSlider?.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                prefs.backgroundBlur = value.toInt()
            }
            blurValue?.text = value.toInt().toString()
        }

        autoPerfSwitch?.isChecked = prefs.backgroundAutoPerf
        kbLowFpsSwitch?.isChecked = prefs.backgroundKbLowFps
        kbPauseSwitch?.isChecked = prefs.backgroundKbPause
        prebakeSwitch?.isChecked = prefs.backgroundBlurPrebaked
        applyManualAvailability(
            prefs.backgroundAutoPerf, blurSlider, blurValue, kbLowFpsSwitch, kbPauseSwitch,
            prebakeSwitch, autoPerfState
        )

        autoPerfSwitch?.setOnCheckedChangeListener { _, isChecked ->
            prefs.backgroundAutoPerf = isChecked
            // Re-enabling auto starts learning from scratch; restarts keep the
            // previously learned tier.
            if (isChecked) {
                prefs.backgroundAutoTier = 0
                autoPerfState?.text = getString(R.string.background_auto_perf_t0)
            }
            applyManualAvailability(
                isChecked, blurSlider, blurValue, kbLowFpsSwitch, kbPauseSwitch,
                prebakeSwitch, autoPerfState
            )
        }
        prebakeSwitch?.setOnCheckedChangeListener { _, isChecked ->
            prefs.backgroundBlurPrebaked = isChecked
        }
        kbLowFpsSwitch?.setOnCheckedChangeListener { _, isChecked ->
            prefs.backgroundKbLowFps = isChecked
        }
        kbPauseSwitch?.setOnCheckedChangeListener { _, isChecked ->
            prefs.backgroundKbPause = isChecked
        }

        radioGroup?.setOnCheckedChangeListener { _, checkedId ->
            val type = when (checkedId) {
                R.id.background_default -> 0
                R.id.background_image -> 2
                R.id.background_dynamic -> 3
                else -> 0
            }
            prefs.backgroundType = type
            updateVisibility(type, selectImageButton, blurSection, perfSection)
        }

        selectImageButton?.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }
    }

    private fun updateVisibility(
        type: Int,
        selectImageButton: TextView?,
        blurSection: View?,
        perfSection: View?
    ) {
        selectImageButton?.visibility = if (type == 2) View.VISIBLE else View.GONE
        blurSection?.visibility = if (type == 3) View.VISIBLE else View.GONE
        perfSection?.visibility = if (type == 3) View.VISIBLE else View.GONE
        if (type == 2) {
            val hasImage = !prefs.backgroundImageUri.isNullOrEmpty()
            selectImageButton?.text = if (hasImage) getString(R.string.selected_image) else getString(R.string.select_image)
        }
    }

    // Auto mode owns blur and the Ken Burns toggles: manual controls go dead.
    private fun applyManualAvailability(
        auto: Boolean,
        blurSlider: com.google.android.material.slider.Slider?,
        blurValue: TextView?,
        kbLowFpsSwitch: com.google.android.material.materialswitch.MaterialSwitch?,
        kbPauseSwitch: com.google.android.material.materialswitch.MaterialSwitch?,
        prebakeSwitch: com.google.android.material.materialswitch.MaterialSwitch?,
        autoPerfState: TextView?
    ) {
        val alpha = if (auto) 0.4f else 1f
        blurSlider?.isEnabled = !auto
        blurSlider?.alpha = alpha
        blurValue?.alpha = alpha
        kbLowFpsSwitch?.isEnabled = !auto
        kbLowFpsSwitch?.alpha = alpha
        kbPauseSwitch?.isEnabled = !auto
        kbPauseSwitch?.alpha = alpha
        prebakeSwitch?.isEnabled = !auto
        prebakeSwitch?.alpha = alpha
        autoPerfState?.visibility = if (auto) View.VISIBLE else View.GONE
    }
}
