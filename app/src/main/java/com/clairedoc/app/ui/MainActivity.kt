package com.clairedoc.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.clairedoc.app.engine.LiteRTEngine
import com.clairedoc.app.ui.home.HomeScreen
import com.clairedoc.app.ui.model.ModelManagerScreen
import com.clairedoc.app.ui.result.ResultScreen
import com.clairedoc.app.ui.scan.ScanScreen
import com.clairedoc.app.ui.theme.ClaireDocTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var liteRTEngine: LiteRTEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ClaireDocTheme {
                val navController = rememberNavController()

                val startDestination = if (liteRTEngine.isModelPresent) {
                    NavRoutes.HOME
                } else {
                    NavRoutes.MODEL_MANAGER
                }

                NavHost(
                    navController = navController,
                    startDestination = startDestination
                ) {
                    // First-launch model download / ongoing model management
                    composable(NavRoutes.MODEL_MANAGER) {
                        ModelManagerScreen(
                            navController = navController,
                            canNavigateBack = navController.previousBackStackEntry != null
                        )
                    }

                    composable(NavRoutes.HOME) {
                        HomeScreen(navController = navController)
                    }

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
