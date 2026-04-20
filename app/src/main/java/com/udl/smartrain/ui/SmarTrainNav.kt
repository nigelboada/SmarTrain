package com.udl.smartrain.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.udl.smartrain.ui.screens.DashboardScreen
import com.udl.smartrain.ui.screens.SessionScreen
import com.udl.smartrain.ui.viewmodel.MainViewModel

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Dashboard : Screen("dashboard")
    object Session : Screen("session")
}

@Composable
fun SmarTrainNav(navController: NavHostController, viewModel: MainViewModel) {
    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route // Utilitzem la ruta de l'objecte
    ) {
        // Pantalla Dashboard (READ)
        composable(Screen.Dashboard.route) {
            DashboardScreen(viewModel = viewModel, navController = navController) //ara li passo el controlador
        }

        // Pantalla Sessió (CREATE)
        composable(Screen.Session.route) {
            SessionScreen(
                viewModel = viewModel,
                onStopSession = { navController.popBackStack() } // Torna enrere
            )
        }

        // Si més endavant fas el Login:
        // composable(Screen.Login.route) { LoginScreen(...) }
    }
}