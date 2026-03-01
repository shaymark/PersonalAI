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

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE scheduled_tasks ADD COLUMN taskType TEXT NOT NULL DEFAULT 'REMINDER'")
        database.execSQL("ALTER TABLE scheduled_tasks ADD COLUMN aiPrompt TEXT")
        database.execSQL("ALTER TABLE scheduled_tasks ADD COLUMN outputTarget TEXT NOT NULL DEFAULT 'NOTIFICATION'")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE scheduled_tasks ADD COLUMN recurrenceType TEXT NOT NULL DEFAULT 'NONE'")
    }
}

@Database(
    entities = [MessageEntity::class, ScheduledTaskEntity::class, MemoryEntity::class],
    version = 4,
    exportSchema = false
)
abstract class PersonalAIDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun scheduledTaskDao(): ScheduledTaskDao
    abstract fun memoryDao(): MemoryDao
}
