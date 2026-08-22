package com.silentwolf75.aethermesh.data

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.readText

/** Ensures Android backup excludes match [SecurePrefsNames] (Keystore-bound prefs). */
class BackupRulesTest {
    private val xmlDir: Path = Paths.get(System.getProperty("user.dir"), "src/main/res/xml")

    @Test
    fun backupRulesExcludeSecurePrefsFiles() {
        listOf("backup_rules.xml", "data_extraction_rules.xml").forEach { name ->
            val text = xmlDir.resolve(name).readText()
            SecurePrefsNames.backupExcludePaths.forEach { path ->
                assertTrue(
                    "Missing sharedpref exclude for $path in $name",
                    text.contains("""path="$path"""")
                )
            }
        }
    }

    @Test
    fun backupRuleFilesExist() {
        listOf("backup_rules.xml", "data_extraction_rules.xml").forEach { name ->
            assertTrue("Missing $name", Files.exists(xmlDir.resolve(name)))
        }
    }
}
