package com.sacco.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sacco.app.ui.navigation.Routes
import com.sacco.app.viewmodel.MemberViewModel

data class Tab(val route: String, val label: String, val icon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberShell(onLogout: () -> Unit, vm: MemberViewModel = viewModel()) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val unread by vm.unread.collectAsStateWithLifecycle()
    var confirmLogout by remember { mutableStateOf(false) }
    val tabs = listOf(
        Tab(Routes.HOME, "Home", Icons.Filled.Home), Tab(Routes.SAVINGS, "Savings", Icons.Filled.Savings),
        Tab(Routes.LOANS, "Loans", Icons.Filled.AccountBalanceWallet), Tab(Routes.TXNS, "History", Icons.Filled.SwapHoriz),
        Tab(Routes.PROFILE, "Profile", Icons.Filled.Person)
    )
    val titles = mapOf(Routes.HOME to "Dashboard", Routes.SAVINGS to "Savings", Routes.LOANS to "Loans", Routes.APPLY to "Apply for a loan",
        Routes.LOAN to "Loan details", Routes.TXNS to "Transactions", Routes.SHARES to "Shares", Routes.NOTIFS to "Notifications", Routes.PROFILE to "My profile")
    fun go(r: String) {
        if (tabs.any { it.route == r }) nav.navigate(r) { popUpTo(Routes.HOME) { saveState = true }; launchSingleTop = true; restoreState = true }
        else nav.navigate(r) { launchSingleTop = true }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(titles[route] ?: "SACCO Connect") },
                navigationIcon = { if (route != null && tabs.none { it.route == route }) IconButton({ nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton({ go(Routes.NOTIFS) }) {
                        BadgedBox(badge = { if (unread > 0) Badge { Text("$unread") } }) { Icon(Icons.Filled.Notifications, "Notifications") }
                    }
                    IconButton({ confirmLogout = true }) { Icon(Icons.AutoMirrored.Filled.Logout, "Logout") }
                }
            )
        },
        bottomBar = {
            if (tabs.any { it.route == route }) NavigationBar {
                tabs.forEach { t -> NavigationBarItem(selected = route == t.route, onClick = { go(t.route) }, icon = { Icon(t.icon, null) }, label = { Text(t.label) }) }
            }
        }
    ) { pad ->
        NavHost(nav, startDestination = Routes.HOME, modifier = Modifier.padding(pad)) {
            composable(Routes.HOME) { HomeScreen(vm, ::go) }
            composable(Routes.SAVINGS) { SavingsScreen(vm) }
            composable(Routes.LOANS) { LoansScreen(vm, { go(Routes.APPLY) }, { nav.navigate("loan/$it") }) }
            composable(Routes.APPLY) { LoanApplyScreen(vm) { nav.popBackStack(); go(Routes.LOANS) } }
            composable(Routes.LOAN, arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                LoanDetailScreen(vm, it.arguments!!.getLong("id"))
            }
            composable(Routes.TXNS) { TransactionsScreen(vm.transactions.collectAsStateWithLifecycle().value) }
            composable(Routes.SHARES) { SharesScreen(vm) }
            composable(Routes.NOTIFS) { NotificationsScreen(vm) }
            composable(Routes.PROFILE) { ProfileScreen(vm) { confirmLogout = true } }
        }
    }
    if (confirmLogout) AlertDialog(
        onDismissRequest = { confirmLogout = false }, title = { Text("Log out?") }, text = { Text("You will need to sign in again.") },
        confirmButton = { Button({ confirmLogout = false; onLogout() }) { Text("Logout") } },
        dismissButton = { TextButton({ confirmLogout = false }) { Text("Cancel") } }
    )
}
