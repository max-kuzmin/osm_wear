package com.osm.wear.presentation.components

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.material3.*
import com.osm.wear.presentation.theme.AppDimensions

@Composable
fun NavigationButton(
    isActive: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    warningText: String? = null,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isActive) {
            Button(
                onClick = onStop,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                icon = {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop Navigation",
                        modifier = Modifier.size(AppDimensions.IconNormal)
                    )
                }
            ) {
                Text("Stop Navigation", style = MaterialTheme.typography.labelMedium)
            }
        } else {
            Button(
                onClick = {
                    if (warningText != null) {
                        Toast.makeText(context, warningText, Toast.LENGTH_LONG).show()
                    } else {
                        onStart()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                icon = {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Start Navigation",
                        modifier = Modifier.size(AppDimensions.IconNormal)
                    )
                }
            ) {
                Text("Start Navigation", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
