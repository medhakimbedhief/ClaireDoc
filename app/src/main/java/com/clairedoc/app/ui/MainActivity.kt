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
import com.clairedoc.app.ui.download.DownloadScreen
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
                // If the model is already on device, skip straight to scan.
                val startDestination = if (liteRTEngine.isModelPresent) {
                    NavRoutes.SCAN
                } else {
                    NavRoutes.DOWNLOAD
                }

                NavHost(
                    navController = navController,
                    startDestination = startDestination
                ) {
                    composable(NavRoutes.DOWNLOAD) {
                        DownloadScreen(navController = navController)
                    }

                    composable(NavRoutes.SCAN) {
                        ScanScreen(navController = navController)
                    }

                    composable(
                        route = NavRoutes.RESULT,
                        arguments = listOf(
                            navArgument("resultJson") { type = NavType.StringType }
                        )
                    ) {
                        // ResultViewModel reads resultJson from SavedStateHandle directly
                        // and URL-decodes it there. No need to parse it here.
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
