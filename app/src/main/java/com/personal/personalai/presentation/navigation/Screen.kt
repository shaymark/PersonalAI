package com.personal.personalai.presentation.navigation

sealed class Screen(val route: String) {
    object Chat : Screen("chat")
    object ScheduledTasks : Screen("scheduled_tasks")
    object LocationTasks : Screen("location_tasks")
    object Settings : Screen("settings")
}
