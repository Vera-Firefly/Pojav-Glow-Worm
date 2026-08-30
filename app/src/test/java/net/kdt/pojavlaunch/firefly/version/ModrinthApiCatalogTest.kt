package net.kdt.pojavlaunch.firefly.version

import org.junit.Assert.assertEquals
import org.junit.Test

class ModrinthApiCatalogTest {
    @Test
    fun ordersCompatibleReleasesByNewestPublicationFirst() {
        val versions = ModrinthApiCatalog.parseVersions(
            ModrinthApiCatalog.FABRIC_API_PROJECT,
            "1.21.1",
            releases(
                release("0.110.0", "2024-09-10T10:00:00Z", "fabric-api-110.jar"),
                release("0.112.0", "2024-10-01T10:00:00Z", "fabric-api-112.jar"),
                release("0.111.0", "2024-09-20T10:00:00Z", "fabric-api-111.jar")
            )
        )

        assertEquals(listOf("0.112.0", "0.111.0", "0.110.0"), versions.map { it.version })
    }

    @Test
    fun usesVersionThenFileNameForPublicationTimeTies() {
        val versions = ModrinthApiCatalog.parseVersions(
            ModrinthApiCatalog.FABRIC_API_PROJECT,
            "1.21.1",
            releases(
                release("0.9.0", "2024-10-01T10:00:00Z", "z.jar"),
                release("0.10.0", "2024-10-01T10:00:00Z", "b.jar"),
                release("0.10.0", "2024-10-01T10:00:00Z", "a.jar")
            )
        )

        assertEquals(listOf("0.10.0", "0.10.0", "0.9.0"), versions.map { it.version })
        assertEquals(listOf("a.jar", "b.jar", "z.jar"), versions.map { it.fileName })
    }

    @Test
    fun keepsInvalidDatesAfterValidReleasesAndSupportsQuiltedApi() {
        val versions = ModrinthApiCatalog.parseVersions(
            ModrinthApiCatalog.QUILTED_FABRIC_API_PROJECT,
            "1.21.1",
            releases(
                release("8.0.0", null, "qfapi-8.jar"),
                release("7.0.0", "not-a-date", "qfapi-7.jar"),
                release("6.0.0", "2024-09-01T10:00:00Z", "qfapi-6.jar"),
                release("ignored", "2024-12-01T10:00:00Z", "ignored.jar", minecraftVersion = "1.20.1")
            )
        )

        assertEquals(ModrinthApiCatalog.QUILTED_FABRIC_API_PROJECT, versions.first().projectId)
        assertEquals(listOf("6.0.0", "8.0.0", "7.0.0"), versions.map { it.version })
    }

    private fun releases(vararg releases: String): String = releases.joinToString(",", prefix = "[", postfix = "]")

    private fun release(
        version: String,
        publishedAt: String?,
        fileName: String,
        minecraftVersion: String = "1.21.1"
    ): String = buildString {
        append("{\"version_number\":\"").append(version).append("\",")
        if (publishedAt != null) append("\"date_published\":\"").append(publishedAt).append("\",")
        append("\"game_versions\":[\"").append(minecraftVersion).append("\"],")
        append("\"files\":[{\"primary\":true,\"filename\":\"").append(fileName)
        append("\",\"url\":\"https://example.invalid/").append(fileName)
        append("\",\"hashes\":{\"sha1\":\"abc\"},\"size\":1}]}")
    }
}
