package com.personal.personalai.presentation.chat

import android.Manifest

/** Maps Android permission strings to user-facing rationale messages shown before the dialog. */
object PermissionRationale {
    fun get(permission: String): String = when (permission) {
        Manifest.permission.SEND_SMS ->
            "The AI needs SMS permission to send text messages on your behalf."
        Manifest.permission.READ_CONTACTS ->
            "The AI needs Contacts permission to look up phone numbers and names."
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION ->
            "The AI needs Location permission to answer location questions or set geofence reminders."
        Manifest.permission.ACCESS_BACKGROUND_LOCATION ->
            "The AI needs Background Location permission so location reminders fire when the app is closed. You'll be directed to Settings."
        Manifest.permission.POST_NOTIFICATIONS ->
            "The AI needs Notification permission to send you reminders and alerts."
        else -> "The AI needs a system permission to complete this action."
    }
}
