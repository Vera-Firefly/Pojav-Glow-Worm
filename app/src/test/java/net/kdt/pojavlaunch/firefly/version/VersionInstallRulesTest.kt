package net.kdt.pojavlaunch.firefly.version

import net.kdt.pojavlaunch.firefly.version.model.GameManifest
import net.kdt.pojavlaunch.firefly.profiles.ProfileIconIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionInstallRulesTest {

    @Test
    fun usesPackageEndpointForLegacyAssetIndexes() {
        val manifest = GameManifest().apply { assets = "legacy" }

        assertEquals(
            "https://launchermeta.mojang.com/v1/packages/770572e819335b6c0a053f8378ad88eda189fc14/legacy.json",
            manifest.assetIndex.url
        )
    }

    @Test
    fun parsesForgeInstallerVersionsFromBmcl() {
        val versions = LoaderCatalog.parseForgeBmclVersions(
            "1.20.1",
            """[
                {"version":"47.4.23","files":[{"category":"installer","format":"jar"}]},
                {"version":"47.4.22","branch":"1.20.1","files":[{"category":"installer","format":"jar"}]},
                {"version":"47.4.21","files":[{"category":"mdk","format":"zip"}]}
            ]"""
        )

        assertEquals(2, versions.size)
        assertEquals("47.4.23", versions.first().loaderVersion)
        assertEquals(
            "https://maven.minecraftforge.net/net/minecraftforge/forge/1.20.1-47.4.23/forge-1.20.1-47.4.23-installer.jar",
            versions.first().downloadUrl
        )
        assertEquals("47.4.22-1.20.1", versions[1].loaderVersion)
    }

    @Test
    fun generatesNamesForVanillaAndLoaders() {
        val fabric = LoaderVersion(LoaderKind.FABRIC, "1.21.1", "0.16.10")
        val forge = LoaderVersion(LoaderKind.FORGE, "1.21.1", "52.0.0")
        val optiFine = LoaderVersion(
            LoaderKind.OPTIFINE,
            "1.21.1",
            "HD_U_J3",
            forgeCompatibility = "52.0.0"
        )

        assertEquals("1.21.1", VersionInstallRules.generatedName("1.21.1", AddonSelection()))
        assertEquals(
            "1.21.1 Fabric 0.16.10",
            VersionInstallRules.generatedName("1.21.1", AddonSelection(fabric = fabric))
        )
        assertEquals(
            "1.21.1 Forge 52.0.0-OptiFine HD_U_J3",
            VersionInstallRules.generatedName("1.21.1", AddonSelection(forge = forge, optiFine = optiFine))
        )
    }

    @Test
    fun selectsTheDefaultProfileIconFromInstalledAddons() {
        val forge = LoaderVersion(LoaderKind.FORGE, "1.21.1", "52.0.0")
        val neoForge = LoaderVersion(LoaderKind.NEOFORGE, "1.21.1", "21.1.0")
        val fabric = LoaderVersion(LoaderKind.FABRIC, "1.21.1", "0.16.10")
        val quilt = LoaderVersion(LoaderKind.QUILT, "1.21.1", "0.26.0")
        val optiFine = LoaderVersion(LoaderKind.OPTIFINE, "1.21.1", "HD_U_J3")

        assertEquals(ProfileIconIds.MINECRAFT, VersionInstallRules.defaultProfileIcon(AddonSelection()))
        assertEquals(ProfileIconIds.FORGE, VersionInstallRules.defaultProfileIcon(AddonSelection(forge = forge)))
        assertEquals(ProfileIconIds.NEOFORGE, VersionInstallRules.defaultProfileIcon(AddonSelection(neoForge = neoForge)))
        assertEquals(ProfileIconIds.FABRIC, VersionInstallRules.defaultProfileIcon(AddonSelection(fabric = fabric)))
        assertEquals(ProfileIconIds.QUILT, VersionInstallRules.defaultProfileIcon(AddonSelection(quilt = quilt)))
        assertEquals(ProfileIconIds.OPTIFINE, VersionInstallRules.defaultProfileIcon(AddonSelection(optiFine = optiFine)))
        assertEquals(
            ProfileIconIds.OPTIFINE,
            VersionInstallRules.defaultProfileIcon(AddonSelection(forge = forge, optiFine = optiFine))
        )
    }

    @Test
    fun checksForgeAndOptiFineCompatibility() {
        val optiFine = LoaderVersion(
            LoaderKind.OPTIFINE,
            "1.21.1",
            "HD_U_J3",
            forgeCompatibility = "52.0.0"
        )
        assertTrue(VersionInstallRules.isOptiFineCompatibleWithForge(
            optiFine,
            LoaderVersion(LoaderKind.FORGE, "1.21.1", "52.0.0")
        ))
        assertFalse(VersionInstallRules.isOptiFineCompatibleWithForge(
            optiFine,
            LoaderVersion(LoaderKind.FORGE, "1.21.1", "51.0.0")
        ))
    }

    @Test
    fun rejectsConflictingPrimaryLoadersAndInvalidNames() {
        val forge = LoaderVersion(LoaderKind.FORGE, "1.21.1", "52.0.0")
        val fabric = LoaderVersion(LoaderKind.FABRIC, "1.21.1", "0.16.10")
        assertFails {
            VersionInstallRules.validate(VersionInstallRequest(
                minecraftVersion = "1.21.1",
                targetVersionName = "conflict",
                addons = AddonSelection(forge = forge, fabric = fabric)
            ))
        }
        assertFails {
            VersionInstallRules.validate(VersionInstallRequest(
                minecraftVersion = "1.21.1",
                targetVersionName = "../invalid",
                addons = AddonSelection()
            ))
        }
    }

    private fun assertFails(block: () -> Unit) {
        try {
            block()
        } catch (_: IllegalArgumentException) {
            return
        }
        throw AssertionError("Expected IllegalArgumentException")
    }
}
