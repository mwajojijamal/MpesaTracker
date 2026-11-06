package com.mpesatracker.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

import com.mpesatracker.ui.analytics.AnalyticsScreen
import com.mpesatracker.ui.analytics.AnalyticsViewModel
import com.mpesatracker.ui.budgets.BudgetViewModel
import com.mpesatracker.ui.budgets.BudgetsScreen
import com.mpesatracker.ui.dashboard.DashboardScreen
import com.mpesatracker.ui.dashboard.DashboardViewModel
import com.mpesatracker.ui.reports.ExportViewModel
import com.mpesatracker.ui.reports.ReportsScreen
import com.mpesatracker.ui.settings.SettingsScreen
import com.mpesatracker.ui.transactions.TransactionViewModel
import com.mpesatracker.ui.transactions.TransactionsScreen

enum class MainNavTab(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "Home", Icons.Default.Home),
    TRANSACTIONS("transactions", "Transactions", Icons.Default.ReceiptLong),
    ANALYTICS("analytics", "Analytics", Icons.Default.BarChart),
    BUDGETS("budgets", "Budgets", Icons.Default.AccountBalanceWallet),
    SETTINGS("settings", "Settings", Icons.Default.Settings)
}

@Composable
fun MpesaAppNavigation() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                MainNavTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
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
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = MainNavTab.HOME.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            // 1. HOME SCREEN (Fixed missing navigation parameter)
            composable(MainNavTab.HOME.route) {
                val dashboardViewModel: DashboardViewModel = hiltViewModel()
                DashboardScreen(
                    viewModel = dashboardViewModel,
                    onNavigateToTransactions = {
                        navController.navigate(MainNavTab.TRANSACTIONS.route)
                    }
                )
            }

            // 2. ANALYTICS SCREEN
            composable(MainNavTab.ANALYTICS.route) {
                val analyticsViewModel: AnalyticsViewModel = hiltViewModel()
                AnalyticsScreen(viewModel = analyticsViewModel)
            }

            // 3. TRANSACTIONS SCREEN
            composable(MainNavTab.TRANSACTIONS.route) {
                val transactionViewModel: TransactionViewModel = hiltViewModel()
                TransactionsScreen(viewModel = transactionViewModel)
            }

            // 4. BUDGETS SCREEN
            composable(MainNavTab.BUDGETS.route) {
                val budgetViewModel: BudgetViewModel = hiltViewModel()
                BudgetsScreen(viewModel = budgetViewModel)
            }

            // 5. SETTINGS SCREEN
            composable(MainNavTab.SETTINGS.route) {
                SettingsScreen()
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = title)
    }
}