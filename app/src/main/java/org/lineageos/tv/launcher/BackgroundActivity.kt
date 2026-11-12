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
        val blurSlider = findViewById<com.google.android.material.slider.Slider>(
            R.id.background_blur_slider
        )
        val blurValue = findViewById<TextView>(R.id.background_blur_value)

        val currentType = prefs.backgroundType
        when (currentType) {
            0 -> findViewById<RadioButton>(R.id.background_default)?.isChecked = true
            1 -> findViewById<RadioButton>(R.id.background_default)?.isChecked = true
            2 -> findViewById<RadioButton>(R.id.background_image)?.isChecked = true
            3 -> findViewById<RadioButton>(R.id.background_dynamic)?.isChecked = true
        }

        updateVisibility(currentType, selectImageButton, blurSection)

        blurSlider?.value = prefs.backgroundBlur.toFloat()
        blurValue?.text = blurSlider?.value?.toInt()?.toString()
        blurSlider?.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                prefs.backgroundBlur = value.toInt()
            }
            blurValue?.text = value.toInt().toString()
        }

        radioGroup?.setOnCheckedChangeListener { _, checkedId ->
            val type = when (checkedId) {
                R.id.background_default -> 0
                R.id.background_image -> 2
                R.id.background_dynamic -> 3
                else -> 0
            }
            prefs.backgroundType = type
            updateVisibility(type, selectImageButton, blurSection)
        }

        selectImageButton?.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }
    }

    private fun updateVisibility(
        type: Int,
        selectImageButton: TextView?,
        blurSection: View?
    ) {
        selectImageButton?.visibility = if (type == 2) View.VISIBLE else View.GONE
        blurSection?.visibility = if (type == 3) View.VISIBLE else View.GONE
        if (type == 2) {
            val hasImage = !prefs.backgroundImageUri.isNullOrEmpty()
            selectImageButton?.text = if (hasImage) getString(R.string.selected_image) else getString(R.string.select_image)
        }
    }
}
