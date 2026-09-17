package com.silentwolf75.aethermesh.data

import android.app.Application
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Drives the real recovery path (saveKeyAndRecover / resumeAtStartup -> batched
 * decrypt -> DB update -> refresh callback). The scope matches
 * AetherMeshRepository.repositoryScope: a plain Job, no exception handler, so
 * any exception escaping recovery would cancel it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class PendingDecryptionRecoveryTest {
    private lateinit var context: Context
    private lateinit var db: DatabaseHelper
    private lateinit var journal: PrefsRecoveryJournal
    private val dbExecutor = Executors.newSingleThreadExecutor()
    private val dbDispatcher = dbExecutor.asCoroutineDispatcher()
    private val scopeJob = Job()
    private val repositoryScope = CoroutineScope(Dispatchers.Default + scopeJob)
    private val refreshes = CopyOnWriteArrayList<Int>()
    private val errors = CopyOnWriteArrayList<Throwable>()
    private val keys = java.util.concurrent.ConcurrentHashMap<String, String>()

    private val chatId = "CHANNEL_Trail"
    private val aad = "10>ffffffff#Trail"

    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("aethermesh.db")
        db = DatabaseHelper(context)
        val prefs = context.getSharedPreferences(AppUiPrefs.FILE, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        journal = PrefsRecoveryJournal(prefs)
    }

    @After fun close() {
        repositoryScope.cancel()
        db.close()
        dbExecutor.shutdownNow()
    }

    private fun recovery(
        scope: CoroutineScope = repositoryScope,
        journal: RecoveryJournal = this.journal,
        batchSize: Int = PendingDecryptionRecovery.DEFAULT_BATCH_SIZE,
        onBatchRecovered: (String, Int) -> Unit = { _, count -> refreshes.add(count) },
        onError: (String?, Throwable) -> Unit = { _, error -> errors.add(error) },
        decrypt: (String, String, String) -> String = { c, k, ctx -> ChatCrypto.decrypt(c, k, ctx) }
    ) = PendingDecryptionRecovery(
        scope = scope,
        dbDispatcher = dbDispatcher,
        workDispatcher = Dispatchers.Default,
        db = db,
        journal = journal,
        keyFor = { keys[it] },
        onBatchRecovered = onBatchRecovered,
        onError = onError,
        batchSize = batchSize,
        decrypt = decrypt
    )

    /** A scope whose process has already died: launched work never runs. */
    private fun deadProcessScope() = CoroutineScope(Dispatchers.Default + Job()).also { it.cancel() }

    private fun storePending(cipher: String, packetId: Int, chat: String = chatId): Long =
        db.insertMessage(
            senderId = 10,
            recipientId = ChatSendPolicy.BROADCAST,
            content = IncomingChatPolicy.ERROR_NO_KEY,
            channel = "Trail",
            packetId = packetId,
            isEncrypted = true,
            pendingCipher = PendingCipher(cipher, chat, aad)
        )

    @Test fun savingKeyRecoversStoredMessagesAndRefreshes() = runBlocking {
        storePending(ChatCrypto.encrypt("first", "trail-key", aad)!!, packetId = 1)
        storePending(ChatCrypto.encrypt("second", "trail-key", aad)!!, packetId = 2)

        var result: PendingDecryptionRecovery.Result? = null
        recovery().saveKeyAndRecover(chatId, saveKey = { keys[chatId] = "trail-key" }) { result = it }.join()

        assertEquals(listOf("first", "second"), db.getAllMessages().map { it.content })
        assertTrue(db.getPendingDecryptions(chatId).isEmpty())
        assertEquals(PendingDecryptionRecovery.Result(chatId, recovered = 2, scanned = 2), result)
        assertEquals(listOf(2), refreshes.toList())
        assertTrue(journal.pending().isEmpty())
        assertTrue(errors.isEmpty())
    }

    @Test fun journalIsWrittenBeforeTheKeyIsStored() {
        var journaledAtSave = emptyList<String>()
        recovery(scope = deadProcessScope()).saveKeyAndRecover(chatId, saveKey = {
            journaledAtSave = journal.pending()
            keys[chatId] = "k"
        })
        assertEquals(listOf(chatId), journaledAtSave)
    }

    @Test fun processDeathAfterKeySaveResumesOnNextStart() = runBlocking {
        storePending(ChatCrypto.encrypt("survived restart", "trail-key", aad)!!, packetId = 1)
        // Key stored, then the process died before recovery ran.
        recovery(scope = deadProcessScope()).saveKeyAndRecover(chatId, saveKey = { keys[chatId] = "trail-key" })
        assertEquals(listOf(chatId), journal.pending())

        var result: PendingDecryptionRecovery.Result? = null
        recovery().resumeAtStartup { result = it }.join()

        assertEquals("survived restart", db.getAllMessages().single().content)
        assertEquals(1, result?.recovered)
        assertTrue(journal.pending().isEmpty())
    }

    @Test fun failedJournalWriteIsReportedAndStartupReconciliationStillRecovers() = runBlocking {
        storePending(ChatCrypto.encrypt("found by reconciliation", "trail-key", aad)!!, packetId = 1)
        val brokenJournal = object : RecoveryJournal {
            override fun add(chatIdentifier: String) = false
            override fun remove(chatIdentifier: String) = false
            override fun pending() = emptyList<String>()
        }
        recovery(scope = deadProcessScope(), journal = brokenJournal)
            .saveKeyAndRecover(chatId, saveKey = { keys[chatId] = "trail-key" })
        assertEquals(1, errors.size)

        recovery(journal = brokenJournal).resumeAtStartup().join()

        assertEquals("found by reconciliation", db.getAllMessages().single().content)
    }

    @Test fun keysImportedOutsideRecoveryAreReconciledAtStartup() = runBlocking {
        storePending(ChatCrypto.encrypt("migrated key", "trail-key", aad)!!, packetId = 1)
        keys[chatId] = "trail-key" // e.g. AppPackageMigration writing keys directly

        recovery().resumeAtStartup().join()

        assertEquals("migrated key", db.getAllMessages().single().content)
    }

    @Test fun reconciliationProbesOnceWhenStoredKeyDoesNotMatch() = runBlocking {
        repeat(10) { storePending("cipher-$it", packetId = it + 1) }
        keys[chatId] = "other-key"
        val calls = AtomicInteger()
        val decrypt: (String, String, String) -> String = { _, _, _ ->
            calls.incrementAndGet()
            ChatCrypto.ERROR_BAD_CONTEXT
        }

        recovery(decrypt = decrypt).resumeAtStartup().join()

        assertEquals(1, calls.get())
        assertEquals(10, db.getPendingDecryptions(chatId).size)
    }

    @Test fun failingRecoveryKeepsJournalAndDoesNotCancelRepositoryScope() = runBlocking {
        storePending(ChatCrypto.encrypt("retry me", "trail-key", aad)!!, packetId = 1)
        val crashing: (String, String, String) -> String = { _, _, _ -> error("decrypt blew up") }

        recovery(decrypt = crashing).saveKeyAndRecover(chatId, saveKey = { keys[chatId] = "trail-key" }).join()

        assertTrue(scopeJob.isActive)
        assertEquals("decrypt blew up", errors.single().message)
        assertEquals(listOf(chatId), journal.pending())
        assertEquals(1, db.getPendingDecryptions(chatId).size)

        // Other work on the same scope still runs, and the next start retries.
        var result: PendingDecryptionRecovery.Result? = null
        recovery().resumeAtStartup { result = it }.join()
        assertEquals(1, result?.recovered)
        assertTrue(journal.pending().isEmpty())
    }

    @Test fun oneFailingChatDoesNotStopStartupForOthers() = runBlocking {
        storePending("boom", packetId = 1, chat = "CHANNEL_Broken")
        storePending(ChatCrypto.encrypt("still recovered", "trail-key", aad)!!, packetId = 2)
        keys["CHANNEL_Broken"] = "k"
        keys[chatId] = "trail-key"
        journal.add("CHANNEL_Broken")
        journal.add(chatId)
        val decrypt: (String, String, String) -> String = { c, k, ctx ->
            if (c == "boom") error("bad row") else ChatCrypto.decrypt(c, k, ctx)
        }

        recovery(decrypt = decrypt).resumeAtStartup().join()

        assertTrue(scopeJob.isActive)
        assertEquals(listOf("CHANNEL_Broken"), journal.pending())
        assertTrue(db.getAllMessages().any { it.content == "still recovered" })
    }

    @Test fun throwingJournalAndCallbacksNeverEscapeToCallerOrScope() = runBlocking {
        storePending(ChatCrypto.encrypt("recovered anyway", "trail-key", aad)!!, packetId = 1)
        val throwingJournal = object : RecoveryJournal {
            override fun add(chatIdentifier: String): Boolean = error("journal add")
            override fun remove(chatIdentifier: String): Boolean = error("journal remove")
            override fun pending(): List<String> = error("journal read")
        }
        val sink = recovery(
            journal = throwingJournal,
            onBatchRecovered = { _, _ -> error("refresh") },
            onError = { _, _ -> error("error sink") }
        )

        // Runs on the caller's thread in the app (the UI thread saving a key).
        sink.saveKeyAndRecover(chatId, saveKey = { keys[chatId] = "trail-key" }) { error("finished callback") }.join()
        sink.resumeAtStartup { error("finished callback") }.join()

        assertTrue(scopeJob.isActive)
        assertEquals("recovered anyway", db.getAllMessages().single().content)
    }

    @Test fun failingRefreshDoesNotSendACompletedRunBackToTheJournal() = runBlocking {
        storePending(ChatCrypto.encrypt("stored", "trail-key", aad)!!, packetId = 1)

        var result: PendingDecryptionRecovery.Result? = null
        recovery(onBatchRecovered = { _, _ -> error("refresh") })
            .saveKeyAndRecover(chatId, saveKey = { keys[chatId] = "trail-key" }) { result = it }.join()

        assertEquals(1, result?.recovered)
        assertTrue(journal.pending().isEmpty())
        assertEquals("refresh", errors.single().message)
    }

    @Test fun keySaveFailureIsTheCallersAndStaysJournaled() {
        try {
            recovery().saveKeyAndRecover(chatId, saveKey = { error("disk full") })
            throw AssertionError("expected the key save failure to reach the caller")
        } catch (e: IllegalStateException) {
            assertEquals("disk full", e.message)
        }
        assertEquals(listOf(chatId), journal.pending())
    }

    @Test fun largeBacklogIsProcessedInBatchesAndSkipsRowsThatStillFail() = runBlocking {
        repeat(60) { i -> storePending(if (i % 4 == 0) "bad-$i" else "good-$i", packetId = i + 1) }
        val decrypt: (String, String, String) -> String = { cipher, _, _ ->
            if (cipher.startsWith("bad")) ChatCrypto.ERROR_BAD_CONTEXT else cipher.removePrefix("good-")
        }

        var result: PendingDecryptionRecovery.Result? = null
        recovery(batchSize = 25, decrypt = decrypt)
            .saveKeyAndRecover(chatId, saveKey = { keys[chatId] = "k" }) { result = it }.join()

        assertEquals(PendingDecryptionRecovery.Result(chatId, recovered = 45, scanned = 60), result)
        // Three batches (25 + 25 + 10), each refreshing the UI once. Every 4th
        // row fails: 7, 6 and 2 of them land in the respective batches.
        assertEquals(listOf(18, 19, 8), refreshes.toList())
        assertEquals(15, db.getPendingDecryptions(chatId).size)
    }

    @Test fun withoutKeyNothingIsTouched() = runBlocking {
        storePending("cipher", packetId = 1)

        var result: PendingDecryptionRecovery.Result? = null
        recovery().saveKeyAndRecover(chatId, saveKey = {}) { result = it }.join()

        assertEquals(PendingDecryptionRecovery.Result(chatId, recovered = 0, scanned = 0), result)
        assertEquals(1, db.getPendingDecryptions(chatId).size)
        assertTrue(refreshes.isEmpty())
    }

    @Test fun recoveryOnlyTouchesItsOwnChat() = runBlocking {
        storePending(ChatCrypto.encrypt("other", "trail-key", aad)!!, packetId = 1, chat = "CHANNEL_Other")

        recovery().saveKeyAndRecover(chatId, saveKey = { keys[chatId] = "trail-key" }).join()

        assertEquals(1, db.getPendingDecryptions("CHANNEL_Other").size)
    }

    @Test fun journalPersistsAcrossInstances() {
        assertTrue(journal.add(chatId))
        assertTrue(journal.add("DM_42"))
        val reopened = PrefsRecoveryJournal(context.getSharedPreferences(AppUiPrefs.FILE, Context.MODE_PRIVATE))
        assertEquals(listOf(chatId, "DM_42").sorted(), reopened.pending())
        assertTrue(reopened.remove(chatId))
        assertEquals(listOf("DM_42"), journal.pending())
    }
}
