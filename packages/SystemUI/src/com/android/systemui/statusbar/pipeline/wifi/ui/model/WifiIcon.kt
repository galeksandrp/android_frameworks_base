/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.pipeline.wifi.ui.model

import android.annotation.DrawableRes
import android.content.Context
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import com.android.settingslib.AccessibilityContentDescriptions.WIFI_CONNECTION_STRENGTH
import com.android.settingslib.AccessibilityContentDescriptions.WIFI_NO_CONNECTION
import com.android.systemui.R
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.log.table.Diffable
import com.android.systemui.log.table.TableRowLogger
import com.android.systemui.statusbar.connectivity.WifiIcons
import com.android.systemui.statusbar.pipeline.wifi.shared.model.WifiNetworkModel

/** Represents the various states of the wifi icon. */
sealed interface WifiIcon : Diffable<WifiIcon> {
    /** Represents a wifi icon that should be hidden (not visible). */
    object Hidden : WifiIcon {
        override fun toString() = "hidden"
    }

    /**
     * Represents a visible wifi icon that uses [res] as its image and [contentDescription] as its
     * description.
     */
    class Visible(
        @DrawableRes res: Int,
        val contentDescription: ContentDescription.Loaded,
    ) : WifiIcon {
        val icon = Icon.Resource(res, contentDescription)

        override fun toString() = contentDescription.description.toString()
    }

    override fun logDiffs(prevVal: WifiIcon, row: TableRowLogger) {
        if (prevVal.toString() != toString()) {
            row.logChange(COL_ICON, toString())
        }
    }

    override fun logFull(row: TableRowLogger) {
        row.logChange(COL_ICON, toString())
    }

    companion object {
        @StringRes
        @VisibleForTesting
        internal val NO_INTERNET = R.string.data_connection_no_internet

        /** Mapping from a [WifiNetworkModel] to the appropriate [WifiIcon] */
        fun fromModel(model: WifiNetworkModel, context: Context): WifiIcon =
            when (model) {
                is WifiNetworkModel.Unavailable -> Hidden
                is WifiNetworkModel.Invalid -> Hidden
                is WifiNetworkModel.CarrierMerged -> Hidden
                is WifiNetworkModel.Inactive ->
                    Visible(
                        res = WifiIcons.WIFI_NO_NETWORK,
                        ContentDescription.Loaded(
                            "${context.getString(WIFI_NO_CONNECTION)},${context.getString(
                                NO_INTERNET
                            )}"
                        )
                    )
                is WifiNetworkModel.Active -> {
                    val levelDesc = context.getString(WIFI_CONNECTION_STRENGTH[model.level])
                    when {
                        model.isValidated ->
                            Visible(
                                WifiIcons.WIFI_FULL_ICONS[model.level],
                                ContentDescription.Loaded(levelDesc),
                            )
                        else ->
                            Visible(
                                WifiIcons.WIFI_NO_INTERNET_ICONS[model.level],
                                ContentDescription.Loaded(
                                    "$levelDesc,${context.getString(NO_INTERNET)}"
                                ),
                            )
                    }
                }
            }
    }
}

private const val COL_ICON = "icon"
