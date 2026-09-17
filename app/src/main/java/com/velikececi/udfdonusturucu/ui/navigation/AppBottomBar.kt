package com.velikececi.udfdonusturucu.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState

private data class BottomNavItem(val route: String, val label: String, val icon: ImageVector)

// iOS MainTabView.swift'teki 4 sekme, aynı sıra ve başlıklar: ANA SAYFA / ARAÇLAR / GEÇMİŞ / AYARLAR.
private val items = listOf(
    BottomNavItem(Routes.MAIN, "ANA SAYFA", Icons.Filled.Home),
    BottomNavItem(Routes.TOOLS, "ARAÇLAR", Icons.Filled.Handyman),
    BottomNavItem(Routes.HISTORY, "GEÇMİŞ", Icons.Filled.History),
    BottomNavItem(Routes.SETTINGS, "AYARLAR", Icons.Filled.Settings),
)

/**
 * Sekme tıklaması Play'in standart deseniyle çalışır: mevcut sekmenin durumu `saveState` ile
 * korunur, hedef sekmeye `restoreState` ile geri dönülür, `launchSingleTop` aynı sekmeye tekrar
 * tıklamada yeni bir kopya biriktirmez. Geri tuşu bu yüzden her sekmede ayrı, tutarlı bir
 * geçmiş yerine doğrudan ANA SAYFA'ya düşer.
 */
@Composable
fun AppBottomBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    NavigationBar {
        items.forEach { item ->
            val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (!selected) {
                        navController.navigate(item.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) },
            )
        }
    }
}
