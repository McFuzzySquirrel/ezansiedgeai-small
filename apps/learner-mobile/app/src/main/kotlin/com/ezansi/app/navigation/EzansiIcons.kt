package com.ezansi.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Centralised icon definitions for navigation.
 *
 * Uses Material Icons bundled with compose-material-icons-extended.
 * These are compile-time constants — no runtime icon loading.
 */
object EzansiIcons {
    val Chat: ImageVector = Icons.Filled.ChatBubble
    val Topics: ImageVector = Icons.Filled.MenuBook
    val Profiles: ImageVector = Icons.Filled.Person
    val Preferences: ImageVector = Icons.Filled.Settings
    val Library: ImageVector = Icons.Filled.LibraryBooks
}
