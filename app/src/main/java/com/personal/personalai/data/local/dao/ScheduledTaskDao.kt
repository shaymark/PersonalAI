package com.personal.personalai.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.personal.personalai.data.local.entity.ScheduledTaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduledTaskDao {

    @Query("SELECT * FROM scheduled_tasks ORDER BY scheduledAt ASC")
    fun getAllTasks(): Flow<List<ScheduledTaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: ScheduledTaskEntity): Long

    @Update
    suspend fun updateTask(task: ScheduledTaskEntity)

    @Delete
    suspend fun deleteTask(task: ScheduledTaskEntity)

    @Query("UPDATE scheduled_tasks SET isCompleted = 1 WHERE id = :taskId")
    suspend fun markCompleted(taskId: Long)

    @Query("UPDATE scheduled_tasks SET workerId = :workerId WHERE id = :taskId")
    suspend fun updateWorkerId(taskId: Long, workerId: String)
}
