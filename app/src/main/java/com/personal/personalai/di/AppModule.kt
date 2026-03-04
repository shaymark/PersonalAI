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
import com.personal.personalai.data.audio.AudioRecorderImpl
import com.personal.personalai.data.repository.AiRepositoryImpl
import com.personal.personalai.data.repository.ChatRepositoryImpl
import com.personal.personalai.data.repository.MemoryRepositoryImpl
import com.personal.personalai.data.repository.TaskRepositoryImpl
import com.personal.personalai.data.tools.ToolRegistryImpl
import com.personal.personalai.data.tools.android.GetClipboardTool
import com.personal.personalai.data.tools.interaction.AskUserTool
import com.personal.personalai.data.tools.android.GetInstalledAppsTool
import com.personal.personalai.data.tools.android.OpenAppTool
import com.personal.personalai.data.tools.android.ReadContactsTool
import com.personal.personalai.data.tools.management.ForgetAllMemoriesTool
import com.personal.personalai.data.tools.management.ForgetMemoryTool
import com.personal.personalai.data.tools.management.SaveMemoryTool
import com.personal.personalai.data.tools.management.ScheduleTaskTool
import com.personal.personalai.domain.audio.AudioRecorder
import com.personal.personalai.domain.repository.AiRepository
import com.personal.personalai.domain.repository.ChatRepository
import com.personal.personalai.domain.repository.MemoryRepository
import com.personal.personalai.domain.repository.TaskRepository
import com.personal.personalai.domain.tools.AgentTool
import com.personal.personalai.domain.tools.ToolRegistry
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds
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
    abstract fun bindAudioRecorder(impl: AudioRecorderImpl): AudioRecorder

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

@Module
@InstallIn(SingletonComponent::class)
abstract class ToolModule {

    /** Declares the Set<AgentTool> multibinding (required even when elements are always provided). */
    @Multibinds
    abstract fun toolSet(): Set<AgentTool>

    @Binds
    @Singleton
    abstract fun bindToolRegistry(impl: ToolRegistryImpl): ToolRegistry

    // ── Management tools ─────────────────────────────────────────────────────

    @Binds @IntoSet
    abstract fun bindScheduleTaskTool(impl: ScheduleTaskTool): AgentTool

    @Binds @IntoSet
    abstract fun bindSaveMemoryTool(impl: SaveMemoryTool): AgentTool

    @Binds @IntoSet
    abstract fun bindForgetMemoryTool(impl: ForgetMemoryTool): AgentTool

    @Binds @IntoSet
    abstract fun bindForgetAllMemoriesTool(impl: ForgetAllMemoriesTool): AgentTool

    // ── Android tools ────────────────────────────────────────────────────────

    @Binds @IntoSet
    abstract fun bindOpenAppTool(impl: OpenAppTool): AgentTool

    @Binds @IntoSet
    abstract fun bindGetInstalledAppsTool(impl: GetInstalledAppsTool): AgentTool

    @Binds @IntoSet
    abstract fun bindReadContactsTool(impl: ReadContactsTool): AgentTool

    @Binds @IntoSet
    abstract fun bindGetClipboardTool(impl: GetClipboardTool): AgentTool

    // ── Interaction tools ─────────────────────────────────────────────────────

    @Binds @IntoSet
    abstract fun bindAskUserTool(impl: AskUserTool): AgentTool
}
