package com.ezansi.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState

/**
 * Bottom navigation bar for the 3 primary destinations: Chat, Topics, Profiles.
 *
 * Preferences and Library are accessed from within Profiles and Topics
 * respectively — they are not top-level navigation destinations.
 *
 * Design constraints:
 * - 48×48 dp minimum touch targets (WCAG / cracked-screen accessibility)
 * - High-contrast colours from the EzansiTheme palette
 * - No decorative animations (frame budget constraint)
 */
@Composable
fun EzansiBottomBar(
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        bottomBarItems.forEach { item ->
            NavigationBarItem(
                selected = currentRoute == item.route.route,
                onClick = {
                    if (currentRoute != item.route.route) {
                        navController.navigate(item.route.route) {
                            // Pop up to start destination to avoid building up a large back stack
                            popUpTo(EzansiRoute.Chat.route) {
                                saveState = true
                            }
                            // Avoid multiple copies of the same destination
                            launchSingleTop = true
                            // Restore state when re-selecting a previously selected item
                            restoreState = true
                        }
                    }
                },
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                    )
                },
                label = {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        }
    }
}

/**
 * Bottom bar navigation item descriptor.
 */
internal data class BottomBarItem(
    val route: EzansiRoute,
    val icon: ImageVector,
    val label: String,
)

/**
 * The 3 primary bottom bar destinations.
 * Uses Material Icons that are bundled with compose-material3 (no extra dependency).
 */
internal val bottomBarItems = listOf(
    BottomBarItem(
        route = EzansiRoute.Chat,
        icon = EzansiIcons.Chat,
        label = "Chat",
    ),
    BottomBarItem(
        route = EzansiRoute.Topics,
        icon = EzansiIcons.Topics,
        label = "Topics",
    ),
    BottomBarItem(
        route = EzansiRoute.Profiles,
        icon = EzansiIcons.Profiles,
        label = "Profiles",
    ),
)
