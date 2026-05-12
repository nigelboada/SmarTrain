package com.udl.smartrain.ui.navigation

// Screen.kt

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Dashboard : Screen("dashboard")
    object Session : Screen("session")
    object Profile : Screen("profile") // Nova ruta
    object SessionDetail : Screen("session_detail/{sessionId}") {
        const val ARG_SESSION_ID = "sessionId"

        fun createRoute(sessionId: String): String = "session_detail/$sessionId"
    }
}
