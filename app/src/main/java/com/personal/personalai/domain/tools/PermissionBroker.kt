package com.personal.personalai.domain.tools

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Broker that lets [AgentLoopUseCase] suspend the agent loop until the user grants a permission.
 *
 * Flow:
 * 1. Agent loop calls [requestAndAwait] → emits [Request] to [incoming] and suspends.
 * 2. ChatViewModel observes [incoming] → sets pendingPermissionRequest in UiState.
 * 3. ChatScreen fires the appropriate ActivityResultLauncher.
 * 4. User grants or denies → ChatViewModel calls [resolve].
 * 5. [requestAndAwait] resumes with `true` (granted) or `false` (denied).
 */
@Singleton
class PermissionBroker @Inject constructor() {

    data class Request(val id: String, val permission: String)

    private val _incoming = MutableSharedFlow<Request>(extraBufferCapacity = 1)

    /** ViewModel collects this to show the permission request. */
    val incoming: SharedFlow<Request> = _incoming

    private val pending = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()

    /** Called by [AgentLoopUseCase]: suspends until the UI resolves the permission request. */
    suspend fun requestAndAwait(permission: String): Boolean {
        val id = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<Boolean>()
        pending[id] = deferred
        _incoming.emit(Request(id, permission))
        return deferred.await()
    }

    /** Called by ChatViewModel with the system result (true = granted, false = denied). */
    fun resolve(requestId: String, granted: Boolean) {
        pending.remove(requestId)?.complete(granted)
    }
}
