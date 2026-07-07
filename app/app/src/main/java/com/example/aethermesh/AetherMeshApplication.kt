package com.example.aethermesh

import android.app.Application
import com.example.aethermesh.data.AetherMeshRepository

class AetherMeshApplication : Application() {

    lateinit var repository: AetherMeshRepository
        private set

    // True while MainActivity is in the foreground; message notifications are
    // suppressed when the user is already looking at the app.
    @Volatile
    var isActivityVisible: Boolean = false

    override fun onCreate() {
        super.onCreate()
        repository = AetherMeshRepository(applicationContext)
    }
}
