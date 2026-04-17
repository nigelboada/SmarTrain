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
import androidx.room.Room
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import com.udl.smartrain.data.local.AppDatabase
import com.udl.smartrain.data.repository.SessionRepositoryImpl
import com.udl.smartrain.ui.Screen
import com.udl.smartrain.ui.screens.LoginScreen
import com.udl.smartrain.ui.screens.SessionScreen
import com.udl.smartrain.ui.theme.SmarTrainTheme
import com.udl.smartrain.ui.viewmodel.MainViewModel
import com.udl.smartrain.ui.viewmodel.MainViewModelFactory

import androidx.activity.viewModels

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, "smartrain-db"
        ).build()

        // 2. Inicialitzar Firebase i el Repositori
        val firestore = Firebase.firestore
        val repository = SessionRepositoryImpl(db.sessionDao(), firestore)

        // 3. Crear el ViewModel usant la Factory
        val viewModel: MainViewModel by viewModels {
            MainViewModelFactory(repository)
        }

        setContent {
            SmarTrainTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    // Passar el viewModel a la teva App
                    SmarTrainApp(viewModel)
                }
            }
        }


    }
}


@Composable
fun SmarTrainApp(viewModel: MainViewModel) {
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