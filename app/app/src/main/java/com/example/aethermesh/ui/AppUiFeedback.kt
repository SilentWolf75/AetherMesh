package com.example.aethermesh.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/** One-shot in-app messages (replaces scattered Toasts). */
data class UiMessage(
    val text: String,
    val actionLabel: String? = null,
    val duration: SnackbarDuration = SnackbarDuration.Short,
    val onAction: (() -> Unit)? = null
)

object AppUiFeedback {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 16)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    fun show(text: String, duration: SnackbarDuration = SnackbarDuration.Short) {
        _messages.tryEmit(UiMessage(text = text, duration = duration))
    }

    fun show(
        text: String,
        actionLabel: String,
        duration: SnackbarDuration = SnackbarDuration.Long,
        onAction: () -> Unit
    ) {
        _messages.tryEmit(
            UiMessage(
                text = text,
                actionLabel = actionLabel,
                duration = duration,
                onAction = onAction
            )
        )
    }

    fun bind(hostState: SnackbarHostState, scope: CoroutineScope = AppUiFeedback.scope) {
        scope.launch {
            messages.collect { msg ->
                val result = hostState.showSnackbar(
                    message = msg.text,
                    actionLabel = msg.actionLabel,
                    duration = msg.duration,
                    withDismissAction = true
                )
                if (result == SnackbarResult.ActionPerformed) {
                    msg.onAction?.invoke()
                }
            }
        }
    }
}
