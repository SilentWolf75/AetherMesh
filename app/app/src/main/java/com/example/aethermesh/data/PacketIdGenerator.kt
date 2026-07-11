package com.example.aethermesh.data

import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicInteger

internal class PacketIdSequence(initialValue: Int) {
    private val counter = AtomicInteger(initialValue.coerceIn(1, Int.MAX_VALUE))

    fun next(): Int = counter.updateAndGet { current ->
        if (current == Int.MAX_VALUE) 1 else current + 1
    }
}

object PacketIdGenerator {
    private val sequence = PacketIdSequence(SecureRandom().nextInt(Int.MAX_VALUE - 1) + 1)

    fun next(): Int = sequence.next()
}
