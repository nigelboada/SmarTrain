package com.udl.smartrain

import com.udl.smartrain.ui.screens.DashboardScreen
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.udl.smartrain.ui.Screen
import com.udl.smartrain.ui.screens.LoginScreen
import com.udl.smartrain.ui.screens.SessionScreen
import com.udl.smartrain.ui.theme.SmarTrainTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SmarTrainTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    SmarTrainApp()
                }
            }
        }
    }
}


@Composable
fun SmarTrainApp() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Screen.Login.route) {
        composable(Screen.Login.route) {
            LoginScreen(onLoginSuccess = {
                navController.navigate(Screen.Dashboard.route)
            })
        }
        composable(Screen.Dashboard.route) {
            DashboardScreen(onStartSession = {
                navController.navigate(Screen.Session.route)
            })
        }
        composable(Screen.Session.route) {
            SessionScreen(onStopSession = {
                navController.popBackStack() // Torna al Dashboard
            })
        }
    }
}