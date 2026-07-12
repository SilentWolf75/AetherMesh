package com.example.aethermesh

import android.app.Application
import com.example.aethermesh.data.AetherMeshRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PendingNotificationChat(
    val channel: String? = null,
    val dmPeerId: Long? = null
)

class AetherMeshApplication : Application() {

    lateinit var repository: AetherMeshRepository
        private set

    // True while MainActivity is in the foreground; message notifications are
    // suppressed when the user is already looking at the app.
    @Volatile
    var isActivityVisible: Boolean = false

    private val _pendingNotificationChat = MutableStateFlow<PendingNotificationChat?>(null)
    val pendingNotificationChat: StateFlow<PendingNotificationChat?> =
        _pendingNotificationChat.asStateFlow()

    fun queueNotificationChat(channel: String?, dmPeerId: Long?) {
        if (channel.isNullOrBlank() && (dmPeerId == null || dmPeerId == 0L)) return
        _pendingNotificationChat.value = PendingNotificationChat(
            channel = channel?.takeIf { it.isNotBlank() },
            dmPeerId = dmPeerId?.takeIf { it != 0L }
        )
    }

    fun consumeNotificationChat(): PendingNotificationChat? {
        val link = _pendingNotificationChat.value ?: return null
        _pendingNotificationChat.value = null
        return link
    }

    override fun onCreate() {
        super.onCreate()
        repository = AetherMeshRepository(applicationContext)
    }
}
