package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.service.ReelAccessibilityService
import com.example.ui.MainViewModel
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.DisclosureScreen
import com.example.ui.screens.PrivacyPolicyScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.utils.AccessibilityPermissionManager

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                MainApp(viewModel)
            }
        }
    }
}

@Composable
fun MainApp(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "home"
    
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    
    var isServiceEnabled by remember { mutableStateOf(AccessibilityPermissionManager.isAccessibilityServiceEnabled(context)) }
    
    var hasShownDisclosure by remember { mutableStateOf(prefs.getBoolean("has_shown_disclosure", false)) }
    var wasServiceEnabled by remember { mutableStateOf(prefs.getBoolean("was_service_enabled", false)) }
    
    var currentDisclosureDismissed by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val enabled = AccessibilityPermissionManager.isAccessibilityServiceEnabled(context)
                isServiceEnabled = enabled
                
                if (enabled) {
                    prefs.edit().putBoolean("was_service_enabled", true).apply()
                    wasServiceEnabled = true
                    if (!hasShownDisclosure) {
                        prefs.edit().putBoolean("has_shown_disclosure", true).apply()
                        hasShownDisclosure = true
                    }
                } else {
                    if (wasServiceEnabled) {
                        // User disabled it later
                        prefs.edit().putBoolean("was_service_enabled", false).apply()
                        wasServiceEnabled = false
                        currentDisclosureDismissed = false // force show again
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val showDisclosure = (!hasShownDisclosure || (!isServiceEnabled && !wasServiceEnabled && !hasShownDisclosure) || (!isServiceEnabled && !currentDisclosureDismissed && prefs.getBoolean("has_shown_disclosure", true) && !wasServiceEnabled && prefs.contains("was_service_enabled")))
    
    // Simplification for the disclosure logic:
    // We show disclosure IF:
    // 1. First launch (!hasShownDisclosure) AND not dismissed this session
    // 2. Or, user disabled it later (wasServiceEnabled was true, now false, so we reset wasServiceEnabled and cleared currentDisclosureDismissed)
    val shouldShowDisclosure = (!hasShownDisclosure && !currentDisclosureDismissed) || (!isServiceEnabled && !currentDisclosureDismissed && !wasServiceEnabled && prefs.contains("was_service_enabled"))
    
    // Actually, let's use a simpler state:
    var displayDisclosure by remember { mutableStateOf(!prefs.getBoolean("has_shown_disclosure", false)) }
    
    // Effect to check if we need to show disclosure again because service was disabled
    LaunchedEffect(isServiceEnabled) {
        if (isServiceEnabled) {
            prefs.edit().putBoolean("was_service_enabled", true).apply()
            prefs.edit().putBoolean("has_shown_disclosure", true).apply()
            displayDisclosure = false
        } else {
            val previouslyEnabled = prefs.getBoolean("was_service_enabled", false)
            if (previouslyEnabled) {
                prefs.edit().putBoolean("was_service_enabled", false).apply()
                displayDisclosure = true // Show again since it was disabled
            }
        }
    }

    if (displayDisclosure) {
        DisclosureScreen(
            onDismiss = {
                displayDisclosure = false
                prefs.edit().putBoolean("has_shown_disclosure", true).apply()
            },
            onEnabled = {
                displayDisclosure = false
                prefs.edit().putBoolean("has_shown_disclosure", true).apply()
            }
        )
    } else {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        icon = { Icon(Icons.Filled.Home, contentDescription = "Home") },
                        label = { Text("Home") },
                        selected = currentRoute == "home",
                        onClick = {
                            navController.navigate("home") {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "History") },
                        label = { Text("History") },
                        selected = currentRoute == "history",
                        onClick = {
                            navController.navigate("history") {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Filled.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") },
                        selected = currentRoute == "settings",
                        onClick = {
                            navController.navigate("settings") {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = "home",
                modifier = Modifier.padding(innerPadding)
            ) {
                composable("home") { HomeScreen(viewModel, isServiceEnabled) }
                composable("history") { HistoryScreen(viewModel) }
                composable("settings") { 
                    SettingsScreen(viewModel, onPrivacyPolicyClick = {
                        navController.navigate("privacy_policy")
                    }, onDisclosureClick = {
                        displayDisclosure = true
                    }) 
                }
                composable("privacy_policy") {
                    PrivacyPolicyScreen(onBack = { navController.popBackStack() })
                }
            }
        }
    }
}
