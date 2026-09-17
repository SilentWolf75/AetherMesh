package com.silentwolf75.aethermesh.data

import com.silentwolf75.aethermesh.proto.NodeIdentity
import java.security.MessageDigest
import java.util.Locale

/**
 * Identity keys as the phone sees them.
 *
 * The phone never verifies a signature itself — it has no curve implementation
 * on minSdk 24 and no keys of its own. Its radio does that, over an
 * authenticated link, and reports the verdict; this file turns that verdict into
 * something a person can act on, and computes the fingerprint both ends show.
 */
object NodeIdentityPolicy {

    /** Matches the firmware's identity::formatFingerprint so the two agree aloud. */
    private const val FINGERPRINT_DOMAIN = "AMID1-fp"
    private const val KEY_BYTES = 32

    enum class State {
        /** No announcement heard yet — messages to this node cannot be sealed. */
        UNKNOWN,

        /** First key seen for this node; stored and in use. */
        LEARNED,

        /** Matches the key already on file. */
        KNOWN,

        /** Replaced by a properly signed newer key. Legitimate, but worth seeing. */
        ROTATED,

        /** A different key claimed this node's id. The stored key was kept. */
        CONFLICT,
    }

    fun stateOf(trust: NodeIdentity.Trust?): State = when (trust) {
        NodeIdentity.Trust.FIRST_USE -> State.LEARNED
        NodeIdentity.Trust.KNOWN -> State.KNOWN
        NodeIdentity.Trust.ROTATED -> State.ROTATED
        NodeIdentity.Trust.CONFLICT -> State.CONFLICT
        else -> State.UNKNOWN
    }

    /** True while direct messages to this node are being sealed to its key. */
    fun isSealable(state: State): Boolean = state == State.LEARNED || state == State.KNOWN

    /**
     * True when the user has to be shown something. A rotation is included: it
     * is indistinguishable from someone else reflashing that node.
     */
    fun needsAttention(state: State): Boolean = state == State.CONFLICT || state == State.ROTATED

    /**
     * "A1B2-C3D4-E5F6-7890" over the signing key, identical to what the node
     * prints, so the two can be read against each other.
     */
    fun fingerprint(ed25519Public: ByteArray?): String {
        if (ed25519Public == null || ed25519Public.size != KEY_BYTES) return ""
        if (ed25519Public.all { it == 0.toByte() }) return ""
        val digest = MessageDigest.getInstance("SHA-256").apply {
            update(FINGERPRINT_DOMAIN.toByteArray(Charsets.US_ASCII))
            update(ed25519Public)
        }.digest()
        return buildString {
            for (i in 0 until 8) {
                append(String.format(Locale.US, "%02X", digest[i]))
                if (i % 2 == 1 && i != 7) append('-')
            }
        }
    }

    /**
     * An all-zero X25519 key makes every shared secret zero, so a node
     * announcing one is never treated as having an identity.
     */
    fun keyIsUsable(key: ByteArray?): Boolean =
        key != null && key.size == KEY_BYTES && key.any { it != 0.toByte() }

    /** Short label for the node list and details screen. */
    fun label(state: State, spanish: Boolean): String = when (state) {
        State.UNKNOWN -> if (spanish) "sin clave" else "no key yet"
        State.LEARNED, State.KNOWN -> if (spanish) "clave verificada" else "key verified"
        State.ROTATED -> if (spanish) "clave cambiada" else "key changed"
        State.CONFLICT -> if (spanish) "CLAVE EN CONFLICTO" else "KEY CONFLICT"
    }

    /** What the user should actually do about it. */
    fun explanation(state: State, spanish: Boolean): String = when (state) {
        State.UNKNOWN -> if (spanish)
            "Este nodo aún no ha anunciado su clave. Los mensajes directos no van sellados."
        else
            "This node has not announced a key yet. Direct messages to it are not sealed."
        State.LEARNED, State.KNOWN -> if (spanish)
            "Los mensajes directos a este nodo van sellados con su clave. Compara la huella en persona para confirmar."
        else
            "Direct messages to this node are sealed to its key. Compare the fingerprint in person to be sure of who it is."
        State.ROTATED -> if (spanish)
            "Este nodo anunció una clave nueva. Es normal tras reinstalar el firmware, pero se ve igual si otra persona reprograma el nodo. Confirma la huella antes de enviar algo sensible."
        else
            "This node announced a new key. That is normal after reinstalling firmware, but it looks the same if someone else reflashed the node. Check the fingerprint before sending anything sensitive."
        State.CONFLICT -> if (spanish)
            "Otra clave reclamó el id de este nodo. Se conservó la clave original y no se sella nada hasta que lo confirmes. Verifica la huella en persona."
        else
            "A different key claimed this node's id. The original key was kept and nothing is being sealed to it. Verify the fingerprint in person before trusting it."
    }
}
