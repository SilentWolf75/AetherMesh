package com.silentwolf75.aethermesh.data

import android.content.SharedPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Chats whose recovery has not finished; must survive the app being closed. */
interface RecoveryJournal {
    /** Returns false when the entry could not be persisted. */
    fun add(chatIdentifier: String): Boolean
    fun remove(chatIdentifier: String): Boolean
    fun pending(): List<String>
}

class PrefsRecoveryJournal(private val prefs: SharedPreferences) : RecoveryJournal {
    companion object {
        const val KEY = "pending_decryption_recovery"
    }

    // commit, not apply: callers need to know the entry reached disk.
    @Synchronized
    override fun add(chatIdentifier: String): Boolean =
        prefs.edit().putStringSet(KEY, pending().toSet() + chatIdentifier).commit()

    @Synchronized
    override fun remove(chatIdentifier: String): Boolean =
        prefs.edit().putStringSet(KEY, pending().toSet() - chatIdentifier).commit()

    @Synchronized
    override fun pending(): List<String> = prefs.getStringSet(KEY, emptySet()).orEmpty().sorted()
}

/**
 * Decrypts stored messages that arrived before their key was saved.
 *
 * Batches: each batch reads and writes on [dbDispatcher], which incoming
 * messages and screen refreshes share, but decrypts on [workDispatcher], so a
 * backlog of 120k-iteration PBKDF2 calls never holds the database thread for
 * more than one short query. Rows are paged by id, so rows that still fail
 * (wrong key) are passed over instead of re-read forever.
 *
 * Durability: [saveKeyAndRecover] journals the chat before the key is stored,
 * and the entry is only cleared when a run completes. [resumeAtStartup] finishes
 * journaled chats, then reconciles the database against stored keys to catch
 * anything the journal missed (a failed journal write, keys imported without
 * going through here).
 *
 * Failures: a run that throws is reported through [onError] and keeps its
 * journal entry. Journal I/O and every callback (including [onError]) are
 * guarded too, so a recovery bug cannot cancel the caller's other coroutines or
 * crash the app. The one exception is [saveKeyAndRecover]'s own `saveKey`
 * argument: that is the caller's operation, and its failure is rethrown.
 */
class PendingDecryptionRecovery(
    private val scope: CoroutineScope,
    private val dbDispatcher: CoroutineDispatcher,
    private val workDispatcher: CoroutineDispatcher,
    private val db: DatabaseHelper,
    private val journal: RecoveryJournal,
    private val keyFor: (chatIdentifier: String) -> String?,
    private val onBatchRecovered: (chatIdentifier: String, recovered: Int) -> Unit,
    private val onError: (chatIdentifier: String?, error: Throwable) -> Unit,
    private val batchSize: Int = DEFAULT_BATCH_SIZE,
    private val decrypt: (cipherText: String, key: String, context: String) -> String =
        { cipherText, key, context -> ChatCrypto.decrypt(cipherText, key, context) }
) {
    companion object {
        const val DEFAULT_BATCH_SIZE = 25
    }

    data class Result(val chatIdentifier: String, val recovered: Int, val scanned: Int)

    private val runLock = Mutex()
    private val queuedRuns = mutableMapOf<String, Int>()

    /**
     * Store a chat key with [saveKey] and recover messages waiting for it. The
     * journal entry is written first, so a process death at any later point
     * still leaves startup a record to resume from.
     */
    fun saveKeyAndRecover(
        chatIdentifier: String,
        saveKey: () -> Unit,
        onFinished: (Result) -> Unit = {}
    ): Job {
        synchronized(queuedRuns) {
            queuedRuns[chatIdentifier] = (queuedRuns[chatIdentifier] ?: 0) + 1
        }
        if (!journalAdd(chatIdentifier)) {
            // Startup reconciliation still finds this chat through its pending
            // rows, so only the journaled fast path is lost.
            report(chatIdentifier, IllegalStateException("Could not persist decryption recovery journal"))
        }
        try {
            saveKey()
        } catch (e: Exception) {
            release(chatIdentifier, countedRun = true, completed = false)
            throw e
        }
        return scope.launch(workDispatcher) {
            var result: Result? = null
            try {
                result = guarded(chatIdentifier) { runLock.withLock { recover(chatIdentifier) } }
            } finally {
                release(chatIdentifier, countedRun = true, completed = result != null)
            }
            result?.let { finished(onFinished, it) }
        }
    }

    /** Finish interrupted recoveries, then reconcile stored rows with stored keys. */
    fun resumeAtStartup(onFinished: (Result) -> Unit = {}): Job = scope.launch(workDispatcher) {
        val journaled = guarded(null) { journal.pending() }.orEmpty()
        for (chatIdentifier in journaled) {
            val result = guarded(chatIdentifier) { runLock.withLock { recover(chatIdentifier) } }
            release(chatIdentifier, countedRun = false, completed = result != null)
            result?.let { finished(onFinished, it) }
        }

        val others = guarded(null) {
            withContext(dbDispatcher) { db.getPendingDecryptionChats() }
        }.orEmpty() - journaled.toSet()
        for (chatIdentifier in others) {
            // One PBKDF2 per chat: only a full run when the newest pending row
            // opens with the stored key, so a chat full of rows for some other
            // key does not burn minutes of CPU on every launch.
            val readable = guarded(chatIdentifier) { newestPendingRowReadable(chatIdentifier) } ?: false
            if (!readable) continue
            guarded(chatIdentifier) { runLock.withLock { recover(chatIdentifier) } }
                ?.let { finished(onFinished, it) }
        }
    }

    // Everything below that calls out (journal, callbacks) is wrapped: a
    // throwing journal or listener must not escape into the caller's scope or,
    // for saveKeyAndRecover, onto the thread that saved the key.

    private suspend fun <T> guarded(chatIdentifier: String?, block: suspend () -> T): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            report(chatIdentifier, e)
            null
        }

    private fun report(chatIdentifier: String?, error: Throwable) {
        try {
            onError(chatIdentifier, error)
        } catch (_: Exception) {
            // The error sink itself failed; nothing left to tell.
        }
    }

    private fun finished(onFinished: (Result) -> Unit, result: Result) {
        try {
            onFinished(result)
        } catch (e: Exception) {
            report(result.chatIdentifier, e)
        }
    }

    private fun journalAdd(chatIdentifier: String): Boolean =
        try {
            journal.add(chatIdentifier)
        } catch (e: Exception) {
            report(chatIdentifier, e)
            false
        }

    private fun release(chatIdentifier: String, countedRun: Boolean, completed: Boolean) {
        synchronized(queuedRuns) {
            val remaining = (queuedRuns[chatIdentifier] ?: 0) - if (countedRun) 1 else 0
            if (remaining > 0) queuedRuns[chatIdentifier] = remaining else queuedRuns.remove(chatIdentifier)
            // A queued run for a newer key keeps the journal entry alive.
            if (!completed || remaining > 0) return
            val removed = try {
                journal.remove(chatIdentifier)
            } catch (e: Exception) {
                report(chatIdentifier, e)
                return
            }
            if (!removed) {
                report(chatIdentifier, IllegalStateException("Could not clear decryption recovery journal"))
            }
        }
    }

    private suspend fun newestPendingRowReadable(chatIdentifier: String): Boolean {
        val key = keyFor(chatIdentifier)?.takeIf { it.isNotEmpty() } ?: return false
        val newest = withContext(dbDispatcher) { db.getNewestPendingDecryption(chatIdentifier) } ?: return false
        return !IncomingChatPolicy.isDecryptFailure(decrypt(newest.cipherText, key, newest.cryptoContext))
    }

    private suspend fun recover(chatIdentifier: String): Result {
        var afterRowId = 0L
        var recovered = 0
        var scanned = 0
        while (true) {
            // Read the key per batch so a key saved mid-run applies to the rest.
            val key = keyFor(chatIdentifier)?.takeIf { it.isNotEmpty() } ?: break
            val batch = withContext(dbDispatcher) {
                db.getPendingDecryptions(chatIdentifier, afterRowId, batchSize)
            }
            if (batch.isEmpty()) break
            val readable = batch.mapNotNull { row ->
                val plain = decrypt(row.cipherText, key, row.cryptoContext)
                if (IncomingChatPolicy.isDecryptFailure(plain)) null else row.rowId to plain
            }
            if (readable.isNotEmpty()) {
                withContext(dbDispatcher) { db.resolvePendingDecryptions(readable) }
                // The rows are already stored; a failing refresh must not mark
                // the run failed and send it back through the journal.
                try {
                    onBatchRecovered(chatIdentifier, readable.size)
                } catch (e: Exception) {
                    report(chatIdentifier, e)
                }
            }
            recovered += readable.size
            scanned += batch.size
            afterRowId = batch.last().rowId
        }
        return Result(chatIdentifier, recovered, scanned)
    }
}
