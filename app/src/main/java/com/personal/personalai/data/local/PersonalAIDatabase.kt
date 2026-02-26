package com.personal.personalai.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.personal.personalai.data.local.dao.MemoryDao
import com.personal.personalai.data.local.dao.MessageDao
import com.personal.personalai.data.local.dao.ScheduledTaskDao
import com.personal.personalai.data.local.entity.MemoryEntity
import com.personal.personalai.data.local.entity.MessageEntity
import com.personal.personalai.data.local.entity.ScheduledTaskEntity

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS memories (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                content TEXT NOT NULL,
                topic TEXT NOT NULL DEFAULT '',
                createdAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

@Database(
    entities = [MessageEntity::class, ScheduledTaskEntity::class, MemoryEntity::class],
    version = 2,
    exportSchema = false
)
abstract class PersonalAIDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun scheduledTaskDao(): ScheduledTaskDao
    abstract fun memoryDao(): MemoryDao
}
