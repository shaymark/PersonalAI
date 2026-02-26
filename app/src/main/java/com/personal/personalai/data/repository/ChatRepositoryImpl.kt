package com.personal.personalai.data.repository

import com.personal.personalai.data.local.dao.MessageDao
import com.personal.personalai.data.local.entity.toDomain
import com.personal.personalai.data.local.entity.toEntity
import com.personal.personalai.domain.model.Message
import com.personal.personalai.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ChatRepositoryImpl @Inject constructor(
    private val messageDao: MessageDao
) : ChatRepository {

    override fun getMessages(): Flow<List<Message>> =
        messageDao.getAllMessages().map { entities -> entities.map { it.toDomain() } }

    override suspend fun saveMessage(message: Message): Long =
        messageDao.insertMessage(message.toEntity())

    override suspend fun clearHistory() =
        messageDao.clearAll()
}
