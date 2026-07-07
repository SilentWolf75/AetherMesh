package com.example.aethermesh.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "aethermesh.db"
        private const val DATABASE_VERSION = 13

        // Telemetry history (append-only) for per-node battery/voltage graphs
        const val TABLE_TELEMETRY = "telemetry_history"
        const val COL_TEL_ID = "id"
        const val COL_TEL_NODE_ID = "node_id"
        const val COL_TEL_TIMESTAMP = "timestamp"
        const val COL_TEL_BATTERY = "battery"
        const val COL_TEL_VOLTAGE = "voltage"
        const val COL_TEL_CHARGING = "is_charging"
        // Keep this many most-recent samples per node; older rows are pruned on insert.
        private const val TELEMETRY_KEEP_PER_NODE = 500

        // Messages Table
        const val TABLE_MESSAGES = "messages"
        const val COL_MSG_ID = "id"
        const val COL_MSG_SENDER = "sender_id"
        const val COL_MSG_RECIPIENT = "recipient_id"
        const val COL_MSG_CONTENT = "content"
        const val COL_MSG_TIMESTAMP = "timestamp"
        const val COL_MSG_CHANNEL = "channel"
        const val COL_MSG_PACKET_ID = "packet_id"
        const val COL_MSG_STATUS = "status"
        const val COL_MSG_IS_ENCRYPTED = "is_encrypted"

        // Nodes Table
        const val TABLE_NODES = "nodes"
        const val COL_NODE_ID = "node_id"
        const val COL_NODE_NAME = "name"
        const val COL_NODE_SHORT_NAME = "short_name"
        const val COL_NODE_BATTERY = "battery"
        const val COL_NODE_LATITUDE = "latitude"
        const val COL_NODE_LONGITUDE = "longitude"
        const val COL_NODE_LAST_ACTIVE = "last_active"
        const val COL_NODE_MODEL = "model"
        const val COL_NODE_UPTIME = "uptime_seconds"
        const val COL_NODE_FW_VERSION = "firmware_version"
        const val COL_NODE_IS_CHARGING = "is_charging"
        // Last-heard signal of this node's traffic (persisted so it survives an app
        // restart instead of waiting for the node's next telemetry). 0 = unknown /
        // local node (its own loopback carries no rx signal).
        const val COL_NODE_RSSI = "last_rssi"
        const val COL_NODE_SNR = "last_snr"
        const val COL_NODE_VOLTAGE = "battery_voltage"
        const val COL_NODE_POS_PRECISION = "position_precision"

        // Encryption Keys Table
        const val TABLE_KEYS = "encryption_keys"
        const val COL_KEY_CHAT_ID = "chat_identifier" // channel name or peer MAC/nodeID
        const val COL_KEY_VAL = "encryption_key"

        // Range Test Logs Table
        const val TABLE_RANGE_TEST_LOGS = "range_test_logs"
        const val COL_LOG_ID = "id"
        const val COL_LOG_TIMESTAMP = "timestamp"
        const val COL_LOG_TARGET_ID = "target_id"
        const val COL_LOG_LATITUDE = "latitude"
        const val COL_LOG_LONGITUDE = "longitude"
        const val COL_LOG_RSSI = "rssi"
        const val COL_LOG_SNR = "snr"
        const val COL_LOG_SUCCESS = "success"
        // Signal quality of the ping AS HEARD BY THE TARGET (reported back in the ACK).
        // NULL when the target didn't report it (old firmware) or the ping timed out.
        const val COL_LOG_REMOTE_RSSI = "remote_rssi"
        const val COL_LOG_REMOTE_SNR = "remote_snr"
        // Phone GPS metadata at ping time (NULL when no fresh fix): speed in m/s,
        // horizontal accuracy in meters. Lets drive tests correlate loss with speed
        // and filter rows with poor fixes.
        const val COL_LOG_SPEED_MPS = "speed_mps"
        const val COL_LOG_GPS_ACCURACY_M = "gps_accuracy_m"

        // Channels Table
        const val TABLE_CHANNELS = "channels"
        const val COL_CHAN_ID = "id"
        const val COL_CHAN_NAME = "name"
        const val COL_CHAN_PSK = "psk"
        const val COL_CHAN_UPLINK = "uplink_enabled"
        const val COL_CHAN_DOWNLINK = "downlink_enabled"
        const val COL_CHAN_POSITION = "position_enabled"
        const val COL_CHAN_PRECISE = "precise_location"
        const val COL_CHAN_PRECISION_MILES = "precision_miles"
        const val COL_CHAN_PRIMARY = "is_primary"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createMessagesTable = """
            CREATE TABLE $TABLE_MESSAGES (
                $COL_MSG_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_MSG_SENDER INTEGER,
                $COL_MSG_RECIPIENT INTEGER,
                $COL_MSG_CONTENT TEXT,
                $COL_MSG_TIMESTAMP INTEGER,
                $COL_MSG_CHANNEL TEXT,
                $COL_MSG_PACKET_ID INTEGER DEFAULT 0,
                $COL_MSG_STATUS TEXT DEFAULT 'SENT',
                $COL_MSG_IS_ENCRYPTED INTEGER DEFAULT 0
            )
        """.trimIndent()

        val createNodesTable = """
            CREATE TABLE $TABLE_NODES (
                $COL_NODE_ID INTEGER PRIMARY KEY,
                $COL_NODE_NAME TEXT,
                $COL_NODE_SHORT_NAME TEXT DEFAULT '',
                $COL_NODE_BATTERY INTEGER,
                $COL_NODE_LATITUDE REAL,
                $COL_NODE_LONGITUDE REAL,
                $COL_NODE_LAST_ACTIVE INTEGER,
                $COL_NODE_MODEL TEXT,
                $COL_NODE_UPTIME INTEGER,
                $COL_NODE_FW_VERSION TEXT,
                $COL_NODE_IS_CHARGING INTEGER DEFAULT 0,
                $COL_NODE_RSSI REAL DEFAULT 0,
                $COL_NODE_SNR REAL DEFAULT 0,
                $COL_NODE_VOLTAGE REAL DEFAULT 0,
                $COL_NODE_POS_PRECISION INTEGER DEFAULT 0
            )
        """.trimIndent()

        val createKeysTable = """
            CREATE TABLE $TABLE_KEYS (
                $COL_KEY_CHAT_ID TEXT PRIMARY KEY,
                $COL_KEY_VAL TEXT
            )
        """.trimIndent()

        val createRangeLogsTable = """
            CREATE TABLE $TABLE_RANGE_TEST_LOGS (
                $COL_LOG_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_LOG_TIMESTAMP INTEGER,
                $COL_LOG_TARGET_ID INTEGER,
                $COL_LOG_LATITUDE REAL,
                $COL_LOG_LONGITUDE REAL,
                $COL_LOG_RSSI REAL,
                $COL_LOG_SNR REAL,
                $COL_LOG_SUCCESS INTEGER,
                $COL_LOG_REMOTE_RSSI REAL,
                $COL_LOG_REMOTE_SNR REAL,
                $COL_LOG_SPEED_MPS REAL,
                $COL_LOG_GPS_ACCURACY_M REAL
            )
        """.trimIndent()

        val createChannelsTable = """
            CREATE TABLE $TABLE_CHANNELS (
                $COL_CHAN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_CHAN_NAME TEXT UNIQUE,
                $COL_CHAN_PSK TEXT,
                $COL_CHAN_UPLINK INTEGER DEFAULT 1,
                $COL_CHAN_DOWNLINK INTEGER DEFAULT 1,
                $COL_CHAN_POSITION INTEGER DEFAULT 1,
                $COL_CHAN_PRECISE INTEGER DEFAULT 1,
                $COL_CHAN_PRECISION_MILES REAL DEFAULT 0.0,
                $COL_CHAN_PRIMARY INTEGER DEFAULT 0
            )
        """.trimIndent()

        val insertDefaultChannel = """
            INSERT OR IGNORE INTO $TABLE_CHANNELS 
            ($COL_CHAN_NAME, $COL_CHAN_PSK, $COL_CHAN_UPLINK, $COL_CHAN_DOWNLINK, $COL_CHAN_POSITION, $COL_CHAN_PRECISE, $COL_CHAN_PRECISION_MILES, $COL_CHAN_PRIMARY)
            VALUES ('StandardMesh', 'AQ==', 1, 1, 1, 1, 0.0, 1)
        """.trimIndent()

        val createTelemetryTable = """
            CREATE TABLE $TABLE_TELEMETRY (
                $COL_TEL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_TEL_NODE_ID INTEGER,
                $COL_TEL_TIMESTAMP INTEGER,
                $COL_TEL_BATTERY INTEGER,
                $COL_TEL_VOLTAGE REAL,
                $COL_TEL_CHARGING INTEGER DEFAULT 0
            )
        """.trimIndent()

        db.execSQL(createMessagesTable)
        db.execSQL(createNodesTable)
        db.execSQL(createTelemetryTable)
        db.execSQL(createKeysTable)
        db.execSQL(createRangeLogsTable)
        db.execSQL(createChannelsTable)
        db.execSQL(insertDefaultChannel)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE $TABLE_NODES ADD COLUMN $COL_NODE_UPTIME INTEGER DEFAULT 0")
            db.execSQL("ALTER TABLE $TABLE_NODES ADD COLUMN $COL_NODE_FW_VERSION TEXT DEFAULT ''")
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE $TABLE_MESSAGES ADD COLUMN $COL_MSG_PACKET_ID INTEGER DEFAULT 0")
            db.execSQL("ALTER TABLE $TABLE_MESSAGES ADD COLUMN $COL_MSG_STATUS TEXT DEFAULT 'SENT'")
        }
        if (oldVersion < 4) {
            // Add is_encrypted to messages table
            db.execSQL("ALTER TABLE $TABLE_MESSAGES ADD COLUMN $COL_MSG_IS_ENCRYPTED INTEGER DEFAULT 0")
            
            // Create keys and range logs tables
            val createKeysTable = """
                CREATE TABLE $TABLE_KEYS (
                    $COL_KEY_CHAT_ID TEXT PRIMARY KEY,
                    $COL_KEY_VAL TEXT
                )
            """.trimIndent()

            val createRangeLogsTable = """
                CREATE TABLE $TABLE_RANGE_TEST_LOGS (
                    $COL_LOG_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    $COL_LOG_TIMESTAMP INTEGER,
                    $COL_LOG_TARGET_ID INTEGER,
                    $COL_LOG_LATITUDE REAL,
                    $COL_LOG_LONGITUDE REAL,
                    $COL_LOG_RSSI REAL,
                    $COL_LOG_SNR REAL,
                    $COL_LOG_SUCCESS INTEGER
                )
            """.trimIndent()

            db.execSQL(createKeysTable)
            db.execSQL(createRangeLogsTable)
        }
        if (oldVersion < 5) {
            val createChannelsTable = """
                CREATE TABLE IF NOT EXISTS $TABLE_CHANNELS (
                    $COL_CHAN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    $COL_CHAN_NAME TEXT UNIQUE,
                    $COL_CHAN_PSK TEXT,
                    $COL_CHAN_UPLINK INTEGER DEFAULT 1,
                    $COL_CHAN_DOWNLINK INTEGER DEFAULT 1,
                    $COL_CHAN_POSITION INTEGER DEFAULT 1,
                    $COL_CHAN_PRECISE INTEGER DEFAULT 1,
                    $COL_CHAN_PRECISION_MILES REAL DEFAULT 0.0,
                    $COL_CHAN_PRIMARY INTEGER DEFAULT 0
                )
            """.trimIndent()

            val insertDefaultChannel = """
                INSERT OR IGNORE INTO $TABLE_CHANNELS 
                ($COL_CHAN_NAME, $COL_CHAN_PSK, $COL_CHAN_UPLINK, $COL_CHAN_DOWNLINK, $COL_CHAN_POSITION, $COL_CHAN_PRECISE, $COL_CHAN_PRECISION_MILES, $COL_CHAN_PRIMARY)
                VALUES ('StandardMesh', 'AQ==', 1, 1, 1, 1, 0.0, 1)
            """.trimIndent()

            db.execSQL(createChannelsTable)
            db.execSQL(insertDefaultChannel)
        }
        if (oldVersion < 6) {
            try {
                db.execSQL("ALTER TABLE $TABLE_NODES ADD COLUMN $COL_NODE_SHORT_NAME TEXT DEFAULT ''")
            } catch (e: Exception) {
                android.util.Log.e("DatabaseHelper", "Failed to add short_name column: ${e.message}")
            }
        }
        if (oldVersion < 7) {
            try {
                db.execSQL("ALTER TABLE $TABLE_RANGE_TEST_LOGS ADD COLUMN $COL_LOG_REMOTE_RSSI REAL")
                db.execSQL("ALTER TABLE $TABLE_RANGE_TEST_LOGS ADD COLUMN $COL_LOG_REMOTE_SNR REAL")
            } catch (e: Exception) {
                android.util.Log.e("DatabaseHelper", "Failed to add remote signal columns: ${e.message}")
            }
        }
        if (oldVersion < 8) {
            try {
                db.execSQL("ALTER TABLE $TABLE_RANGE_TEST_LOGS ADD COLUMN $COL_LOG_SPEED_MPS REAL")
                db.execSQL("ALTER TABLE $TABLE_RANGE_TEST_LOGS ADD COLUMN $COL_LOG_GPS_ACCURACY_M REAL")
            } catch (e: Exception) {
                android.util.Log.e("DatabaseHelper", "Failed to add GPS metadata columns: ${e.message}")
            }
        }
        if (oldVersion < 9) {
            try {
                db.execSQL("ALTER TABLE $TABLE_NODES ADD COLUMN $COL_NODE_IS_CHARGING INTEGER DEFAULT 0")
            } catch (e: Exception) {
                android.util.Log.e("DatabaseHelper", "Failed to add is_charging column: ${e.message}")
            }
        }
        if (oldVersion < 10) {
            try {
                db.execSQL("ALTER TABLE $TABLE_NODES ADD COLUMN $COL_NODE_RSSI REAL DEFAULT 0")
                db.execSQL("ALTER TABLE $TABLE_NODES ADD COLUMN $COL_NODE_SNR REAL DEFAULT 0")
            } catch (e: Exception) {
                android.util.Log.e("DatabaseHelper", "Failed to add signal columns: ${e.message}")
            }
        }
        if (oldVersion < 11) {
            try {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS $TABLE_TELEMETRY (
                        $COL_TEL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                        $COL_TEL_NODE_ID INTEGER,
                        $COL_TEL_TIMESTAMP INTEGER,
                        $COL_TEL_BATTERY INTEGER,
                        $COL_TEL_VOLTAGE REAL,
                        $COL_TEL_CHARGING INTEGER DEFAULT 0
                    )
                """.trimIndent())
            } catch (e: Exception) {
                android.util.Log.e("DatabaseHelper", "Failed to add telemetry_history table: ${e.message}")
            }
        }
        if (oldVersion < 12) {
            try {
                db.execSQL("ALTER TABLE $TABLE_NODES ADD COLUMN $COL_NODE_VOLTAGE REAL DEFAULT 0")
            } catch (e: Exception) {
                android.util.Log.e("DatabaseHelper", "Failed to add node voltage column: ${e.message}")
            }
        }
        if (oldVersion < 13) {
            try {
                db.execSQL("ALTER TABLE $TABLE_NODES ADD COLUMN $COL_NODE_POS_PRECISION INTEGER DEFAULT 0")
            } catch (e: Exception) {
                android.util.Log.e("DatabaseHelper", "Failed to add position precision column: ${e.message}")
            }
        }
    }

    // Append a telemetry sample and prune old rows for that node.
    fun insertTelemetrySample(nodeId: Long, battery: Int, voltage: Float, isCharging: Boolean) {
        if (nodeId == 0L) return
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COL_TEL_NODE_ID, nodeId)
            put(COL_TEL_TIMESTAMP, System.currentTimeMillis())
            put(COL_TEL_BATTERY, battery)
            put(COL_TEL_VOLTAGE, voltage.toDouble())
            put(COL_TEL_CHARGING, if (isCharging) 1 else 0)
        }
        db.insert(TABLE_TELEMETRY, null, values)
        // Prune: keep only the newest TELEMETRY_KEEP_PER_NODE rows for this node.
        db.execSQL(
            "DELETE FROM $TABLE_TELEMETRY WHERE $COL_TEL_NODE_ID = ? AND $COL_TEL_ID NOT IN " +
            "(SELECT $COL_TEL_ID FROM $TABLE_TELEMETRY WHERE $COL_TEL_NODE_ID = ? ORDER BY $COL_TEL_ID DESC LIMIT $TELEMETRY_KEEP_PER_NODE)",
            arrayOf(nodeId.toString(), nodeId.toString())
        )
    }

    fun getTelemetryHistory(nodeId: Long): List<TelemetrySample> {
        val db = this.readableDatabase
        val list = mutableListOf<TelemetrySample>()
        val cursor = db.rawQuery(
            "SELECT $COL_TEL_TIMESTAMP, $COL_TEL_BATTERY, $COL_TEL_VOLTAGE, $COL_TEL_CHARGING FROM $TABLE_TELEMETRY WHERE $COL_TEL_NODE_ID = ? ORDER BY $COL_TEL_TIMESTAMP ASC",
            arrayOf(nodeId.toString())
        )
        if (cursor.moveToFirst()) {
            do {
                list.add(TelemetrySample(
                    timestamp = cursor.getLong(0),
                    battery = cursor.getInt(1),
                    voltage = cursor.getDouble(2).toFloat(),
                    isCharging = cursor.getInt(3) != 0
                ))
            } while (cursor.moveToNext())
        }
        cursor.close()
        return list
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // The same applicationId gets installed from two source trees (the original
        // project and this copy), so an older build can meet a newer schema. The
        // default behavior is a startup crash; recreate the schema instead. Local
        // history is lost, but the app opens.
        android.util.Log.w("DatabaseHelper", "DB downgrade v$oldVersion -> v$newVersion: recreating schema (local data reset).")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_MESSAGES")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NODES")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_KEYS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_RANGE_TEST_LOGS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_CHANNELS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_TELEMETRY")
        onCreate(db)
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        try {
            db.execSQL("UPDATE $TABLE_CHANNELS SET $COL_CHAN_NAME = 'StandardMesh' WHERE $COL_CHAN_NAME = 'LongFast' AND $COL_CHAN_PRIMARY = 1")
        } catch (e: Exception) {
            android.util.Log.e("DatabaseHelper", "Failed to migrate default channel name: ${e.message}")
        }
        deduplicateNodes(db)
    }

    fun deduplicateNodes(db: SQLiteDatabase) {
        val cursor = db.rawQuery("SELECT $COL_NODE_ID FROM $TABLE_NODES ORDER BY $COL_NODE_ID DESC", null)
        val seenLower16 = mutableSetOf<Long>()
        val duplicatesToRemove = mutableListOf<Long>()
        
        if (cursor.moveToFirst()) {
            do {
                val nodeId = cursor.getLong(0)
                val lower16 = nodeId and 0xFFFFL
                if (seenLower16.contains(lower16)) {
                    duplicatesToRemove.add(nodeId)
                } else {
                    seenLower16.add(lower16)
                }
            } while (cursor.moveToNext())
        }
        cursor.close()
        
        duplicatesToRemove.forEach { dupId ->
            val lower16 = dupId and 0xFFFFL
            val canonicalCursor = db.rawQuery(
                "SELECT $COL_NODE_ID FROM $TABLE_NODES WHERE ($COL_NODE_ID & 65535) = CAST(? AS INTEGER) AND $COL_NODE_ID != CAST(? AS INTEGER)",
                arrayOf(lower16.toString(), dupId.toString())
            )
            if (canonicalCursor.moveToFirst()) {
                val canonicalId = canonicalCursor.getLong(0)
                db.delete(TABLE_NODES, "$COL_NODE_ID = ?", arrayOf(dupId.toString()))
                
                val msgValues = ContentValues().apply { put(COL_MSG_SENDER, canonicalId) }
                db.update(TABLE_MESSAGES, msgValues, "$COL_MSG_SENDER = ?", arrayOf(dupId.toString()))
                
                val rxValues = ContentValues().apply { put(COL_MSG_RECIPIENT, canonicalId) }
                db.update(TABLE_MESSAGES, rxValues, "$COL_MSG_RECIPIENT = ?", arrayOf(dupId.toString()))
            }
            canonicalCursor.close()
        }
    }

    // Message insertion
    fun insertMessage(
        senderId: Long,
        recipientId: Long,
        content: String,
        channel: String = "General",
        packetId: Int = 0,
        status: String = "SENT",
        isEncrypted: Boolean = false
    ): Long {
        val db = this.writableDatabase
        val canonicalSender = resolveCanonicalNodeId(db, senderId)
        val canonicalRecipient = resolveCanonicalNodeId(db, recipientId)
        val values = ContentValues().apply {
            put(COL_MSG_SENDER, canonicalSender)
            put(COL_MSG_RECIPIENT, canonicalRecipient)
            put(COL_MSG_CONTENT, content)
            put(COL_MSG_TIMESTAMP, System.currentTimeMillis())
            put(COL_MSG_CHANNEL, channel)
            put(COL_MSG_PACKET_ID, packetId)
            put(COL_MSG_STATUS, status)
            put(COL_MSG_IS_ENCRYPTED, if (isEncrypted) 1 else 0)
        }
        return db.insert(TABLE_MESSAGES, null, values)
    }

    // Update message status by packetId
    fun updateMessageStatus(packetId: Int, status: String) {
        if (packetId == 0) return
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COL_MSG_STATUS, status)
        }
        db.update(TABLE_MESSAGES, values, "$COL_MSG_PACKET_ID = ?", arrayOf(packetId.toString()))
    }

    fun updateMessageStatusById(messageId: Long, status: String) {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COL_MSG_STATUS, status)
        }
        db.update(TABLE_MESSAGES, values, "$COL_MSG_ID = ?", arrayOf(messageId.toString()))
    }

    fun markTimedOutPendingMessages(cutoffTimestamp: Long): Int {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COL_MSG_STATUS, "FAILED")
        }
        return db.update(
            TABLE_MESSAGES,
            values,
            "$COL_MSG_STATUS = ? AND $COL_MSG_TIMESTAMP <= ?",
            arrayOf("PENDING", cutoffTimestamp.toString())
        )
    }

    // Retrieve all messages for a specific chat (either a named group channel or direct
    // messages with a specific node). For channel chats, pass the channel name in `channel`.
    fun getMessages(chatId: Long, isChannel: Boolean, channel: String = "General"): List<ChatMessage> {
        val db = this.readableDatabase
        val canonicalChatId = resolveCanonicalNodeId(db, chatId)
        val list = mutableListOf<ChatMessage>()

        val query = if (isChannel) {
            "SELECT * FROM $TABLE_MESSAGES WHERE $COL_MSG_CHANNEL = ? ORDER BY $COL_MSG_TIMESTAMP ASC"
        } else {
            "SELECT * FROM $TABLE_MESSAGES WHERE ($COL_MSG_SENDER = ? AND $COL_MSG_CHANNEL = '') OR ($COL_MSG_RECIPIENT = ? AND $COL_MSG_CHANNEL = '') ORDER BY $COL_MSG_TIMESTAMP ASC"
        }

        val args = if (isChannel) arrayOf(channel) else arrayOf(canonicalChatId.toString(), canonicalChatId.toString())

        val cursor = db.rawQuery(query, args)
        if (cursor.moveToFirst()) {
            do {
                list.add(ChatMessage(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_MSG_ID)),
                    senderId = cursor.getLong(cursor.getColumnIndexOrThrow(COL_MSG_SENDER)),
                    recipientId = cursor.getLong(cursor.getColumnIndexOrThrow(COL_MSG_RECIPIENT)),
                    content = cursor.getString(cursor.getColumnIndexOrThrow(COL_MSG_CONTENT)),
                    timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COL_MSG_TIMESTAMP)),
                    channel = cursor.getString(cursor.getColumnIndexOrThrow(COL_MSG_CHANNEL)),
                    packetId = cursor.getInt(cursor.getColumnIndexOrThrow(COL_MSG_PACKET_ID)),
                    status = cursor.getString(cursor.getColumnIndexOrThrow(COL_MSG_STATUS)) ?: "SENT",
                    isEncrypted = cursor.getInt(cursor.getColumnIndexOrThrow(COL_MSG_IS_ENCRYPTED)) != 0
                ))
            } while (cursor.moveToNext())
        }
        cursor.close()
        return list
    }

    fun getRetryableDirectMessages(recipientId: Long, limit: Int = 5): List<ChatMessage> {
        val db = this.readableDatabase
        val canonicalRecipient = resolveCanonicalNodeId(db, recipientId)
        val list = mutableListOf<ChatMessage>()
        val cursor = db.rawQuery(
            """
                SELECT * FROM $TABLE_MESSAGES
                WHERE $COL_MSG_RECIPIENT = ?
                  AND $COL_MSG_CHANNEL = ''
                  AND $COL_MSG_STATUS IN ('FAILED', 'QUEUED')
                ORDER BY $COL_MSG_TIMESTAMP ASC
                LIMIT ?
            """.trimIndent(),
            arrayOf(canonicalRecipient.toString(), limit.toString())
        )
        if (cursor.moveToFirst()) {
            do {
                list.add(ChatMessage(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_MSG_ID)),
                    senderId = cursor.getLong(cursor.getColumnIndexOrThrow(COL_MSG_SENDER)),
                    recipientId = cursor.getLong(cursor.getColumnIndexOrThrow(COL_MSG_RECIPIENT)),
                    content = cursor.getString(cursor.getColumnIndexOrThrow(COL_MSG_CONTENT)),
                    timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COL_MSG_TIMESTAMP)),
                    channel = cursor.getString(cursor.getColumnIndexOrThrow(COL_MSG_CHANNEL)),
                    packetId = cursor.getInt(cursor.getColumnIndexOrThrow(COL_MSG_PACKET_ID)),
                    status = cursor.getString(cursor.getColumnIndexOrThrow(COL_MSG_STATUS)) ?: "SENT",
                    isEncrypted = cursor.getInt(cursor.getColumnIndexOrThrow(COL_MSG_IS_ENCRYPTED)) != 0
                ))
            } while (cursor.moveToNext())
        }
        cursor.close()
        return list
    }

    // Save or update chat encryption passcode
    fun saveChatKey(chatIdentifier: String, key: String) {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COL_KEY_CHAT_ID, chatIdentifier)
            put(COL_KEY_VAL, key)
        }
        val rows = db.update(TABLE_KEYS, values, "$COL_KEY_CHAT_ID = ?", arrayOf(chatIdentifier))
        if (rows == 0) {
            db.insert(TABLE_KEYS, null, values)
        }
    }

    // Retrieve chat encryption passcode
    fun getChatKey(chatIdentifier: String): String? {
        val db = this.readableDatabase
        var key: String? = null
        val cursor = db.rawQuery("SELECT $COL_KEY_VAL FROM $TABLE_KEYS WHERE $COL_KEY_CHAT_ID = ?", arrayOf(chatIdentifier))
        if (cursor.moveToFirst()) {
            key = cursor.getString(0)
        }
        cursor.close()
        return key
    }

    // Insert Range Test diagnostics log
    fun insertRangeTestLog(
        targetId: Long,
        latitude: Double,
        longitude: Double,
        rssi: Float,
        snr: Float,
        success: Boolean,
        remoteRssi: Float? = null,
        remoteSnr: Float? = null,
        speedMps: Float? = null,
        gpsAccuracyM: Float? = null
    ): Long {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COL_LOG_TIMESTAMP, System.currentTimeMillis())
            put(COL_LOG_TARGET_ID, targetId)
            put(COL_LOG_LATITUDE, latitude)
            put(COL_LOG_LONGITUDE, longitude)
            put(COL_LOG_RSSI, rssi.toDouble())
            put(COL_LOG_SNR, snr.toDouble())
            put(COL_LOG_SUCCESS, if (success) 1 else 0)
            if (remoteRssi != null) put(COL_LOG_REMOTE_RSSI, remoteRssi.toDouble()) else putNull(COL_LOG_REMOTE_RSSI)
            if (remoteSnr != null) put(COL_LOG_REMOTE_SNR, remoteSnr.toDouble()) else putNull(COL_LOG_REMOTE_SNR)
            if (speedMps != null) put(COL_LOG_SPEED_MPS, speedMps.toDouble()) else putNull(COL_LOG_SPEED_MPS)
            if (gpsAccuracyM != null) put(COL_LOG_GPS_ACCURACY_M, gpsAccuracyM.toDouble()) else putNull(COL_LOG_GPS_ACCURACY_M)
        }
        return db.insert(TABLE_RANGE_TEST_LOGS, null, values)
    }

    private fun rangeTestLogFromCursor(cursor: android.database.Cursor): RangeTestLog {
        val remoteRssiIdx = cursor.getColumnIndexOrThrow(COL_LOG_REMOTE_RSSI)
        val remoteSnrIdx = cursor.getColumnIndexOrThrow(COL_LOG_REMOTE_SNR)
        val speedIdx = cursor.getColumnIndexOrThrow(COL_LOG_SPEED_MPS)
        val accuracyIdx = cursor.getColumnIndexOrThrow(COL_LOG_GPS_ACCURACY_M)
        return RangeTestLog(
            id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_LOG_ID)),
            timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COL_LOG_TIMESTAMP)),
            targetId = cursor.getLong(cursor.getColumnIndexOrThrow(COL_LOG_TARGET_ID)),
            latitude = cursor.getDouble(cursor.getColumnIndexOrThrow(COL_LOG_LATITUDE)),
            longitude = cursor.getDouble(cursor.getColumnIndexOrThrow(COL_LOG_LONGITUDE)),
            rssi = cursor.getDouble(cursor.getColumnIndexOrThrow(COL_LOG_RSSI)).toFloat(),
            snr = cursor.getDouble(cursor.getColumnIndexOrThrow(COL_LOG_SNR)).toFloat(),
            success = cursor.getInt(cursor.getColumnIndexOrThrow(COL_LOG_SUCCESS)) != 0,
            remoteRssi = if (cursor.isNull(remoteRssiIdx)) null else cursor.getDouble(remoteRssiIdx).toFloat(),
            remoteSnr = if (cursor.isNull(remoteSnrIdx)) null else cursor.getDouble(remoteSnrIdx).toFloat(),
            speedMps = if (cursor.isNull(speedIdx)) null else cursor.getDouble(speedIdx).toFloat(),
            gpsAccuracyM = if (cursor.isNull(accuracyIdx)) null else cursor.getDouble(accuracyIdx).toFloat()
        )
    }

    // Retrieve Range Test diagnostic logs
    fun getRangeTestLogs(targetId: Long): List<RangeTestLog> {
        val db = this.readableDatabase
        val list = mutableListOf<RangeTestLog>()
        val cursor = db.rawQuery(
            "SELECT * FROM $TABLE_RANGE_TEST_LOGS WHERE $COL_LOG_TARGET_ID = ? ORDER BY $COL_LOG_TIMESTAMP ASC",
            arrayOf(targetId.toString())
        )
        if (cursor.moveToFirst()) {
            do {
                list.add(rangeTestLogFromCursor(cursor))
            } while (cursor.moveToNext())
        }
        cursor.close()
        return list
    }

    // Retrieve Range Test logs for every target (for CSV export/analysis)
    fun getAllRangeTestLogs(): List<RangeTestLog> {
        val db = this.readableDatabase
        val list = mutableListOf<RangeTestLog>()
        val cursor = db.rawQuery(
            "SELECT * FROM $TABLE_RANGE_TEST_LOGS ORDER BY $COL_LOG_TIMESTAMP ASC",
            null
        )
        if (cursor.moveToFirst()) {
            do {
                list.add(rangeTestLogFromCursor(cursor))
            } while (cursor.moveToNext())
        }
        cursor.close()
        return list
    }

    // Clear Range Test diagnostic logs for a node
    fun clearRangeTestLogs(targetId: Long) {
        val db = this.writableDatabase
        db.delete(TABLE_RANGE_TEST_LOGS, "$COL_LOG_TARGET_ID = ?", arrayOf(targetId.toString()))
    }

    // Remove the most recent timeout/failure row for a target (used when a late ACK arrives).
    fun deleteLastRangeTestFailure(targetId: Long): Boolean {
        val db = this.writableDatabase
        val cursor = db.rawQuery(
            "SELECT $COL_LOG_ID FROM $TABLE_RANGE_TEST_LOGS WHERE $COL_LOG_TARGET_ID = ? AND $COL_LOG_SUCCESS = 0 ORDER BY $COL_LOG_TIMESTAMP DESC LIMIT 1",
            arrayOf(targetId.toString())
        )
        return try {
            if (!cursor.moveToFirst()) {
                false
            } else {
                val rowId = cursor.getLong(0)
                db.delete(TABLE_RANGE_TEST_LOGS, "$COL_LOG_ID = ?", arrayOf(rowId.toString())) > 0
            }
        } finally {
            cursor.close()
        }
    }

    // Distinct named group channels that have at least one message (excludes the "" DM channel).
    fun getChannels(): List<String> {
        val db = this.readableDatabase
        val list = mutableListOf<String>()
        val cursor = db.rawQuery(
            "SELECT DISTINCT $COL_MSG_CHANNEL FROM $TABLE_MESSAGES WHERE $COL_MSG_CHANNEL != '' ORDER BY $COL_MSG_CHANNEL ASC",
            null
        )
        if (cursor.moveToFirst()) {
            do {
                list.add(cursor.getString(0))
            } while (cursor.moveToNext())
        }
        cursor.close()
        return list
    }

    // Resolve canonical node ID (handles merging 16-bit and 32-bit representations of the same physical node)
    fun resolveCanonicalNodeId(db: android.database.sqlite.SQLiteDatabase, nodeId: Long): Long {
        if (nodeId == 0L) return 0L
        val incomingLower16 = nodeId and 0xFFFFL
        
        val cursor = db.rawQuery(
            "SELECT $COL_NODE_ID FROM $TABLE_NODES WHERE ($COL_NODE_ID & 65535) = CAST(? AS INTEGER)",
            arrayOf(incomingLower16.toString())
        )
        var canonicalId = nodeId
        if (cursor.moveToFirst()) {
            val existingId = cursor.getLong(0)
            if (existingId != nodeId) {
                if (existingId > 0xFFFF && nodeId <= 0xFFFF) {
                    canonicalId = existingId
                } else if (nodeId > 0xFFFF && existingId <= 0xFFFF) {
                    db.delete(TABLE_NODES, "$COL_NODE_ID = ?", arrayOf(existingId.toString()))
                    
                    val msgValues = ContentValues().apply { put(COL_MSG_SENDER, nodeId) }
                    db.update(TABLE_MESSAGES, msgValues, "$COL_MSG_SENDER = ?", arrayOf(existingId.toString()))
                    
                    val rxValues = ContentValues().apply { put(COL_MSG_RECIPIENT, nodeId) }
                    db.update(TABLE_MESSAGES, rxValues, "$COL_MSG_RECIPIENT = ?", arrayOf(existingId.toString()))
                    
                    canonicalId = nodeId
                }
            }
        }
        cursor.close()
        return canonicalId
    }

    // Retrieve single node name helper
    fun getNodeName(nodeId: Long): String? {
        val db = this.readableDatabase
        var name: String? = null
        val cursor = db.rawQuery(
            "SELECT $COL_NODE_NAME FROM $TABLE_NODES WHERE $COL_NODE_ID = ? OR ($COL_NODE_ID & 65535) = CAST(? AS INTEGER)",
            arrayOf(nodeId.toString(), (nodeId and 0xFFFFL).toString())
        )
        if (cursor.moveToFirst()) {
            name = cursor.getString(0)
        }
        cursor.close()
        return name
    }

    // Node name update helper
    fun updateNodeName(nodeId: Long, name: String) {
        if (nodeId == 0L) return
        val db = this.writableDatabase
        val canonicalId = resolveCanonicalNodeId(db, nodeId)
        val values = ContentValues().apply {
            put(COL_NODE_NAME, name)
        }
        val rows = db.update(TABLE_NODES, values, "$COL_NODE_ID = ?", arrayOf(canonicalId.toString()))
        if (rows == 0) {
            values.put(COL_NODE_ID, canonicalId)
            values.put(COL_NODE_BATTERY, 0)
            values.put(COL_NODE_LATITUDE, 0f)
            values.put(COL_NODE_LONGITUDE, 0f)
            values.put(COL_NODE_LAST_ACTIVE, System.currentTimeMillis())
            values.put(COL_NODE_MODEL, "Unknown")
            db.insert(TABLE_NODES, null, values)
        }
    }

    // Node updates
    fun updateNode(
        nodeId: Long,
        battery: Int,
        lat: Float,
        lon: Float,
        model: String,
        uptimeSeconds: Long = 0,
        firmwareVersion: String = "",
        isCharging: Boolean = false,
        rssi: Float = 0f,
        snr: Float = 0f,
        voltage: Float = 0f,
        positionPrecision: Int = 0
    ) {
        if (nodeId == 0L) return
        val db = this.writableDatabase
        val canonicalId = resolveCanonicalNodeId(db, nodeId)

        var existingName = ""
        var existingShortName = ""
        val cursorName = db.rawQuery(
            "SELECT $COL_NODE_NAME, $COL_NODE_SHORT_NAME FROM $TABLE_NODES WHERE $COL_NODE_ID = ?",
            arrayOf(canonicalId.toString())
        )
        if (cursorName.moveToFirst()) {
            existingName = cursorName.getString(0) ?: ""
            existingShortName = cursorName.getString(1) ?: ""
        }
        cursorName.close()

        val nameToUse = if (existingName.isNotEmpty()) existingName else "Node ${String.format("%04X", (canonicalId and 0xFFFF).toInt())}"
        val shortNameToUse = if (existingShortName.isNotEmpty()) existingShortName else {
            val clean = nameToUse.replace("AetherMesh-", "").replace("Node ", "").replace(Regex("[^a-zA-Z0-9]"), "")
            clean.take(4).uppercase().ifEmpty { String.format("%04X", (canonicalId and 0xFFFF).toInt()) }
        }

        val values = ContentValues().apply {
            put(COL_NODE_BATTERY, battery)
            put(COL_NODE_LATITUDE, lat)
            put(COL_NODE_LONGITUDE, lon)
            put(COL_NODE_LAST_ACTIVE, System.currentTimeMillis())
            put(COL_NODE_MODEL, model)
            put(COL_NODE_NAME, nameToUse)
            put(COL_NODE_SHORT_NAME, shortNameToUse)
            put(COL_NODE_UPTIME, uptimeSeconds)
            put(COL_NODE_FW_VERSION, firmwareVersion)
            put(COL_NODE_IS_CHARGING, if (isCharging) 1 else 0)
            // Only store signal from real over-the-air reception. rssi == 0 means
            // the local/connected node's own loopback, which carries no rx signal;
            // leave the previous value untouched.
            if (rssi != 0f) {
                put(COL_NODE_RSSI, rssi.toDouble())
                put(COL_NODE_SNR, snr.toDouble())
            }
            // 0 = unmeasured (old firmware) - keep the previous value
            if (voltage != 0f) {
                put(COL_NODE_VOLTAGE, voltage.toDouble())
            }
            // Precision is authoritative per telemetry packet: 0 legitimately
            // means "precise" (or old firmware), so always store it.
            put(COL_NODE_POS_PRECISION, positionPrecision)
        }
        
        val rows = db.update(TABLE_NODES, values, "$COL_NODE_ID = ?", arrayOf(canonicalId.toString()))
        if (rows == 0) {
            values.put(COL_NODE_ID, canonicalId)
            db.insert(TABLE_NODES, null, values)
        }
    }

    // Retrieve all known nodes
    fun getNodes(): List<MeshNode> {
        val db = this.readableDatabase
        val list = mutableListOf<MeshNode>()
        val cursor = db.rawQuery("SELECT * FROM $TABLE_NODES WHERE $COL_NODE_ID != 0 ORDER BY $COL_NODE_LAST_ACTIVE DESC", null)
        if (cursor.moveToFirst()) {
            do {
                list.add(MeshNode(
                    nodeId = cursor.getLong(cursor.getColumnIndexOrThrow(COL_NODE_ID)),
                    name = cursor.getString(cursor.getColumnIndexOrThrow(COL_NODE_NAME)),
                    shortName = cursor.getString(cursor.getColumnIndexOrThrow(COL_NODE_SHORT_NAME)) ?: "",
                    battery = cursor.getInt(cursor.getColumnIndexOrThrow(COL_NODE_BATTERY)),
                    latitude = cursor.getFloat(cursor.getColumnIndexOrThrow(COL_NODE_LATITUDE)),
                    longitude = cursor.getFloat(cursor.getColumnIndexOrThrow(COL_NODE_LONGITUDE)),
                    lastActive = cursor.getLong(cursor.getColumnIndexOrThrow(COL_NODE_LAST_ACTIVE)),
                    model = cursor.getString(cursor.getColumnIndexOrThrow(COL_NODE_MODEL)),
                    uptimeSeconds = cursor.getLong(cursor.getColumnIndexOrThrow(COL_NODE_UPTIME)),
                    firmwareVersion = cursor.getString(cursor.getColumnIndexOrThrow(COL_NODE_FW_VERSION)) ?: "",
                    isCharging = cursor.getInt(cursor.getColumnIndexOrThrow(COL_NODE_IS_CHARGING)) != 0,
                    rssi = cursor.getFloat(cursor.getColumnIndexOrThrow(COL_NODE_RSSI)),
                    snr = cursor.getFloat(cursor.getColumnIndexOrThrow(COL_NODE_SNR)),
                    voltage = cursor.getFloat(cursor.getColumnIndexOrThrow(COL_NODE_VOLTAGE)),
                    positionPrecision = cursor.getInt(cursor.getColumnIndexOrThrow(COL_NODE_POS_PRECISION))
                ))
            } while (cursor.moveToNext())
        }
        cursor.close()
        return list
    }

    fun updateNodeNameAndShortName(nodeId: Long, name: String, shortName: String) {
        if (nodeId == 0L) return
        val db = this.writableDatabase
        val canonicalId = resolveCanonicalNodeId(db, nodeId)
        val values = ContentValues().apply {
            put(COL_NODE_NAME, name)
            put(COL_NODE_SHORT_NAME, shortName)
        }
        val rows = db.update(TABLE_NODES, values, "$COL_NODE_ID = ?", arrayOf(canonicalId.toString()))
        if (rows == 0) {
            values.put(COL_NODE_ID, canonicalId)
            values.put(COL_NODE_BATTERY, 0)
            values.put(COL_NODE_LATITUDE, 0f)
            values.put(COL_NODE_LONGITUDE, 0f)
            values.put(COL_NODE_LAST_ACTIVE, System.currentTimeMillis())
            values.put(COL_NODE_MODEL, "Unknown")
            db.insert(TABLE_NODES, null, values)
        }
    }

    fun deleteNode(nodeId: Long) {
        if (nodeId == 0L) return
        val db = this.writableDatabase
        db.delete(TABLE_NODES, "$COL_NODE_ID = ?", arrayOf(nodeId.toString()))
    }

    fun clearAllMessages() {
        val db = this.writableDatabase
        db.execSQL("DELETE FROM $TABLE_MESSAGES")
        db.execSQL("DELETE FROM $TABLE_KEYS") // Delete keys too on full clear
    }

    fun clearAllNodes() {
        val db = this.writableDatabase
        db.execSQL("DELETE FROM $TABLE_NODES")
        db.execSQL("DELETE FROM $TABLE_RANGE_TEST_LOGS") // Delete range test logs on directory reset
    }

    fun getChannelsList(): List<ChannelConfig> {
        val list = mutableListOf<ChannelConfig>()
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_CHANNELS ORDER BY $COL_CHAN_PRIMARY DESC, $COL_CHAN_ID ASC", null)
        if (cursor.moveToFirst()) {
            do {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_CHAN_ID))
                val name = cursor.getString(cursor.getColumnIndexOrThrow(COL_CHAN_NAME))
                val psk = cursor.getString(cursor.getColumnIndexOrThrow(COL_CHAN_PSK))
                val uplink = cursor.getInt(cursor.getColumnIndexOrThrow(COL_CHAN_UPLINK)) == 1
                val downlink = cursor.getInt(cursor.getColumnIndexOrThrow(COL_CHAN_DOWNLINK)) == 1
                val position = cursor.getInt(cursor.getColumnIndexOrThrow(COL_CHAN_POSITION)) == 1
                val precise = cursor.getInt(cursor.getColumnIndexOrThrow(COL_CHAN_PRECISE)) == 1
                val precision = cursor.getFloat(cursor.getColumnIndexOrThrow(COL_CHAN_PRECISION_MILES))
                val primary = cursor.getInt(cursor.getColumnIndexOrThrow(COL_CHAN_PRIMARY)) == 1
                list.add(ChannelConfig(id, name, psk, uplink, downlink, position, precise, precision, primary))
            } while (cursor.moveToNext())
        }
        cursor.close()
        return list
    }

    fun insertChannel(channel: ChannelConfig): Long {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COL_CHAN_NAME, channel.name)
            put(COL_CHAN_PSK, channel.psk)
            put(COL_CHAN_UPLINK, if (channel.uplinkEnabled) 1 else 0)
            put(COL_CHAN_DOWNLINK, if (channel.downlinkEnabled) 1 else 0)
            put(COL_CHAN_POSITION, if (channel.positionEnabled) 1 else 0)
            put(COL_CHAN_PRECISE, if (channel.preciseLocation) 1 else 0)
            put(COL_CHAN_PRECISION_MILES, channel.precisionMiles)
            put(COL_CHAN_PRIMARY, if (channel.isPrimary) 1 else 0)
        }
        val id = db.insertWithOnConflict(TABLE_CHANNELS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        saveChatKey("CHANNEL_${channel.name}", channel.psk)
        return id
    }

    fun updateChannel(channel: ChannelConfig) {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COL_CHAN_NAME, channel.name)
            put(COL_CHAN_PSK, channel.psk)
            put(COL_CHAN_UPLINK, if (channel.uplinkEnabled) 1 else 0)
            put(COL_CHAN_DOWNLINK, if (channel.downlinkEnabled) 1 else 0)
            put(COL_CHAN_POSITION, if (channel.positionEnabled) 1 else 0)
            put(COL_CHAN_PRECISE, if (channel.preciseLocation) 1 else 0)
            put(COL_CHAN_PRECISION_MILES, channel.precisionMiles)
            put(COL_CHAN_PRIMARY, if (channel.isPrimary) 1 else 0)
        }
        db.update(TABLE_CHANNELS, values, "$COL_CHAN_ID = ?", arrayOf(channel.id.toString()))
        saveChatKey("CHANNEL_${channel.name}", channel.psk)
    }

    fun deleteChannel(id: Long) {
        val db = this.writableDatabase
        // Retrieve name first to clean up encryption keys table
        var name: String? = null
        val cursor = db.rawQuery("SELECT $COL_CHAN_NAME FROM $TABLE_CHANNELS WHERE $COL_CHAN_ID = ?", arrayOf(id.toString()))
        if (cursor.moveToFirst()) {
            name = cursor.getString(0)
        }
        cursor.close()
        
        db.delete(TABLE_CHANNELS, "$COL_CHAN_ID = ?", arrayOf(id.toString()))
        if (name != null) {
            db.delete(TABLE_KEYS, "$COL_KEY_CHAT_ID = ?", arrayOf("CHANNEL_$name"))
        }
    }
}

data class ChannelConfig(
    val id: Long = 0,
    val name: String,
    val psk: String,
    val uplinkEnabled: Boolean = true,
    val downlinkEnabled: Boolean = true,
    val positionEnabled: Boolean = true,
    val preciseLocation: Boolean = true,
    val precisionMiles: Float = 0.0f,
    val isPrimary: Boolean = false
)

data class ChatMessage(
    val id: Long,
    val senderId: Long,
    val recipientId: Long,
    val content: String,
    val timestamp: Long,
    val channel: String,
    val packetId: Int = 0,
    val status: String = "SENT",
    val isEncrypted: Boolean = false
)

data class MeshNode(
    val nodeId: Long,
    val name: String,
    val shortName: String = "",
    val battery: Int,
    val latitude: Float,
    val longitude: Float,
    val lastActive: Long,
    val model: String,
    val uptimeSeconds: Long = 0,
    val firmwareVersion: String = "",
    val isCharging: Boolean = false,
    val rssi: Float = 0f,
    val snr: Float = 0f,
    val voltage: Float = 0f,
    // Privacy blur radius (m) the node applies to its broadcast position; 0 = precise
    val positionPrecision: Int = 0
)

data class TelemetrySample(
    val timestamp: Long,
    val battery: Int,
    val voltage: Float,
    val isCharging: Boolean
)

data class RangeTestLog(
    val id: Long,
    val timestamp: Long,
    val targetId: Long,
    val latitude: Double,
    val longitude: Double,
    val rssi: Float,
    val snr: Float,
    val success: Boolean,
    // Ping signal quality as measured AT THE TARGET, reported back in the ACK.
    // Null on timeouts or when the target runs firmware that doesn't report it.
    val remoteRssi: Float? = null,
    val remoteSnr: Float? = null,
    // Phone GPS metadata at ping time; null when no fresh fix was available
    val speedMps: Float? = null,
    val gpsAccuracyM: Float? = null
)
