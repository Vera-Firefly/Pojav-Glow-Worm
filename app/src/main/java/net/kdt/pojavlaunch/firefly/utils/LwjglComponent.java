package net.kdt.pojavlaunch.firefly.utils;

import androidx.annotation.NonNull;

import net.kdt.pojavlaunch.firefly.Architecture;
import net.kdt.pojavlaunch.firefly.JMinecraftVersionList;
import net.kdt.pojavlaunch.firefly.Tools;
import net.kdt.pojavlaunch.firefly.value.DependentLibrary;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** Resolves the bundled LWJGL component required by a Minecraft version. */
public final class LwjglComponent {
    private static final String VERSION_333 = "3.3.3";
    private static final String VERSION_341 = "3.4.1";

    private final int lwjglVersion;

    private LwjglComponent(int lwjglVersion) {
        this.lwjglVersion = lwjglVersion;
    }

    public static LwjglComponent forVersion(JMinecraftVersionList.Version versionInfo) {
        int detectedVersion = 0;
        if (versionInfo != null && versionInfo.libraries != null) {
            for (DependentLibrary library : versionInfo.libraries) {
                if (library == null || library.name == null) continue;
                String prefix = library.name.startsWith("org.lwjgl:lwjgl:")
                        ? "org.lwjgl:lwjgl:"
                        : library.name.startsWith("org.lwjgl.lwjgl:lwjgl:") ? "org.lwjgl.lwjgl:lwjgl:" : null;
                if (prefix == null) continue;
                String versionPart = library.name.substring(prefix.length());
                int versionEnd = 0;
                while (versionEnd < versionPart.length()) {
                    char character = versionPart.charAt(versionEnd);
                    if (!Character.isDigit(character) && character != '.') break;
                    versionEnd++;
                }
                String numericVersion = versionPart.substring(0, versionEnd).replace(".", "");
                try {
                    detectedVersion = Integer.parseInt(numericVersion);
                } catch (NumberFormatException ignored) {
                }
                break;
            }
        }
        return new LwjglComponent(detectedVersion);
    }

    @NonNull
    public File getDirectory() {
        return new File(new File(new File(Tools.DIR_DATA, "components"), "lwjgl"), getDirectoryName());
    }

    @NonNull
    public File getNativeDirectory() {
        return new File(new File(getDirectory(), "natives"), getAbiDirectory());
    }

    @NonNull
    public String getClassPath() {
        File[] files = getDirectory().listFiles((directory, name) -> name.endsWith(".jar"));
        if (files == null) return "";
        Arrays.sort(files, Comparator.comparingInt(file -> getJarOrder(file.getName())));
        List<String> classpath = new ArrayList<>();
        boolean lwjgl2 = lwjglVersion >= 200 && lwjglVersion <= 299;
        for (File file : files) {
            if (!lwjgl2 && "lwjgl-lwjglx.jar".equals(file.getName())) continue;
            classpath.add(file.getAbsolutePath());
        }
        return String.join(":", classpath);
    }

    private String getDirectoryName() {
        return lwjglVersion >= 341 ? VERSION_341 : VERSION_333;
    }

    private static int getJarOrder(String name) {
        if ("lwjgl.jar".equals(name)) return 0;
        if (name.endsWith("-merged-modules.jar")) return 1;
        if ("lwjgl-lwjglx.jar".equals(name)) return 3;
        return 2;
    }

    private static String getAbiDirectory() {
        switch (Architecture.getDeviceArchitecture()) {
            case Architecture.ARCH_ARM64: return "arm64-v8a";
            case Architecture.ARCH_ARM: return "armeabi-v7a";
            case Architecture.ARCH_X86: return "x86";
            case Architecture.ARCH_X86_64: return "x86_64";
            default: throw new IllegalStateException("Unsupported device architecture");
        }
    }
}
