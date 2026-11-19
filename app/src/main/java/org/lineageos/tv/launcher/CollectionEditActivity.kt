/*
 * SPDX-FileCopyrightText: 2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.tv.launcher

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.leanback.widget.VerticalGridView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.lineageos.tv.launcher.adapter.ModifyFavoritesAdapter
import org.lineageos.tv.launcher.model.UserCollection
import org.lineageos.tv.launcher.utils.PermissionsGatedCallback
import org.lineageos.tv.launcher.viewmodels.CollectionEditViewModel

class CollectionEditActivity : ModalActivity(R.layout.activity_collection_edit) {
    private val model: CollectionEditViewModel by viewModels()

    private val header by lazy { findViewById<TextView>(R.id.collection_edit_header)!! }
    private val nameEdit by lazy { findViewById<EditText>(R.id.collection_name_edit)!! }
    private val appsGrid by lazy { findViewById<VerticalGridView>(R.id.collection_apps_grid)!! }
    private val gridSwitch by lazy { findViewById<MaterialSwitch>(R.id.collection_grid_switch)!! }
    private val gridColumnsSection by lazy {
        findViewById<View>(R.id.collection_grid_columns_section)!!
    }
    private val gridColumnsSlider by lazy {
        findViewById<Slider>(R.id.collection_grid_columns_slider)!!
    }
    private val gridColumnsValue by lazy {
        findViewById<TextView>(R.id.collection_grid_columns_value)!!
    }
    private val cardSizeCard by lazy { findViewById<View>(R.id.collection_card_size_card)!! }
    private val cardSizeSlider by lazy { findViewById<Slider>(R.id.collection_card_size_slider)!! }
    private val cardSizeValue by lazy { findViewById<TextView>(R.id.collection_card_size_value)!! }
    private val cornerRadiusSlider by lazy {
        findViewById<Slider>(R.id.collection_corner_radius_slider)!!
    }
    private val cornerRadiusValue by lazy {
        findViewById<TextView>(R.id.collection_corner_radius_value)!!
    }
    private val deleteButton by lazy { findViewById<MaterialButton>(R.id.collection_delete_button)!! }

    private val pickerAdapter by lazy { ModifyFavoritesAdapter() }

    // Suppresses write-backs to the model while the UI is being synced from it,
    // so the collection object itself is the single source of truth.
    private var binding = false

    private val permissionsGatedCallback = PermissionsGatedCallback(this) {
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.collection.collectLatest { collection ->
                    collection?.let { bindUi(it) }
                }
            }
        }
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.appsToSelected.collectLatest {
                    pickerAdapter.submitList(it)
                }
            }
        }
    }

    private fun bindUi(collection: UserCollection) {
        binding = true

        header.text = collection.title
        if (nameEdit.text.toString() != collection.title) {
            nameEdit.setText(collection.title)
        }

        gridSwitch.isChecked = collection.grid
        gridColumnsSection.isVisible = collection.grid
        gridColumnsSlider.value = collection.gridColumns.coerceIn(4, 10).toFloat()
        gridColumnsValue.text = collection.gridColumns.toString()

        // In grid mode the card size is derived from the column count, so the
        // size control is meaningless there.
        cardSizeCard.isVisible = !collection.grid
        cardSizeSlider.value = collection.cardSize.coerceIn(60, 140).toFloat()
        cardSizeValue.text = "${cardSizeSlider.value.toInt()}%"

        cornerRadiusSlider.value = collection.cornerRadius.coerceIn(0, 20).toFloat()
        cornerRadiusValue.text = "${cornerRadiusSlider.value.toInt()}dp"

        binding = false
    }

    private fun showKeyboard() {
        val imm = getSystemService(InputMethodManager::class.java) ?: return
        imm.showSoftInput(nameEdit, InputMethodManager.SHOW_IMPLICIT)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val collectionId = intent.getLongExtra(EXTRA_COLLECTION_ID, -1L)
        model.setCollection(collectionId)

        appsGrid.adapter = pickerAdapter
        pickerAdapter.onFavoriteChanged = { packageName, selected ->
            if (!binding) model.toggleApp(packageName, selected)
        }

        // On TV devices the automatic IME popup on focus is suppressed when a
        // physical keyboard is present, so request the keyboard explicitly.
        nameEdit.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) showKeyboard()
        }

        // The keyboard's "Done" action releases the field focus, like the OK key does.
        nameEdit.setOnEditorActionListener { view, _, _ ->
            view.clearFocus()
            getSystemService(InputMethodManager::class.java)
                ?.hideSoftInputFromWindow(view.windowToken, 0)
            true
        }

        nameEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!binding) model.setTitle(s?.toString().orEmpty())
            }
        })

        gridSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (!binding) {
                model.setGrid(isChecked)
                gridColumnsSection.isVisible = isChecked
                cardSizeCard.isVisible = !isChecked
            }
        }

        gridColumnsSlider.addOnChangeListener { _, value, fromUser ->
            val intValue = value.toInt()
            gridColumnsValue.text = intValue.toString()
            if (fromUser && !binding) model.setGridColumns(intValue)
        }

        cardSizeSlider.addOnChangeListener { _, value, fromUser ->
            val intValue = value.toInt()
            cardSizeValue.text = "$intValue%"
            if (fromUser && !binding) model.setCardSize(intValue)
        }

        cornerRadiusSlider.addOnChangeListener { _, value, fromUser ->
            val intValue = value.toInt()
            cornerRadiusValue.text = "${intValue}dp"
            if (fromUser && !binding) model.setCornerRadius(intValue)
        }

        deleteButton.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_collection)
                .setMessage(R.string.delete_collection_confirm)
                .setNegativeButton(android.R.string.cancel) { dialog, _ -> dialog.dismiss() }
                .setPositiveButton(R.string.delete_collection) { _, _ ->
                    model.delete()
                    finish()
                }
                .show()
        }

        permissionsGatedCallback.runAfterPermissionsCheck()
    }

    companion object {
        const val EXTRA_COLLECTION_ID = "collection_id"
    }
}
