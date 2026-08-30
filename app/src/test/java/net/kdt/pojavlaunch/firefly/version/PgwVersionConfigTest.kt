package net.kdt.pojavlaunch.firefly.version

import com.google.gson.Gson
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PgwVersionConfigTest {
    private val gameHome = File("game-home")
    private val versionHome = File(gameHome, "versions/Fabric instance")
    private val customHome = File("custom-home")

    @Test
    fun followsGlobalIsolationWhenRequested() {
        assertEquals(
            versionHome,
            VersionIsolationPolicy.resolve(
                gameHome, versionHome, customHome, VersionIsolationMode.FOLLOW_GLOBAL, true
            )
        )
        assertEquals(
            customHome,
            VersionIsolationPolicy.resolve(
                gameHome, versionHome, customHome, VersionIsolationMode.FOLLOW_GLOBAL, false
            )
        )
    }

    @Test
    fun explicitIsolationAlwaysWinsOverGlobalSetting() {
        assertEquals(
            versionHome,
            VersionIsolationPolicy.resolve(
                gameHome, versionHome, customHome, VersionIsolationMode.ENABLE, false
            )
        )
        assertEquals(
            customHome,
            VersionIsolationPolicy.resolve(
                gameHome, versionHome, customHome, VersionIsolationMode.DISABLE, true
            )
        )
        assertEquals(
            gameHome,
            VersionIsolationPolicy.resolve(
                gameHome, versionHome, null, VersionIsolationMode.DISABLE, true
            )
        )
    }

    @Test
    fun serializesInstanceSettingsWithoutLegacyProfileFields() {
        val config = PgwVersionConfig(
            pinned = true,
            summary = "Fabric instance",
            isolation = VersionIsolationMode.ENABLE,
            runtimeName = "Internal-21",
            graphicsApi = "vulkan",
            customGameDir = "ignored-when-isolated"
        )

        val json = Gson().toJson(config)
        val restored = Gson().fromJson(json, PgwVersionConfig::class.java)

        assertTrue(restored.pinned)
        assertEquals("Fabric instance", restored.summary)
        assertEquals(VersionIsolationMode.ENABLE, restored.isolation)
        assertEquals("Internal-21", restored.runtimeName)
        assertEquals("vulkan", restored.graphicsApi)
        assertFalse(json.contains("lastVersionId"))
        assertFalse(json.contains("launcher_profiles"))
    }
}
