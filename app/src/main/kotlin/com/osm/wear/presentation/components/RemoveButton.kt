package com.osm.wear.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import com.osm.wear.presentation.theme.AppDimensions

@Composable
fun RemoveButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(AppDimensions.RemoveButtonBox)
            .background(MaterialTheme.colorScheme.errorContainer, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Remove",
            modifier = Modifier.size(AppDimensions.IconSmall),
            tint = MaterialTheme.colorScheme.onErrorContainer
        )
    }
}
