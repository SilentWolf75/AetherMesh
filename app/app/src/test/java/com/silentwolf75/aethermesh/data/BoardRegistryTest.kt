package com.silentwolf75.aethermesh.data

import com.silentwolf75.aethermesh.ble.OtaPayloadPolicy
import org.junit.Assert.*
import org.junit.Test

class BoardRegistryTest {
    @Test fun everyBoardRecognizesItsReleaseEnvironment() {
        for (board in BoardRegistry.boards) {
            assertEquals(board.id, BoardRegistry.forFile("aethermesh-" + board.env + "-abcdef0.zip")?.id)
            assertEquals(board.env, FirmwareCatalog.envTagForBoard(board.id))
        }
    }
    @Test fun nordicModelsSelectDfuAndRejectEspImages() {
        for (model in listOf("T-Echo", "T1000-E", "RAK4631", "RAK3401 1W", "RAK19026")) {
            assertTrue(model, OtaPayloadPolicy.isRakTarget(model, "AetherMesh-1234"))
            assertFalse(model, OtaPayloadPolicy.isEspTarget(model))
            val board = FirmwareCatalog.boardIdForModel(model)
            assertNotNull(board)
            val zip = ByteArray(256).apply { this[0] = 80; this[1] = 75 }
            assertNull(OtaPayloadPolicy.validate(zip, "firmware.zip", true, board))
            assertNotNull(OtaPayloadPolicy.validate(ByteArray(2048), "firmware.bin", true, board))
        }
    }
    @Test fun descriptiveHeltecV3ModelKeepsItsBoardIdentity() {
        assertEquals("heltec-v3", FirmwareCatalog.boardIdForModel("Heltec WiFi LoRa 32 V3"))
    }
    @Test fun knownModelTakesPriorityOverUserChosenDeviceName() {
        assertFalse(OtaPayloadPolicy.isRakTarget("Heltec V4", "rak-test"))
        assertTrue(OtaPayloadPolicy.isEspTarget("Heltec V4", "rak-test"))
    }
    @Test fun explicitCatalogBoardCannotBeOverriddenByFilename() {
        val artifact = FirmwareCatalog.Artifact("Wrong", "aethermesh-t-echo.zip", 100, "", "ota", "seeed-t1000-e")
        assertFalse(FirmwareCatalog.matchesBoard(artifact, "lilygo-t-echo"))
    }
}
