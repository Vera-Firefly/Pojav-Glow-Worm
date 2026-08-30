package net.kdt.pojavlaunch.firefly.fragments

import net.kdt.pojavlaunch.firefly.version.LoaderKind
import net.kdt.pojavlaunch.firefly.version.LoaderVersion
import net.kdt.pojavlaunch.firefly.version.ModrinthApiVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VersionAddonViewModelTest {
    @Test
    fun clearingFabricAlsoClearsItsApi() {
        val model = VersionAddonViewModel()
        val fabric = LoaderVersion(LoaderKind.FABRIC, "1.21.1", "0.16.10")
        val api = apiVersion("fabric-api", "0.110.0", "fabric-api.jar")

        model.select(AddonCardType.FABRIC, AddonOption("0.16.10", "0.16.10", loader = fabric))
        model.select(AddonCardType.FABRIC_API, AddonOption("fabric-api.jar", "0.110.0", api = api))
        model.select(AddonCardType.FABRIC, null)

        assertNull(model.state.value.selection.fabric)
        assertNull(model.state.value.selection.fabricApi)
    }

    @Test
    fun clearingQuiltAlsoClearsItsApi() {
        val model = VersionAddonViewModel()
        val quilt = LoaderVersion(LoaderKind.QUILT, "1.21.1", "0.26.4")
        val api = apiVersion("quilted-fabric-api", "8.0.0", "qfapi.jar")

        model.select(AddonCardType.QUILT, AddonOption("0.26.4", "0.26.4", loader = quilt))
        model.select(AddonCardType.QUILTED_FABRIC_API, AddonOption("qfapi.jar", "8.0.0", api = api))
        model.select(AddonCardType.QUILT, null)

        assertNull(model.state.value.selection.quilt)
        assertNull(model.state.value.selection.quiltedFabricApi)
    }

    @Test
    fun forgeAndOptiFineRemainSelectedInEitherOrderAndClearIndependently() {
        val forge = LoaderVersion(LoaderKind.FORGE, "1.21.1", "52.0.0")
        val replacementForge = LoaderVersion(LoaderKind.FORGE, "1.21.1", "52.1.0")
        val optiFine = LoaderVersion(
            LoaderKind.OPTIFINE,
            "1.21.1",
            "HD_U_J3",
            forgeCompatibility = "51.0.0"
        )
        val replacementOptiFine = LoaderVersion(LoaderKind.OPTIFINE, "1.21.1", "HD_U_J4")
        val forgeFirst = VersionAddonViewModel()

        forgeFirst.select(AddonCardType.FORGE, AddonOption("52.0.0", "52.0.0", loader = forge))
        forgeFirst.select(AddonCardType.OPTIFINE, AddonOption("HD_U_J3", "HD_U_J3", loader = optiFine))
        assertEquals(forge, forgeFirst.state.value.selection.forge)
        assertEquals(optiFine, forgeFirst.state.value.selection.optiFine)

        forgeFirst.select(AddonCardType.FORGE, AddonOption("52.1.0", "52.1.0", loader = replacementForge))
        forgeFirst.select(AddonCardType.OPTIFINE, AddonOption("HD_U_J4", "HD_U_J4", loader = replacementOptiFine))
        assertEquals(replacementForge, forgeFirst.state.value.selection.forge)
        assertEquals(replacementOptiFine, forgeFirst.state.value.selection.optiFine)

        forgeFirst.select(AddonCardType.FORGE, null)
        assertNull(forgeFirst.state.value.selection.forge)
        assertEquals(replacementOptiFine, forgeFirst.state.value.selection.optiFine)
        forgeFirst.select(AddonCardType.OPTIFINE, null)
        assertNull(forgeFirst.state.value.selection.optiFine)

        val optiFineFirst = VersionAddonViewModel()
        optiFineFirst.select(AddonCardType.OPTIFINE, AddonOption("HD_U_J3", "HD_U_J3", loader = optiFine))
        optiFineFirst.select(AddonCardType.FORGE, AddonOption("52.0.0", "52.0.0", loader = forge))

        assertEquals(forge, optiFineFirst.state.value.selection.forge)
        assertEquals(optiFine, optiFineFirst.state.value.selection.optiFine)
    }

    @Test
    fun selectingAnotherPrimaryLoaderClearsForgeAndOptiFine() {
        val model = VersionAddonViewModel()
        model.select(AddonCardType.FORGE, AddonOption("52.0.0", "52.0.0", loader = LoaderVersion(LoaderKind.FORGE, "1.21.1", "52.0.0")))
        model.select(AddonCardType.OPTIFINE, AddonOption("HD_U_J3", "HD_U_J3", loader = LoaderVersion(LoaderKind.OPTIFINE, "1.21.1", "HD_U_J3")))
        model.select(AddonCardType.FABRIC, AddonOption("0.16.10", "0.16.10", loader = LoaderVersion(LoaderKind.FABRIC, "1.21.1", "0.16.10")))

        assertNull(model.state.value.selection.forge)
        assertNull(model.state.value.selection.optiFine)
        assertEquals("0.16.10", model.state.value.selection.fabric?.loaderVersion)
    }

    private fun apiVersion(projectId: String, version: String, fileName: String) = ModrinthApiVersion(
        projectId = projectId,
        minecraftVersion = "1.21.1",
        version = version,
        fileName = fileName,
        downloadUrl = "https://example.invalid/$fileName",
        sha1 = null,
        size = 1L
    )
}
