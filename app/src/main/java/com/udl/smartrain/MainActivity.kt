package com.udl.smartrain

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import androidx.room.Room
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.firestore
import com.udl.smartrain.data.local.AppDatabase
import com.udl.smartrain.data.local.LocationProvider
import com.udl.smartrain.data.repository.SessionRepositoryImpl
import com.udl.smartrain.ui.navigation.Screen
import com.udl.smartrain.ui.screens.DashboardScreen
import com.udl.smartrain.ui.screens.LoginScreen
import com.udl.smartrain.ui.screens.ProfileScreen
import com.udl.smartrain.ui.screens.SessionDetailScreen
import com.udl.smartrain.ui.screens.SessionScreen
import com.udl.smartrain.ui.theme.SmarTrainTheme
import com.udl.smartrain.ui.viewmodel.MainViewModel
import com.udl.smartrain.ui.viewmodel.MainViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, "smartrain-db"
        )
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .addMigrations(AppDatabase.MIGRATION_2_3)
            .addMigrations(AppDatabase.MIGRATION_3_4)
            .build()

        val firestore = Firebase.firestore
        val repository = SessionRepositoryImpl(db.sessionDao(), firestore)
        val locationProvider = LocationProvider(applicationContext)
        val auth = FirebaseAuth.getInstance()

        val viewModel: MainViewModel by viewModels {
            MainViewModelFactory(repository, locationProvider, auth)
        }

        setContent {
            SmarTrainTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    SmarTrainApp(viewModel)
                }
            }
        }
    }
}

@Composable
fun SmarTrainApp(viewModel: MainViewModel) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = if (viewModel.isAuthenticated) Screen.Dashboard.route else Screen.Login.route
    ) {
        composable(Screen.Login.route) {
            LoginScreen(
                viewModel = viewModel,
                onLoginSuccess = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.Dashboard.route) {
            DashboardScreen(viewModel = viewModel, navController = navController)
        }
        composable(Screen.Session.route) {
            SessionScreen(
                viewModel = viewModel,
                onStopSession = { navController.popBackStack() }
            )
        }
        composable(
            route = Screen.SessionDetail.route,
            arguments = listOf(navArgument(Screen.SessionDetail.ARG_SESSION_ID) {
                type = NavType.StringType
            })
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments
                ?.getString(Screen.SessionDetail.ARG_SESSION_ID)
                .orEmpty()
            SessionDetailScreen(
                viewModel = viewModel,
                sessionId = sessionId,
                navController = navController
            )
        }
        composable(Screen.Profile.route) {
            ProfileScreen(
                viewModel = viewModel,
                navController = navController
            )
        }
    }
}
