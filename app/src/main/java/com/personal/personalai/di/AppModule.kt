package com.personal.personalai.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import androidx.work.WorkManager
import com.personal.personalai.data.local.MIGRATION_1_2
import com.personal.personalai.data.local.MIGRATION_2_3
import com.personal.personalai.data.local.MIGRATION_3_4
import com.personal.personalai.data.local.PersonalAIDatabase
import com.personal.personalai.data.local.dao.MemoryDao
import com.personal.personalai.data.local.dao.MessageDao
import com.personal.personalai.data.local.dao.ScheduledTaskDao
import com.personal.personalai.data.repository.AiRepositoryImpl
import com.personal.personalai.data.repository.ChatRepositoryImpl
import com.personal.personalai.data.repository.MemoryRepositoryImpl
import com.personal.personalai.data.repository.TaskRepositoryImpl
import com.personal.personalai.domain.repository.AiRepository
import com.personal.personalai.domain.repository.ChatRepository
import com.personal.personalai.domain.repository.MemoryRepository
import com.personal.personalai.domain.repository.TaskRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PersonalAIDatabase =
        Room.databaseBuilder(context, PersonalAIDatabase::class.java, "personal_ai_db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .build()

    @Provides
    fun provideMessageDao(db: PersonalAIDatabase): MessageDao = db.messageDao()

    @Provides
    fun provideScheduledTaskDao(db: PersonalAIDatabase): ScheduledTaskDao = db.scheduledTaskDao()

    @Provides
    fun provideMemoryDao(db: PersonalAIDatabase): MemoryDao = db.memoryDao()

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
        WorkManager.getInstance(context)

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.dataStore

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAiRepository(impl: AiRepositoryImpl): AiRepository

    @Binds
    @Singleton
    abstract fun bindChatRepository(impl: ChatRepositoryImpl): ChatRepository

    @Binds
    @Singleton
    abstract fun bindTaskRepository(impl: TaskRepositoryImpl): TaskRepository

    @Binds
    @Singleton
    abstract fun bindMemoryRepository(impl: MemoryRepositoryImpl): MemoryRepository
}
