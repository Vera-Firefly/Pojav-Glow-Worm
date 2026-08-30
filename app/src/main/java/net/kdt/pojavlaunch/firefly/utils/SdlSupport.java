package net.kdt.pojavlaunch.firefly.utils;

import android.util.Log;

import net.kdt.pojavlaunch.firefly.JMinecraftVersionList;
import net.kdt.pojavlaunch.firefly.value.DependentLibrary;

import org.jackhuang.hmcl.util.versioning.VersionNumber;

/** Detects Minecraft versions that ship the LWJGL SDL3 bindings (26.3+ snapshots). */
public final class SdlSupport {
    private SdlSupport() {}

    public static boolean isSdl3Version(JMinecraftVersionList.Version versionInfo) {
        if (versionInfo != null && versionInfo.libraries != null) {
            for (DependentLibrary library : versionInfo.libraries) {
                if (library == null || library.name == null) continue;
                if (library.name.startsWith("org.lwjgl:lwjgl-sdl") ||
                    library.name.startsWith("org.lwjgl.lwjgl:lwjgl-sdl")) {
                    return true;
                }
            }
        }
        return isAtLeast26_3(versionInfo);
    }

    private static boolean isAtLeast26_3(JMinecraftVersionList.Version versionInfo) {
        if (versionInfo == null || versionInfo.id == null) return false;
        return VersionNumber.compare(
                VersionNumber.asVersion(versionInfo.id).getCanonical(), "26.3") >= 0;
    }
}
