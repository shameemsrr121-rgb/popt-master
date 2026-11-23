package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * POPT Official Brand Logo Composable
 * Matches the bold white POPT typography on dark background.
 * Proportioned to fit neatly with zero font padding in compact mobile layouts.
 */
@Composable
fun PoptLogoIcon(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    backgroundColor: Color = Color.Black,
    showText: Boolean = true,
    cornerRadius: Dp = (size.value * 0.2f).dp
) {
    Surface(
        modifier = modifier
            .height(size)
            .widthIn(min = (size.value * 1.38f).dp),
        shape = RoundedCornerShape(cornerRadius),
        color = backgroundColor
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            if (showText) {
                val fontSize = (size.value * 0.35f).coerceIn(10f, 18f).sp
                Text(
                    text = "POPT",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.SansSerif,
                    fontSize = fontSize,
                    letterSpacing = 0.5.sp,
                    style = TextStyle(
                        platformStyle = PlatformTextStyle(
                            includeFontPadding = false
                        )
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun PoptLogoBadge(
    modifier: Modifier = Modifier,
    badgeText: String = "POPT",
    containerColor: Color = Color.Black,
    contentColor: Color = Color.White
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = containerColor
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        ) {
            Text(
                text = badgeText,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.SansSerif,
                fontSize = 11.sp,
                letterSpacing = 0.5.sp,
                color = contentColor,
                style = TextStyle(
                    platformStyle = PlatformTextStyle(
                        includeFontPadding = false
                    )
                ),
                maxLines = 1
            )
        }
    }
}


