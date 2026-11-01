package com.mowtiie.writa.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.mowtiie.writa.feature.auth.LoginDestination
import com.mowtiie.writa.feature.notes.list.NotesDestination
import com.mowtiie.writa.feature.notes.list.NotesNavigation
import com.mowtiie.writa.feature.setup.SetupDestination

/**
 * The one navigation graph. The screen the user is on follows [authState], so no screen checks the session or
 * navigates after signing in or saving the server: those change the state, and the graph follows. Nothing is drawn
 * until the state is known.
 */
@Composable
fun WritaNavHost(
    authState: AuthState?,
    setup: @Composable () -> Unit = { SetupDestination() },
    login: @Composable () -> Unit = { LoginDestination() },
    notes: @Composable () -> Unit = { NotesDestination(NotesNavigation()) },
) {
    if (authState == null) return

    val navController = rememberNavController()
    val startRoute = remember { authState.route }
    NavHost(navController = navController, startDestination = startRoute) {
        composable<SetupRoute> { setup() }
        composable<LoginRoute> { login() }
        composable<NotesRoute> { notes() }
    }
    LaunchedEffect(authState) { navController.show(authState) }
}

private fun NavController.show(state: AuthState) {
    val target = state.route
    if (currentDestination?.hasRoute(target::class) == true) return
    navigate(target) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}
