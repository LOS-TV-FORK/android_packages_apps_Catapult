/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.tv.launcher.flow

import android.content.Context
import androidx.core.os.bundleOf
import androidx.tvprovider.media.tv.PreviewChannel
import androidx.tvprovider.media.tv.TvContractCompat
import kotlinx.coroutines.flow.map
import org.lineageos.tv.launcher.ext.mapEachRow
import org.lineageos.tv.launcher.ext.queryFlow

@Suppress("RestrictedApi")
class PreviewChannelsFlow(private val context: Context) : QueryFlow<PreviewChannel> {
    override fun flowData() = context.contentResolver.queryFlow(
        TvContractCompat.Channels.CONTENT_URI,
        PreviewChannel.Columns.PROJECTION,
        bundleOf(),
    ).mapEachRow { it, _ ->
        // Only preview (suggestion) channels belong to the launcher; regular
        // TV input channels must not appear as rows.
        if (it.getString(PreviewChannel.Columns.COL_TYPE) == TvContractCompat.Channels.TYPE_PREVIEW) {
            PreviewChannel.fromCursor(it)
        } else {
            null
        }
    }.map { it.filterNotNull() }
}
