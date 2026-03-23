package com.personal.personalai.data.geofence

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.personal.personalai.domain.model.GeofenceTask
import com.personal.personalai.domain.model.GeofenceTransitionType
import com.personal.personalai.domain.repository.GeofenceTaskRepository
import com.personal.personalai.receiver.GeofenceBroadcastReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeofenceManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val geofencingClient: GeofencingClient,
    private val repository: GeofenceTaskRepository
) {
    private val pendingIntent: PendingIntent by lazy {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    suspend fun register(task: GeofenceTask) {
        val geofence = buildGeofence(task) ?: return
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()
        try {
            geofencingClient.addGeofences(request, pendingIntent).await()
            repository.setActive(task.id, true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register geofence ${task.id}: ${e.message}")
        }
    }

    suspend fun remove(taskId: Long) {
        try {
            geofencingClient.removeGeofences(listOf(taskId.toString())).await()
            repository.setActive(taskId, false)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove geofence $taskId: ${e.message}")
        }
    }

    suspend fun registerAll() {
        val tasks = repository.getActiveTasks()
        if (tasks.isEmpty()) return
        val geofences = tasks.mapNotNull { buildGeofence(it) }
        if (geofences.isEmpty()) return
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofences(geofences)
            .build()
        try {
            geofencingClient.addGeofences(request, pendingIntent).await()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to re-register geofences: ${e.message}")
        }
    }

    private fun buildGeofence(task: GeofenceTask): Geofence? {
        if (task.id == 0L) return null
        val transitionTypes = when (task.transitionType) {
            GeofenceTransitionType.ENTER -> Geofence.GEOFENCE_TRANSITION_ENTER
            GeofenceTransitionType.EXIT -> Geofence.GEOFENCE_TRANSITION_EXIT
            GeofenceTransitionType.BOTH -> Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT
        }
        return Geofence.Builder()
            .setRequestId(task.id.toString())
            .setCircularRegion(task.latitude, task.longitude, task.radiusMeters)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(transitionTypes)
            .build()
    }

    companion object {
        private const val TAG = "GeofenceManager"
    }
}
