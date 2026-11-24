package com.example.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.AssistantScreen
import com.example.ui.screens.ControlsScreen
import com.example.ui.screens.PermissionsSetupScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.AmoledBlack
import com.example.ui.theme.DeepObsidian
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.MarcoAiTheme
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary

object MarcoRoutes {
    const val ASSISTANT = "assistant"
    const val CONTROLS = "controls"
    const val SETUP = "setup"
    const val SETTINGS = "settings"
}

private data class NavDestinationItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

@Composable
fun MarcoApp(
    viewModel: MarcoViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val amplitude by viewModel.speechAmplitude.collectAsStateWithLifecycle()
    val partialTranscript by viewModel.partialTranscript.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val memories by viewModel.memories.collectAsStateWithLifecycle()
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val voices by viewModel.availableVoices.collectAsStateWithLifecycle()
    val pendingConfirmation by viewModel.pendingConfirmation.collectAsStateWithLifecycle()
    val permissionPromptNeeded by viewModel.permissionPromptNeeded.collectAsStateWithLifecycle()
    val geminiDiagnostics by viewModel.geminiDiagnostics.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        viewModel.refreshTelemetry()
    }

    LaunchedEffect(permissionPromptNeeded) {
        val target = permissionPromptNeeded ?: return@LaunchedEffect
        viewModel.clearPermissionPrompt()
        when (target) {
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION -> {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                )
            }
            Settings.ACTION_MANAGE_WRITE_SETTINGS -> {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_WRITE_SETTINGS,
                        Uri.parse("package:${context.packageName}")
                    )
                )
            }
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.CAMERA,
            Manifest.permission.POST_NOTIFICATIONS -> {
                permissionLauncher.launch(target)
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshTelemetry()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: MarcoRoutes.ASSISTANT

    val items = listOf(
        NavDestinationItem(
            route = MarcoRoutes.ASSISTANT,
            label = "Marco",
            selectedIcon = Icons.Filled.GraphicEq,
            unselectedIcon = Icons.Outlined.GraphicEq
        ),
        NavDestinationItem(
            route = MarcoRoutes.CONTROLS,
            label = "Controls",
            selectedIcon = Icons.Filled.Dashboard,
            unselectedIcon = Icons.Outlined.Dashboard
        ),
        NavDestinationItem(
            route = MarcoRoutes.SETUP,
            label = "Setup",
            selectedIcon = Icons.Filled.Security,
            unselectedIcon = Icons.Outlined.Security
        ),
        NavDestinationItem(
            route = MarcoRoutes.SETTINGS,
            label = "Settings",
            selectedIcon = Icons.Filled.Settings,
            unselectedIcon = Icons.Outlined.Settings
        )
    )

    val navigateTo: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    MarcoAiTheme(pureAmoledBlack = settings.amoledPureBlack) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavigationRail = maxWidth >= 600.dp

            Scaffold(
                containerColor = AmoledBlack,
                contentWindowInsets = WindowInsets.safeDrawing,
                bottomBar = {
                    if (!useNavigationRail) {
                        NavigationBar(
                            containerColor = DeepObsidian,
                            modifier = Modifier.navigationBarsPadding()
                        ) {
                            items.forEach { item ->
                                val selected = currentRoute == item.route
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateTo(item.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = item.label
                                        )
                                    },
                                    label = { Text(item.label) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = EmeraldBright,
                                        selectedTextColor = EmeraldBright,
                                        indicatorColor = EmeraldContainer,
                                        unselectedIconColor = TextMuted,
                                        unselectedTextColor = TextMuted
                                    ),
                                    modifier = Modifier.testTag("nav_${item.route}")
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useNavigationRail) {
                        NavigationRail(containerColor = DeepObsidian) {
                            items.forEach { item ->
                                val selected = currentRoute == item.route
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { navigateTo(item.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = item.label
                                        )
                                    },
                                    label = { Text(item.label) },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = EmeraldBright,
                                        selectedTextColor = EmeraldBright,
                                        indicatorColor = EmeraldContainer,
                                        unselectedIconColor = TextMuted,
                                        unselectedTextColor = TextMuted
                                    ),
                                    modifier = Modifier.testTag("nav_rail_${item.route}")
                                )
                            }
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = MarcoRoutes.ASSISTANT,
                        modifier = Modifier.weight(1f)
                    ) {
                        composable(MarcoRoutes.ASSISTANT) {
                            AssistantScreen(
                                status = status,
                                amplitude = amplitude,
                                partialTranscript = partialTranscript,
                                messages = messages,
                                telemetry = telemetry,
                                pendingConfirmation = pendingConfirmation,
                                onMicClick = viewModel::toggleVoiceListening,
                                onStopSpeaking = viewModel::stopSpeaking,
                                onSendText = viewModel::sendTextCommand,
                                onConfirmAction = viewModel::confirmPendingAction,
                                onCancelAction = viewModel::cancelPendingAction,
                                onToggleFloatingOrb = {
                                    viewModel.toggleFloatingOrb(!telemetry.floatingOrbRunning)
                                },
                                onNavigateToSetup = { navigateTo(MarcoRoutes.SETUP) }
                            )
                        }

                        composable(MarcoRoutes.CONTROLS) {
                            ControlsScreen(
                                telemetry = telemetry,
                                onRefreshTelemetry = viewModel::refreshTelemetry,
                                onToggleFlashlight = viewModel::toggleFlashlightDirect,
                                onAdjustVolume = viewModel::adjustVolumeDirect,
                                onSetBrightness = viewModel::setBrightnessDirect,
                                onRunAction = viewModel::runDirectAction,
                                onRunVoiceExample = { phrase ->
                                    viewModel.sendTextCommand(phrase)
                                    navigateTo(MarcoRoutes.ASSISTANT)
                                }
                            )
                        }

                        composable(MarcoRoutes.SETUP) {
                            PermissionsSetupScreen(
                                telemetry = telemetry,
                                onRefreshTelemetry = viewModel::refreshTelemetry
                            )
                        }

                        composable(MarcoRoutes.SETTINGS) {
                            SettingsScreen(
                                settings = settings,
                                telemetry = telemetry,
                                voices = voices,
                                memories = memories,
                                geminiDiagnostics = geminiDiagnostics,
                                onUpdateSettings = viewModel::updateSettings,
                                onToggleBackgroundService = viewModel::toggleBackgroundService,
                                onToggleFloatingOrb = viewModel::toggleFloatingOrb,
                                onPreviewVoice = viewModel::previewVoice,
                                onTestGemini = viewModel::testGeminiConnection,
                                onAddMemory = viewModel::addOrUpdateMemory,
                                onDeleteMemory = viewModel::deleteMemory,
                                onClearHistory = viewModel::clearHistory,
                                onOpenPermissions = { navigateTo(MarcoRoutes.SETUP) }
                            )
                        }
                    }
                }
            }
        }
    }
}
