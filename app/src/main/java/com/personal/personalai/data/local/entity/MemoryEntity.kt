package com.personal.personalai.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.personal.personalai.domain.model.Memory

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val topic: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

fun MemoryEntity.toDomain() = Memory(
    id = id,
    content = content,
    topic = topic,
    createdAt = createdAt
)

fun Memory.toEntity() = MemoryEntity(
    id = id,
    content = content,
    topic = topic,
    createdAt = createdAt
)
