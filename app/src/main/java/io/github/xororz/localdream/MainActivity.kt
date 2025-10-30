package io.github.xororz.localdream

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.xororz.localdream.navigation.Screen
import io.github.xororz.localdream.ui.screens.ModelListScreen
import io.github.xororz.localdream.ui.screens.ModelRunScreen
import io.github.xororz.localdream.ui.screens.UpscaleScreen
import io.github.xororz.localdream.ui.screens.HistoryScreen
import io.github.xororz.localdream.ui.screens.PromptLibraryScreen
import io.github.xororz.localdream.ui.theme.LocalDreamTheme
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import io.github.xororz.localdream.data.ThemePreferences
import io.github.xororz.localdream.data.AppTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember

class MainActivity : ComponentActivity() {
    private val requestStoragePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            Toast.makeText(
                this,
                "Storage permission is required for saving generated images",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            Toast.makeText(
                this,
                "Notification permission is required for background image generation",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun checkStoragePermission() {
        // < Android 10
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // ok
                }

                shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE) -> {
                    Toast.makeText(
                        this,
                        "Storage permission is needed for saving generated images",
                        Toast.LENGTH_LONG
                    ).show()
                    requestStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }

                else -> {
                    requestStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }
        }
    }

    private fun checkNotificationPermission() {
        // > Android 13
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // ok
                }

                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    Toast.makeText(
                        this,
                        "Notification permission is needed for background image generation",
                        Toast.LENGTH_LONG
                    ).show()
                    requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }

                else -> {
                    requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        checkStoragePermission()
        checkNotificationPermission()

        setContent {
            val themePreferences = remember { ThemePreferences(this) }
            val currentTheme by themePreferences.currentTheme.collectAsState(initial = AppTheme.SYSTEM)
            val useDynamicColor by themePreferences.useDynamicColor.collectAsState(initial = true)
            
            LocalDreamTheme(
                appTheme = currentTheme,
                dynamicColor = useDynamicColor
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    NavHost(
                        navController = navController,
                        startDestination = Screen.ModelList.route
                    ) {
                        composable(Screen.ModelList.route) {
                            ModelListScreen(navController)
                        }
                        composable(
                            route = Screen.ModelRun.route,
                            arguments = listOf(
                                navArgument("modelId") {
                                    type = NavType.StringType
                                },
                                navArgument("resolution") {
                                    type = NavType.IntType
                                    defaultValue = 512
                                    nullable = false
                                }
                            )
                        ) { backStackEntry ->
                            val modelId = backStackEntry.arguments?.getString("modelId") ?: ""
                            val resolution = backStackEntry.arguments?.getInt("resolution") ?: 512

                            ModelRunScreen(
                                modelId = modelId,
                                resolution = resolution,
                                navController = navController
                            )
                        }
                        composable(Screen.Upscale.route) {
                            UpscaleScreen(navController)
                        }
                        composable(Screen.History.route) {
                            HistoryScreen(navController)
                        }
                        composable(Screen.PromptLibrary.route) {
                            PromptLibraryScreen(navController)
                        }
                    }
                }
            }
        }
    }
}