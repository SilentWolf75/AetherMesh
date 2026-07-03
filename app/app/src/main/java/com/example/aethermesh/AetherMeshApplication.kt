package com.example.aethermesh

import android.app.Application
import com.example.aethermesh.data.AetherMeshRepository

class AetherMeshApplication : Application() {
    
    lateinit var repository: AetherMeshRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = AetherMeshRepository(applicationContext)
    }
}
