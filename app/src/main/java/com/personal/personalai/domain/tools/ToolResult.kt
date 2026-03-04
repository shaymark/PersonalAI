package com.personal.personalai.domain.tools

sealed class ToolResult {
    data class Success(val data: String) : ToolResult()
    data class PermissionDenied(val permission: String) : ToolResult()
    data class Error(val message: String) : ToolResult()

    fun toJson(): String = when (this) {
        is Success -> data
        is PermissionDenied -> """{"error":"Permission denied: $permission"}"""
        is Error -> """{"error":"$message"}"""
    }
}
