package com.udl.smartrain.ui

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Dashboard : Screen("dashboard")
    object Session : Screen("session")
}