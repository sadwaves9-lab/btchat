package com.example.btchat.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class HomeTab(val label: String) {
    CHATS("Chats"),
    GROUPS("Groups"),
    FILES("Files"),
    ME("Me")
}

@Composable
fun BTChatBottomNav(
    selected: HomeTab,
    onSelect: (HomeTab) -> Unit
) {
    NavigationBar(containerColor = Color.Transparent) {
        HomeTab.values().forEach { tab ->
            NavigationBarItem(
                selected = selected == tab,
                onClick = { onSelect(tab) },
                icon = {
                    Icon(
                        when (tab) {
                            HomeTab.CHATS -> Icons.Default.Chat
                            HomeTab.GROUPS -> Icons.Default.Groups
                            HomeTab.FILES -> Icons.Default.Folder
                            HomeTab.ME -> Icons.Default.Person
                        },
                        contentDescription = tab.label
                    )
                },
                label = { Text(tab.label) }
            )
        }
    }
}
