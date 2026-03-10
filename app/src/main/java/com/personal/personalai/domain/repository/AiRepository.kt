package com.personal.personalai.domain.repository

import com.personal.personalai.domain.model.Memory
import com.personal.personalai.domain.model.Message
import com.personal.personalai.domain.tools.AgentResponse
import com.personal.personalai.domain.tools.AgentTool
import org.json.JSONArray
import java.io.File

interface AiRepository {
    suspend fun sendMessageWithTools(
        conversationItems: JSONArray,
        memories: List<Memory>,
        tools: List<AgentTool>
    ): Result<AgentResponse>
    suspend fun transcribeAudio(audioFile: File): Result<String>
}
