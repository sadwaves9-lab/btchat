package com.example.btchat.update

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun UpdateDialog(
    info: UpdateInfo,
    isDownloading: Boolean,
    progress: Int,
    onDismiss: () -> Unit,
    onUpdate: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isDownloading) onDismiss() },
        icon = {
            Icon(
                Icons.Default.CloudDownload,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                "Update Available",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "New version: ",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.7f)
                    )
                    Text(
                        info.versionName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(12.dp))

                if (info.changelog.isNotBlank()) {
                    Text(
                        "What's new:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Box2(
                        Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(10.dp)
                    ) {
                        Text(
                            info.changelog.take(500),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.85f)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }

                if (isDownloading) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Downloading... $progress%",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onUpdate,
                enabled = !isDownloading
            ) {
                Text(if (isDownloading) "Downloading..." else "Update Now")
            }
        },
        dismissButton = {
            if (!isDownloading) {
                TextButton(onClick = onDismiss) {
                    Text("Later")
                }
            }
        }
    )
}

@Composable
private fun Box2(
    modifier: Modifier,
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.Box(modifier = modifier) {
        content()
    }
}
