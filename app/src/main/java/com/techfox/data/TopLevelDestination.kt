package com.techfox.data

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.ui.graphics.vector.ImageVector
import com.techfox.R

enum class TopLevelDestination(
    val route: String,
    val icon: ImageVector,
    @StringRes val labelRes: Int,
    val testTag: String
) {
    TRANSLATE(
        route = "translate_route",
        icon = Icons.Rounded.Translate,
        labelRes = R.string.nav_translate,
        testTag = "nav_item_translate"
    ),
    SUBTITLES(
        route = "subtitles_route",
        icon = Icons.Rounded.Subtitles,
        labelRes = R.string.nav_subtitles,
        testTag = "nav_item_subtitles"
    ),
    HISTORY(
        route = "history_route",
        icon = Icons.Rounded.History,
        labelRes = R.string.nav_history,
        testTag = "nav_item_history"
    ),
    SETTINGS(
        route = "settings_route",
        icon = Icons.Rounded.Settings,
        labelRes = R.string.nav_settings,
        testTag = "nav_item_settings"
    )
}
