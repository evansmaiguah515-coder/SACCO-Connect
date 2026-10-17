package com.sacco.app.ui.navigation

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sacco.app.ui.screens.*
import com.sacco.app.viewmodel.AuthViewModel

object Routes {
    const val HOME = "home"; const val SAVINGS = "savings"; const val LOANS = "loans"; const val APPLY = "loan_apply"
    const val LOAN = "loan/{id}"; const val TXNS = "txns"; const val SHARES = "shares"; const val NOTIFS = "notifs"; const val PROFILE = "profile"
    const val A_DASH = "a_dash"; const val A_MEMBERS = "a_members"; const val A_MEMBER = "a_member/{id}"; const val A_LOANS = "a_loans"
    const val A_REPORTS = "a_reports"; const val A_TXNS = "a_txns"; const val A_REPAY = "a_repay"; const val A_PRODUCTS = "a_products"; const val A_ANNOUNCE = "a_announce"; const val A_MPESA = "a_mpesa"
}

@Composable
fun AppNav(authVm: AuthViewModel = viewModel()) {
    val nav = rememberNavController()
    val uid by authVm.userId.collectAsStateWithLifecycle()

    // Logging out (or an idle-timeout) clears the session; send the user back to the login screen.
    LaunchedEffect(uid) {
        if (uid == null) authVm.firebaseSignOut()   // session ended (logout or idle timeout): end the Firebase session too
        val route = nav.currentDestination?.route
        if (uid == null && (route == "member" || route == "admin"))
            nav.navigate("login") { popUpTo(nav.graph.id) { inclusive = true } }
    }

    NavHost(nav, startDestination = "splash") {
        composable("splash") {
            SplashScreen(authVm) { route -> nav.navigate(route) { popUpTo("splash") { inclusive = true } } }
        }
        composable("login") {
            LoginScreen(authVm,
                onSuccess = { role -> nav.navigate(if (role == "ADMIN") "admin" else "member") { popUpTo("login") { inclusive = true } } },
                onRegister = { nav.navigate("register") }, onForgot = { nav.navigate("forgot") },
                onNeedsProfile = { nav.navigate("complete_profile") })
        }
        composable("register") { RegisterScreen(authVm) { nav.popBackStack() } }
        composable("complete_profile") {
            CompleteProfileScreen(authVm, onDone = { nav.navigate("member") { popUpTo("login") { inclusive = true } } }, onBack = { nav.popBackStack() })
        }
        composable("forgot") { ForgotPasswordScreen(authVm) { nav.popBackStack() } }
        composable("member") { MemberShell(onLogout = { authVm.logout() }) }
        composable("admin") { AdminShell(onLogout = { authVm.logout() }) }
    }
}
