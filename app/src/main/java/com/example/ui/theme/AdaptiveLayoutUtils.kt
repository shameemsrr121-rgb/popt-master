package com.example.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Window Size Classification for Adaptive & Responsive Mobile Design.
 */
enum class WindowWidthClass {
    COMPACT_SMALL,   // < 360dp (Narrow / Small legacy phones)
    COMPACT_REGULAR, // 360dp - 599dp (Standard modern mobile phones)
    MEDIUM,          // 600dp - 839dp (Foldables, landscape phones, small tablets)
    EXPANDED         // >= 840dp (Large tablets, Desktop / DeX)
}

data class WindowSizeInfo(
    val widthClass: WindowWidthClass,
    val screenWidthDp: Dp,
    val screenHeightDp: Dp,
    val isSmallPhone: Boolean,
    val isMediumOrExpanded: Boolean,
    val isLandscape: Boolean,
    val cardGridColumns: Int,
    val poStatusColumns: Int,
    val defaultPadding: Dp,
    val defaultSpacing: Dp,
    val headerLogoSize: Dp,
    val maxContentWidth: Dp
)

/**
 * Calculates adaptive window size info based on the current configuration.
 */
@Composable
fun rememberWindowSizeInfo(): WindowSizeInfo {
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.dp
    val screenHeightDp = configuration.screenHeightDp.dp
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp

    val widthClass = when {
        configuration.screenWidthDp < 360 -> WindowWidthClass.COMPACT_SMALL
        configuration.screenWidthDp < 600 -> WindowWidthClass.COMPACT_REGULAR
        configuration.screenWidthDp < 840 -> WindowWidthClass.MEDIUM
        else -> WindowWidthClass.EXPANDED
    }

    return remember(configuration.screenWidthDp, configuration.screenHeightDp, isLandscape) {
        val isSmall = widthClass == WindowWidthClass.COMPACT_SMALL
        val isWide = widthClass == WindowWidthClass.MEDIUM || widthClass == WindowWidthClass.EXPANDED || (isLandscape && configuration.screenWidthDp >= 540)

        WindowSizeInfo(
            widthClass = widthClass,
            screenWidthDp = screenWidthDp,
            screenHeightDp = screenHeightDp,
            isSmallPhone = isSmall,
            isMediumOrExpanded = isWide,
            isLandscape = isLandscape,
            cardGridColumns = if (isWide) 2 else 1,
            poStatusColumns = if (isSmall) 2 else 4,
            defaultPadding = if (isSmall) 10.dp else if (isWide) 20.dp else 16.dp,
            defaultSpacing = if (isSmall) 8.dp else 12.dp,
            headerLogoSize = if (isSmall) 26.dp else 34.dp,
            maxContentWidth = if (isWide) 760.dp else Dp.Unspecified
        )
    }
}

/**
 * Minimum touch target modifier for accessible, comfortable UI interaction (>= 48dp).
 */
val MinimumTouchTargetModifier = Modifier
