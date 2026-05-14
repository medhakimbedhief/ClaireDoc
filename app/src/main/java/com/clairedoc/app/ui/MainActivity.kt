package com.clairedoc.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.clairedoc.app.engine.LiteRTEngine
import com.clairedoc.app.ui.home.HomeScreen
import com.clairedoc.app.ui.model.ModelManagerScreen
import com.clairedoc.app.ui.rag.RagChatScreen
import com.clairedoc.app.ui.result.ResultScreen
import com.clairedoc.app.ui.scan.ScanScreen
import com.clairedoc.app.ui.settings.SettingsScreen
import com.clairedoc.app.ui.theme.ClaireDocTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

private data class BottomTab(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val BOTTOM_TABS = listOf(
    BottomTab(NavRoutes.HOME,     "Documents", Icons.Default.Description),
    BottomTab(NavRoutes.RAG_CHAT, "Ask",       Icons.Default.Chat),
    BottomTab(NavRoutes.SETTINGS, "Settings",  Icons.Default.Settings)
)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var liteRTEngine: LiteRTEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ClaireDocTheme {
                val navController = rememberNavController()
                val currentEntry by navController.currentBackStackEntryAsState()
                val currentRoute = currentEntry?.destination?.route

                val tabRoutes = BOTTOM_TABS.map { it.route }.toSet()
                val showBottomNav = currentRoute in tabRoutes

                val startDestination = if (liteRTEngine.isModelPresent) NavRoutes.HOME
                                       else NavRoutes.MODEL_MANAGER

                Scaffold(
                    bottomBar = {
                        if (showBottomNav) {
                            ClaireBottomNavBar(
                                navController = navController,
                                currentRoute = currentRoute
                            )
                        }
                    }
                ) { innerPadding ->
                    // Only apply bottom padding so each screen's inner Scaffold
                    // can independently handle its own top bar / system insets.
                    NavHost(
                        navController = navController,
                        startDestination = startDestination,
                        modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())
                    ) {
                        // ── First-launch model download / model management ──────
                        composable(NavRoutes.MODEL_MANAGER) {
                            ModelManagerScreen(
                                navController = navController,
                                canNavigateBack = navController.previousBackStackEntry != null
                            )
                        }

                        // ── Bottom nav destinations ────────────────────────────
                        composable(NavRoutes.HOME) {
                            HomeScreen(navController = navController)
                        }

                        composable(NavRoutes.RAG_CHAT) {
                            RagChatScreen(navController = navController)
                        }

                        composable(NavRoutes.SETTINGS) {
                            SettingsScreen(navController = navController)
                        }

                        // ── Modal / push destinations (no bottom nav) ──────────
                        composable(NavRoutes.SCAN) {
                            ScanScreen(navController = navController)
                        }

                        composable(
                            route = NavRoutes.RESULT,
                            arguments = listOf(
                                navArgument("resultJson") { type = NavType.StringType },
                                navArgument("sessionId")  { type = NavType.StringType }
                            )
                        ) {
                            ResultScreen(
                                resultJson = "",  // unused — ViewModel reads from SavedStateHandle
                                navController = navController
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Material 3 bottom navigation bar with three tabs: Documents / Ask / Settings.
 *
 * Tab switching uses [NavRoutes.HOME] as the pop-up root (not `findStartDestination()`)
 * to avoid popping back to MODEL_MANAGER when it was the graph start destination.
 */
@Composable
private fun ClaireBottomNavBar(
    navController: NavController,
    currentRoute: String?
) {
    NavigationBar {
        BOTTOM_TABS.forEach { tab ->
            NavigationBarItem(
                selected = currentRoute == tab.route,
                onClick = {
                    navController.navigate(tab.route) {
                        // Pop back to HOME (not startDestination) to avoid polluting
                        // the back stack when MODEL_MANAGER was the initial start.
                        popUpTo(NavRoutes.HOME) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label) }
            )
        }
    }
}
