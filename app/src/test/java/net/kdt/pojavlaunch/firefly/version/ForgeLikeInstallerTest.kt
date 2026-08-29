package net.kdt.pojavlaunch.firefly.version

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ForgeLikeInstallerTest {
    @Test
    fun usesArtifactPathWhenInstallerLibraryNameIsNull() {
        val library = JsonParser.parseString(
            """{"name":null,"downloads":{"artifact":{"path":"net/minecraftforge/forge/1.20.1-47.4.23/forge-1.20.1-47.4.23.jar"}}}"""
        ).asJsonObject

        assertEquals(
            "net/minecraftforge/forge/1.20.1-47.4.23/forge-1.20.1-47.4.23.jar",
            ForgeLikeInstaller.embeddedLibraryPath(library)
        )
    }

    @Test
    fun ignoresInstallerLibraryWithoutPathOrCoordinates() {
        val library = JsonParser.parseString("""{"name":null}""").asJsonObject

        assertNull(ForgeLikeInstaller.embeddedLibraryPath(library))
    }
}
