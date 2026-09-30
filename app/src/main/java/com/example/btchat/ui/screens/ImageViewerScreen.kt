package com.example.btchat.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import java.io.File

@Composable
fun ImageViewerScreen(
    filePath: String,
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    val bmp = remember(filePath) {
        try { BitmapFactory.decodeFile(filePath) } catch (_: Exception) { null }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            offsetX += pan.x
                            offsetY += pan.y
                        }
                    }
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    ),
                contentScale = ContentScale.Fit
            )
        } else {
            Icon(
                Icons.Default.Close, null,
                tint = Color.White,
                modifier = Modifier.align(Alignment.Center).size(60.dp)
            )
        }

        // Top bar
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .statusBarsPadding(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0x80000000))
            ) {
                Icon(Icons.Default.Close, null, tint = Color.White)
            }
            Row {
                IconButton(
                    onClick = {
                        try {
                            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "image/*"
                                putExtra(android.content.Intent.EXTRA_STREAM, androidx.core.content.FileProvider.getUriForFile(
                                    ctx, ctx.packageName + ".fileprovider", File(filePath)
                                ))
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            ctx.startActivity(android.content.Intent.createChooser(intent, "Share"))
                        } catch (_: Exception) { }
                    },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0x80000000))
                ) {
                    Icon(Icons.Default.Share, null, tint = Color.White)
                }
            }
        }
    }
}
