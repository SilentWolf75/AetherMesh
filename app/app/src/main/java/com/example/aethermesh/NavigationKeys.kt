package com.example.aethermesh

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Main : NavKey

/** Full-screen node details destination (replaces the old Dialog overlay). */
@Serializable data class NodeDetails(val nodeId: Long) : NavKey
