package com.dialer.truedialer.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.dialer.truedialer.util.PermissionUtils
import com.dialer.truedialer.util.PhoneNumberUtils

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Dialer : Screen("dialer", "Dialer", Icons.Default.Dialpad)
    object Recents : Screen("recents", "Recents", Icons.Default.History)
    object Contacts : Screen("contacts", "Contacts", Icons.Default.Contacts)
    object Enquiries : Screen("enquiries", "Enquiries", Icons.Default.AssignmentInd)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val context = LocalContext.current
    var dialNumberArg by remember { mutableStateOf("") }

    val items = listOf(
        Screen.Dialer,
        Screen.Recents,
        Screen.Contacts,
        Screen.Enquiries,
        Screen.Settings
    )

    fun placeDirectCall(number: String) {
        if (number.isBlank()) return
        val clean = PhoneNumberUtils.normalize(number)
        com.dialer.truedialer.service.ActiveCallManager.isOutgoingCall = true
        com.dialer.truedialer.service.ActiveCallManager.lastIncomingNumber = clean
        val uri = Uri.parse("tel:$clean")
        val intent = Intent(Intent.ACTION_CALL, uri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            if (PermissionUtils.hasPermissions(context, arrayOf(android.Manifest.permission.CALL_PHONE))) {
                context.startActivity(intent)
            } else {
                val dialIntent = Intent(Intent.ACTION_DIAL, uri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(dialIntent)
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Could not call: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                items.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = currentRoute == screen.route,
                        onClick = {
                            if (currentRoute != screen.route) {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dialer.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Dialer.route) {
                DialerScreen(initialDialNumber = dialNumberArg)
            }
            composable(Screen.Recents.route) {
                RecentsScreen(
                    onCallNumber = { number -> placeDirectCall(number) }
                )
            }
            composable(Screen.Contacts.route) {
                ContactsScreen(
                    onCallNumber = { number -> placeDirectCall(number) }
                )
            }
            composable(Screen.Enquiries.route) {
                EnquiryListScreen(
                    onCallNumber = { number -> placeDirectCall(number) }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateToPipeline = { navController.navigate("pipeline") }
                )
            }
            composable("pipeline") {
                AnalyticsScreen(
                    onCallNumber = { number -> placeDirectCall(number) },
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
