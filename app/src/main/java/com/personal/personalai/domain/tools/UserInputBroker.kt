package com.personal.personalai.domain.tools

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Broker that lets [AskUserTool] suspend the agent loop until the user types an answer.
 *
 * Flow:
 * 1. Tool calls [askAndAwait] → emits [Request] to [incoming] and suspends.
 * 2. ChatViewModel observes [incoming] → shows the question card in the UI.
 * 3. User types their answer → ChatViewModel calls [answer].
 * 4. [askAndAwait] resumes with the answer string → tool returns the result to the agent loop.
 */
@Singleton
class UserInputBroker @Inject constructor() {

    data class Request(val id: String, val question: String)

    private val _incoming = MutableSharedFlow<Request>(extraBufferCapacity = 1)

    /** ViewModel collects this to show the question card. */
    val incoming: SharedFlow<Request> = _incoming

    private val pending = ConcurrentHashMap<String, CompletableDeferred<String>>()

    /** Called by [AskUserTool]: suspends until the user submits an answer. */
    suspend fun askAndAwait(question: String): String {
        val id = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<String>()
        pending[id] = deferred
        _incoming.emit(Request(id, question))
        return deferred.await()
    }

    /** Called by ChatViewModel when the user submits their answer. */
    fun answer(requestId: String, text: String) {
        pending.remove(requestId)?.complete(text)
    }
}
