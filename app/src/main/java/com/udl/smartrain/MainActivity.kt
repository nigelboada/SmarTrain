package com.udl.smartrain

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import androidx.room.Room
import androidx.core.view.WindowCompat
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.firestore
import com.udl.smartrain.data.local.AppDatabase
import com.udl.smartrain.data.local.AppLanguage
import com.udl.smartrain.data.local.AppPreferences
import com.udl.smartrain.data.local.LocationProvider
import com.udl.smartrain.data.repository.SessionRepositoryImpl
import com.udl.smartrain.ml.SessionRagRecommender
import com.udl.smartrain.ui.components.AppBackground
import com.udl.smartrain.ui.i18n.TextKey
import com.udl.smartrain.ui.i18n.text
import com.udl.smartrain.ui.navigation.Screen
import com.udl.smartrain.ui.screens.DashboardScreen
import com.udl.smartrain.ui.screens.InfoScreen
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
            .addMigrations(AppDatabase.MIGRATION_4_5)
            .build()

        val firestore = Firebase.firestore
        val repository = SessionRepositoryImpl(db.sessionDao(), firestore)
        val locationProvider = LocationProvider(applicationContext)
        val appPreferences = AppPreferences(applicationContext)
        val auth = FirebaseAuth.getInstance()
        SessionRagRecommender.initialize(applicationContext)

        val viewModel: MainViewModel by viewModels {
            MainViewModelFactory(repository, locationProvider, auth, appPreferences)
        }

        setContent {
            val darkMode by viewModel.darkMode.collectAsState()
            SmarTrainTheme(darkMode = darkMode) {
                AppBackground(darkMode = darkMode) {
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
    val darkMode by viewModel.darkMode.collectAsState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    var showSplash by remember { mutableStateOf(true) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var nicknameDraft by remember { mutableStateOf("") }
    val view = LocalView.current
    val systemBarColor = barColor(darkMode).toArgb()

    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        window.statusBarColor = systemBarColor
        window.navigationBarColor = systemBarColor
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
    }

    LaunchedEffect(Unit) {
        delay(1300)
        showSplash = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = !showSplash, enter = fadeIn(), exit = fadeOut()) {
            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    if (currentRoute != Screen.Login.route) {
                        AppTopNavigation(
                            title = screenTitle(currentRoute, language),
                            language = language,
                            darkMode = darkMode,
                            onProfileClick = {
                                navController.navigate(Screen.Profile.route) {
                                    launchSingleTop = true
                                }
                            },
                            onLanguageSelected = viewModel::updateLanguage,
                            onToggleDarkMode = { viewModel.updateDarkMode(!darkMode) },
                            onSignOutClick = { showLogoutDialog = true }
                        )
                    }
                },
                bottomBar = {
                    if (currentRoute != Screen.Login.route) {
                        AppBottomNavigation(
                            currentRoute = currentRoute,
                            darkMode = darkMode,
                            onHomeClick = {
                                navController.navigate(Screen.Dashboard.route) {
                                    launchSingleTop = true
                                    popUpTo(Screen.Dashboard.route) { inclusive = false }
                                }
                            },
                            onSessionClick = {
                                navController.navigate(Screen.Session.route) {
                                    launchSingleTop = true
                                }
                            },
                            onInfoClick = {
                                navController.navigate(Screen.Info.route) {
                                    launchSingleTop = true
                                }
                            }
                        )
                    }
                }
            ) {
                NavHost(
                    navController = navController,
                    startDestination = if (viewModel.isAuthenticated) Screen.Dashboard.route else Screen.Login.route,
                    modifier = Modifier.padding(it)
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
                    composable(Screen.Info.route) {
                        InfoScreen(language = language)
                    }
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

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text(language.text(TextKey.SIGN_OUT)) },
            text = { Text(language.text(TextKey.SIGN_OUT_QUESTION)) },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    viewModel.signOut()
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }) {
                    Text(language.text(TextKey.SIGN_OUT))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(language.text(TextKey.CANCEL))
                }
            }
        )
    }

    if (viewModel.shouldRequestNickname && currentRoute != Screen.Login.route && !showSplash) {
        val suggestedNickname = remember(viewModel.currentUserName) {
            viewModel.currentUserName
                .substringBefore("@")
                .trim()
                .ifBlank { language.text(TextKey.USER) }
        }
        LaunchedEffect(suggestedNickname) {
            if (nicknameDraft.isBlank()) {
                nicknameDraft = suggestedNickname
            }
        }
        AlertDialog(
            onDismissRequest = {},
            title = { Text(nicknameDialogTitle(language)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(nicknameDialogHelp(language))
                    TextField(
                        value = nicknameDraft,
                        onValueChange = { nicknameDraft = it },
                        singleLine = true,
                        label = { Text(nicknameDialogLabel(language)) }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateUserName(nicknameDraft.ifBlank { suggestedNickname })
                }) {
                    Text(language.text(TextKey.CONFIRM))
                }
            }
        )
    }
}

@Composable
private fun AppTopNavigation(
    title: String,
    language: AppLanguage,
    darkMode: Boolean,
    onProfileClick: () -> Unit,
    onLanguageSelected: (AppLanguage) -> Unit,
    onToggleDarkMode: () -> Unit,
    onSignOutClick: () -> Unit
) {
    var showLanguageMenu by remember { mutableStateOf(false) }
    var showSettingsMenu by remember { mutableStateOf(false) }
    Surface(
        color = barColor(darkMode),
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .height(54.dp)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onProfileClick, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.ManageAccounts, contentDescription = language.text(TextKey.PROFILE), tint = Color.White)
                }
                Box {
                    IconButton(onClick = { showLanguageMenu = true }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Language, contentDescription = language.text(TextKey.CHANGE_LANGUAGE), tint = Color.White)
                    }
                    DropdownMenu(expanded = showLanguageMenu, onDismissRequest = { showLanguageMenu = false }) {
                        AppLanguage.entries.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(if (item == language) "${item.label} *" else item.label) },
                                onClick = {
                                    onLanguageSelected(item)
                                    showLanguageMenu = false
                                }
                            )
                        }
                    }
                }
                Box {
                    IconButton(onClick = { showSettingsMenu = true }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Settings, contentDescription = "Configuracio", tint = Color.White)
                    }
                    DropdownMenu(expanded = showSettingsMenu, onDismissRequest = { showSettingsMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(themeModeLabel(darkMode, language)) },
                            onClick = {
                                onToggleDarkMode()
                                showSettingsMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(language.text(TextKey.SIGN_OUT)) },
                            onClick = {
                                showSettingsMenu = false
                                onSignOutClick()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppBottomNavigation(
    currentRoute: String?,
    darkMode: Boolean,
    onHomeClick: () -> Unit,
    onSessionClick: () -> Unit,
    onInfoClick: () -> Unit
) {
    Surface(
        color = barColor(darkMode),
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(50.dp)
                .padding(horizontal = 28.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomIconButton(
                selected = currentRoute == Screen.Dashboard.route,
                onClick = onHomeClick
            ) {
                Icon(Icons.Default.Home, contentDescription = "Inici")
            }
            BottomIconButton(
                selected = currentRoute == Screen.Session.route,
                onClick = onSessionClick
            ) {
                Icon(Icons.Default.AddCircleOutline, contentDescription = "Nova sessio")
            }
            BottomIconButton(
                selected = currentRoute == Screen.Info.route,
                onClick = onInfoClick
            ) {
                Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = "Ajuda")
            }
        }
    }
}

@Composable
private fun BottomIconButton(selected: Boolean, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (selected) Color.White.copy(alpha = 0.22f) else Color.Transparent,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.size(42.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides Color.White,
                content = icon
            )
        }
    }
}

private fun barColor(darkMode: Boolean): Color {
    return if (darkMode) Color(0xFF1C1C24) else Color(0xFF4B2B73)
}

private fun screenTitle(route: String?, language: AppLanguage): String {
    return when (route) {
        Screen.Dashboard.route -> "SmarTrain"
        Screen.Session.route -> when (language) {
            AppLanguage.CATALAN -> "Sensorització"
            AppLanguage.ENGLISH -> "Sensing"
            AppLanguage.SPANISH -> "Sensorización"
            AppLanguage.CHINESE -> "\u91c7\u96c6"
        }
        Screen.Profile.route -> language.text(TextKey.PROFILE)
        Screen.Info.route -> when (language) {
            AppLanguage.CATALAN -> "Ajuda"
            AppLanguage.ENGLISH -> "Help"
            AppLanguage.SPANISH -> "Ayuda"
            AppLanguage.CHINESE -> "\u5e2e\u52a9"
        }
        Screen.SessionDetail.route -> when (language) {
            AppLanguage.CATALAN -> "Detall de sessió"
            AppLanguage.ENGLISH -> "Session detail"
            AppLanguage.SPANISH -> "Detalle de sesión"
            AppLanguage.CHINESE -> "\u8bad\u7ec3\u8be6\u60c5"
        }
        else -> "SmarTrain"
    }
}

private fun themeModeLabel(darkMode: Boolean, language: AppLanguage): String {
    return when (language) {
        AppLanguage.CATALAN -> if (darkMode) "Mode clar" else "Mode fosc"
        AppLanguage.ENGLISH -> if (darkMode) "Light mode" else "Dark mode"
        AppLanguage.SPANISH -> if (darkMode) "Modo claro" else "Modo oscuro"
        AppLanguage.CHINESE -> if (darkMode) "\u6d45\u8272\u6a21\u5f0f" else "\u6df1\u8272\u6a21\u5f0f"
    }
}

private fun nicknameDialogTitle(language: AppLanguage): String {
    return when (language) {
        AppLanguage.CATALAN -> "Com vols aparèixer?"
        AppLanguage.ENGLISH -> "How should we call you?"
        AppLanguage.SPANISH -> "¿Cómo quieres aparecer?"
        AppLanguage.CHINESE -> "\u4f60\u60f3\u5982\u4f55\u663e\u793a\u540d\u79f0\uff1f"
    }
}

private fun nicknameDialogHelp(language: AppLanguage): String {
    return when (language) {
        AppLanguage.CATALAN -> "Aquest nickname es guardarà al perfil i s'usarà per defecte en les noves sessions."
        AppLanguage.ENGLISH -> "This nickname will be saved in your profile and used by default for new sessions."
        AppLanguage.SPANISH -> "Este nickname se guardará en el perfil y se usará por defecto en las nuevas sesiones."
        AppLanguage.CHINESE -> "\u8be5\u6635\u79f0\u5c06\u4fdd\u5b58\u5230\u4e2a\u4eba\u8d44\u6599\uff0c\u5e76\u9ed8\u8ba4\u7528\u4e8e\u65b0\u8bad\u7ec3\u3002"
    }
}

private fun nicknameDialogLabel(language: AppLanguage): String {
    return when (language) {
        AppLanguage.CATALAN -> "Nickname"
        AppLanguage.ENGLISH -> "Nickname"
        AppLanguage.SPANISH -> "Nickname"
        AppLanguage.CHINESE -> "\u6635\u79f0"
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
