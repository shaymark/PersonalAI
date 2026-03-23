package com.personal.personalai.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.personal.personalai.data.local.entity.GeofenceTaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GeofenceTaskDao {

    @Query("SELECT * FROM geofence_tasks ORDER BY createdAt DESC")
    fun getAllTasks(): Flow<List<GeofenceTaskEntity>>

    @Query("SELECT * FROM geofence_tasks WHERE isActive = 1")
    suspend fun getActiveTasks(): List<GeofenceTaskEntity>

    @Query("SELECT * FROM geofence_tasks WHERE id = :id")
    suspend fun getTaskById(id: Long): GeofenceTaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: GeofenceTaskEntity): Long

    @Update
    suspend fun updateTask(task: GeofenceTaskEntity)

    @Delete
    suspend fun deleteTask(task: GeofenceTaskEntity)

    @Query("UPDATE geofence_tasks SET isActive = :isActive WHERE id = :id")
    suspend fun setActive(id: Long, isActive: Boolean)
}
