package com.dailybeat.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppStorageTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `output file stays inside the selected directory`() {
        val directory = temporaryFolder.newFolder("exports")

        val output = AppStorage.outputFileIn(directory, "dailybeat-export.zip")

        assertEquals(directory.canonicalFile, output.parentFile)
    }

    @Test
    fun `output file rejects traversal and nested paths`() {
        val directory = temporaryFolder.newFolder("exports")

        listOf("../secret", "nested/secret", "nested\\secret", ".", "..").forEach { name ->
            assertThrows(IllegalArgumentException::class.java) {
                AppStorage.outputFileIn(directory, name)
            }
        }
    }

    @Test
    fun `sensitive cleanup removes a temporary payload`() {
        val file = temporaryFolder.newFile("diary.tmp").apply { writeText("private diary") }

        AppStorage.clearSensitiveFile(file)

        assertTrue(!file.exists() || file.length() == 0L)
        assertFalse(file.exists() && file.readText().contains("private diary"))
    }
}
