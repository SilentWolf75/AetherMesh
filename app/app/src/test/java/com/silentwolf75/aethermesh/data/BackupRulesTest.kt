package com.silentwolf75.aethermesh.data

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Paths
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

class BackupRulesTest {
    @Test fun everyBackupTransportExcludesRealSecretFilenamesAndChatDatabase() {
        val root = Paths.get(System.getProperty("user.dir"), "src/main/res/xml")
        // Actual Android on-disk filenames, independent of production constants.
        val required = setOf(
            "sharedpref:aethermesh_secure_prefs.xml",
            "sharedpref:aethermesh_secure_prefs_fallback.xml",
            "sharedpref:aethermesh_prefs.xml",
            "database:aethermesh.db"
        )
        mapOf("backup_rules.xml" to listOf("full-backup-content"),
            "data_extraction_rules.xml" to listOf("cloud-backup", "device-transfer")
        ).forEach { (file, transports) ->
            val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(root.resolve(file).toFile())
            transports.forEach { transport ->
                val node = document.getElementsByTagName(transport).item(0) as Element
                val excludes = node.getElementsByTagName("exclude")
                val actual = (0 until excludes.length).map {
                    val element = excludes.item(it) as Element
                    element.getAttribute("domain") + ":" + element.getAttribute("path")
                }.toSet()
                assertTrue("$file/$transport missing exclusions: " + (required - actual), actual.containsAll(required))
            }
        }
    }
}
