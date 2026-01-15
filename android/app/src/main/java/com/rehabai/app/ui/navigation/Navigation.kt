package com.rehabai.app.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rehabai.app.domain.SessionReport
import com.rehabai.app.ui.screens.AnalyticsScreen
import com.rehabai.app.ui.screens.DashboardScreen
import com.rehabai.app.ui.screens.ExerciseSelectionScreen
import com.rehabai.app.ui.screens.SessionScreen
import com.rehabai.app.ui.viewmodel.ExerciseSelectionViewModel
import com.rehabai.app.ui.viewmodel.SessionViewModel

sealed class Screen(val route: String) {
    object Dashboard : Screen("dashboard")
    object ExerciseSelection : Screen("exercise_selection")
    object Session : Screen("session/{exerciseId}") {
        fun createRoute(exerciseId: String) = "session/$exerciseId"
    }
    object Analytics : Screen("analytics")
}

// Shared state for passing report between screens
object NavigationState {
    var currentReport: SessionReport? = null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RehabNavHost() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    
    // Bottom navigation items
    val bottomNavItems = listOf(
        BottomNavItem("Dashboard", Icons.Default.Home, Screen.Dashboard.route),
        BottomNavItem("Exercise", Icons.Default.Person, Screen.ExerciseSelection.route)
    )
    
    // Show bottom nav only on main screens
    val showBottomNav = currentRoute in listOf(Screen.Dashboard.route, Screen.ExerciseSelection.route)
    
    Scaffold(
        bottomBar = {
            if (showBottomNav) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                            selected = currentRoute == item.route,
                            onClick = {
                                if (currentRoute != item.route) {
                                    navController.navigate(item.route) {
                                        popUpTo(Screen.Dashboard.route) {
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
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Screen.ExerciseSelection.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    onExerciseSelected = {
                        navController.navigate(Screen.ExerciseSelection.route)
                    }
                )
            }
            
            composable(Screen.ExerciseSelection.route) {
                val viewModel: ExerciseSelectionViewModel = viewModel()
                ExerciseSelectionScreen(
                    viewModel = viewModel,
                    onExerciseSelected = { exercise ->
                        navController.navigate(Screen.Session.createRoute(exercise.id))
                    }
                )
            }
            
            composable(
                route = Screen.Session.route,
                arguments = listOf(navArgument("exerciseId") { type = NavType.StringType })
            ) { backStackEntry ->
                val exerciseId = backStackEntry.arguments?.getString("exerciseId") ?: return@composable
                val viewModel: SessionViewModel = viewModel()
                
                SessionScreen(
                    exerciseId = exerciseId,
                    viewModel = viewModel,
                    onBack = {
                        viewModel.resetState()
                        navController.popBackStack()
                    },
                    onSessionComplete = {
                        // Save report for analytics if needed
                        viewModel.uiState.value.report?.let { report ->
                            NavigationState.currentReport = report
                        }
                        viewModel.resetState()
                        navController.popBackStack()
                    },
                    onViewAnalytics = { report ->
                        NavigationState.currentReport = report
                        navController.navigate(Screen.Analytics.route)
                    }
                )
            }
            
            composable(Screen.Analytics.route) {
                val report = NavigationState.currentReport
                if (report != null) {
                    AnalyticsScreen(
                        report = report,
                        onBack = {
                            navController.popBackStack()
                        }
                    )
                } else {
                    // Fallback if no report
                    LaunchedEffect(Unit) {
                        navController.popBackStack()
                    }
                }
            }
        }
    }
}

data class BottomNavItem(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val route: String
)
