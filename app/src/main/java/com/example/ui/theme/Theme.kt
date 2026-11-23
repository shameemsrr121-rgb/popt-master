package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.data.ThemeMode

fun getCustomColorScheme(
    isDark: Boolean,
    accentColorName: String
): ColorScheme {
    val primaryColor = when (accentColorName) {
        "EMERALD_GREEN" -> AccentGreen
        "ROYAL_INDIGO" -> AccentIndigo
        "SAPPHIRE_BLUE" -> AccentBlue
        "AMBER_GOLD" -> AccentAmber
        else -> AccentRed // GARDEN_RED
    }

    return if (isDark) {
        darkColorScheme(
            primary = when (accentColorName) {
                "EMERALD_GREEN" -> Color(0xFF66FFA6)
                "ROYAL_INDIGO" -> Color(0xFFB0B6FF)
                "SAPPHIRE_BLUE" -> Color(0xFF80D4FF)
                "AMBER_GOLD" -> Color(0xFFFFB74D)
                else -> Color(0xFFFFB4AB)
            },
            onPrimary = Color(0xFF380001),
            primaryContainer = when (accentColorName) {
                "EMERALD_GREEN" -> Color(0xFF005224)
                "ROYAL_INDIGO" -> Color(0xFF262C6A)
                "SAPPHIRE_BLUE" -> Color(0xFF004C6D)
                "AMBER_GOLD" -> Color(0xFF7A3600)
                else -> GardenRedDark
            },
            onPrimaryContainer = Color(0xFFFFDAD6),
            secondary = Color(0xFF53DF83),
            onSecondary = Color(0xFF003919),
            secondaryContainer = Color(0xFF005328),
            onSecondaryContainer = Color(0xFF71FC9D),
            tertiary = Color(0xFFC0C1FF),
            onTertiary = Color(0xFF1B1B75),
            tertiaryContainer = Color(0xFF33358E),
            onTertiaryContainer = Color(0xFFE0E0FF),
            background = GardenSurfaceDark,
            surface = GardenSurfaceDark,
            surfaceVariant = GardenSurfaceVariantDark,
            onBackground = GardenOnSurfaceDark,
            onSurface = GardenOnSurfaceDark,
            onSurfaceVariant = Color(0xFFC4C6D0),
            outline = Color(0xFF8E9099),
            outlineVariant = Color(0xFF44474E)
        )
    } else {
        lightColorScheme(
            primary = primaryColor,
            onPrimary = Color.White,
            primaryContainer = when (accentColorName) {
                "EMERALD_GREEN" -> GardenGreenContainer
                "ROYAL_INDIGO" -> GardenIndigoContainer
                "SAPPHIRE_BLUE" -> Color(0xFFD0E4FF)
                "AMBER_GOLD" -> Color(0xFFFFE082)
                else -> GardenRedContainer
            },
            onPrimaryContainer = when (accentColorName) {
                "EMERALD_GREEN" -> OnGardenGreenContainer
                "ROYAL_INDIGO" -> OnGardenIndigoContainer
                "SAPPHIRE_BLUE" -> Color(0xFF001D36)
                "AMBER_GOLD" -> Color(0xFF4E2600)
                else -> OnGardenRedContainer
            },
            secondary = GardenGreenSecondary,
            onSecondary = Color.White,
            secondaryContainer = GardenGreenContainer,
            onSecondaryContainer = OnGardenGreenContainer,
            tertiary = GardenIndigoTertiary,
            onTertiary = Color.White,
            tertiaryContainer = GardenIndigoContainer,
            onTertiaryContainer = OnGardenIndigoContainer,
            background = GardenSurfaceLight,
            surface = GardenSurfaceLight,
            surfaceVariant = GardenSurfaceVariantLight,
            onBackground = GardenOnSurfaceLight,
            onSurface = GardenOnSurfaceLight,
            onSurfaceVariant = Color(0xFF53453D),
            outline = GardenOutlineLight,
            outlineVariant = GardenOutlineVariantLight
        )
    }
}

@Composable
fun MyApplicationTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accentColorName: String = "GARDEN_RED",
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val isSystemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemDark
    }

    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> getCustomColorScheme(isDark, accentColorName)
    }

    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
