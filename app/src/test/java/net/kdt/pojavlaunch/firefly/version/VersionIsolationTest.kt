package net.kdt.pojavlaunch.firefly.version

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionIsolationTest {
    private val gameHome = File("game-home")

    @Test
    fun usesVersionDirectoryWhenIsolationIsEnabled() {
        assertEquals(
            File(gameHome, "versions/1.21.1 Fabric"),
            VersionIsolation.defaultGameDirectory(gameHome, "1.21.1 Fabric", true)
        )
    }

    @Test
    fun usesGlobalDirectoryWhenIsolationIsDisabledOrVersionIsInvalid() {
        assertEquals(gameHome, VersionIsolation.defaultGameDirectory(gameHome, "1.21.1", false))
        assertEquals(gameHome, VersionIsolation.defaultGameDirectory(gameHome, "../outside", true))
        assertEquals(gameHome, VersionIsolation.defaultGameDirectory(gameHome, null, true))
    }

    @Test
    fun isolationOverridesCustomDirectoryUntilItIsDisabled() {
        val customDirectory = File("custom-instance")

        assertEquals(
            File(gameHome, "versions/1.21.1 Fabric"),
            VersionIsolation.resolveGameDirectory(gameHome, customDirectory, "1.21.1 Fabric", true)
        )
        assertEquals(
            customDirectory,
            VersionIsolation.resolveGameDirectory(gameHome, customDirectory, "1.21.1 Fabric", false)
        )
        assertEquals(
            gameHome,
            VersionIsolation.resolveGameDirectory(gameHome, null, "1.21.1 Fabric", false)
        )
    }

    @Test
    fun displaysTheRelativeIsolationDirectory() {
        assertEquals(
            ".minecraft/versions/1.21.1 Fabric",
            VersionIsolation.displayRelativeGameDirectory(File(".minecraft"), "1.21.1 Fabric")
        )
        assertEquals(
            ".minecraft",
            VersionIsolation.displayRelativeGameDirectory(File(".minecraft"), null)
        )
    }

    @Test
    fun acceptsOnlySingleDirectoryNames() {
        assertTrue(VersionIsolation.isDirectoryName("1.21.1 Fabric"))
        assertFalse(VersionIsolation.isDirectoryName(""))
        assertFalse(VersionIsolation.isDirectoryName("."))
        assertFalse(VersionIsolation.isDirectoryName(".."))
        assertFalse(VersionIsolation.isDirectoryName("a/b"))
        assertFalse(VersionIsolation.isDirectoryName("a\\b"))
    }
}
