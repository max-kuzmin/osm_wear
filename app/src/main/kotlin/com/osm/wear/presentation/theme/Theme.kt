package com.osm.wear.presentation.theme

import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ColorScheme

val AppColorScheme = ColorScheme(
    primary = PrimaryColor,
    onPrimary = OnPrimaryColor,
    background = BackgroundColor,
    onBackground = OnSurfaceColor,
    surfaceContainer = SurfaceContainerColor,
    onSurface = OnSurfaceColor,
    onSurfaceVariant = OnSurfaceVariantColor,
    error = ErrorColor,
    onError = OnErrorColor,
    errorContainer = ErrorContainerColor,
    onErrorContainer = OnErrorContainerColor
)

@Composable
fun OsmWearTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography = AppTypography,
        content = content
    )
}
