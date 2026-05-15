package com.udl.smartrain

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
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
import com.udl.smartrain.data.local.AppPreferences
import com.udl.smartrain.data.local.LocationProvider
import com.udl.smartrain.data.repository.SessionRepositoryImpl
import com.udl.smartrain.ui.components.AppBackground
import com.udl.smartrain.ui.i18n.TextKey
import com.udl.smartrain.ui.i18n.text
import com.udl.smartrain.ui.navigation.Screen
import com.udl.smartrain.ui.screens.DashboardScreen
import com.udl.smartrain.ui.screens.LoginScreen
import com.udl.smartrain.ui.screens.ProfileScreen
import com.udl.smartrain.ui.screens.SessionDetailScreen
import com.udl.smartrain.ui.screens.SessionScreen
import com.udl.smartrain.ui.theme.SmarTrainTheme
import com.udl.smartrain.ui.viewmodel.MainViewModel
import com.udl.smartrain.ui.viewmodel.MainViewModelFactory
import kotlinx.coroutines.delay

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
        val appPreferences = AppPreferences(applicationContext)
        val auth = FirebaseAuth.getInstance()

        val viewModel: MainViewModel by viewModels {
            MainViewModelFactory(repository, locationProvider, auth, appPreferences)
        }

        setContent {
            SmarTrainTheme {
                AppBackground {
                    Surface(color = Color.Transparent) {
                        SmarTrainApp(viewModel)
                    }
                }
            }
        }
    }
}

@Composable
fun SmarTrainApp(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val language by viewModel.appLanguage.collectAsState()
    var showSplash by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(1300)
        showSplash = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = !showSplash, enter = fadeIn(), exit = fadeOut()) {
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
        AnimatedVisibility(visible = showSplash, enter = fadeIn(), exit = fadeOut()) {
            SplashScreen()
        }
        LanguageChangeBadge(
            languageLabel = "${language.text(TextKey.LANGUAGE_CHANGED_PREFIX)}: ${language.label}",
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 10.dp)
        )
    }
}

@Composable
private fun SplashScreen() {
    Surface(color = Color.White, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 54.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.logo),
                contentDescription = "SmarTrain",
                modifier = Modifier.size(width = 240.dp, height = 150.dp)
            )
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 28.dp),
                color = Color(0xFF22C9D8),
                trackColor = Color(0xFFE8ECF4)
            )
        }
    }
}

@Composable
private fun LanguageChangeBadge(languageLabel: String, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    var hasSeenInitialLanguage by remember { mutableStateOf(false) }

    LaunchedEffect(languageLabel) {
        if (hasSeenInitialLanguage) {
            visible = true
            delay(3100)
            visible = false
        } else {
            hasSeenInitialLanguage = true
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Surface(color = Color.White.copy(alpha = 0.92f), tonalElevation = 4.dp) {
            Text(
                text = languageLabel,
                color = Color(0xFF1A237E),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}
