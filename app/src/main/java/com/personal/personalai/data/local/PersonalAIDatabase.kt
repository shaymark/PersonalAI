package com.personal.personalai.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.personal.personalai.data.local.dao.MessageDao
import com.personal.personalai.data.local.dao.ScheduledTaskDao
import com.personal.personalai.data.local.entity.MessageEntity
import com.personal.personalai.data.local.entity.ScheduledTaskEntity

@Database(
    entities = [MessageEntity::class, ScheduledTaskEntity::class],
    version = 1,
    exportSchema = false
)
abstract class PersonalAIDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun scheduledTaskDao(): ScheduledTaskDao
}
