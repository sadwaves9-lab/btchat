package com.example.btchat.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/* ============================================================
 *  BTChat Ultra — Shape System (Glassmorphism-friendly)
 * ============================================================ */

val BTChatShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small      = RoundedCornerShape(12.dp),
    medium     = RoundedCornerShape(18.dp),
    large      = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

object BubbleShapes {
    val sent     = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 4.dp)
    val received = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 4.dp, bottomEnd = 20.dp)
    val file     = RoundedCornerShape(16.dp)
    val image    = RoundedCornerShape(18.dp)
}

object CardShapes {
    val glass     = RoundedCornerShape(24.dp)
    val device    = RoundedCornerShape(20.dp)
    val settings  = RoundedCornerShape(18.dp)
    val chip      = RoundedCornerShape(50)
}

object ButtonShapes {
    val primary  = RoundedCornerShape(50)
    val square   = RoundedCornerShape(14.dp)
    val pill     = RoundedCornerShape(50)
}
