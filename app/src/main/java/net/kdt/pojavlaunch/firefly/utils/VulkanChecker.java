/*
 * Zalith Launcher 2
 * Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package net.kdt.pojavlaunch.firefly.utils;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;

import java.io.File;

public final class VulkanChecker {
    static {
        System.loadLibrary("vulkan_check");
    }

    private VulkanChecker() {
    }

    @Nullable
    public static VulkanCapabilities checkCapabilities(@Nullable String nativeDir, @Nullable File cacheDir) {
        try {
            if (cacheDir != null && !cacheDir.exists() && !cacheDir.mkdirs()) return null;
            return nativeCheckVulkan(nativeDir, cacheDir == null ? null : cacheDir.getAbsolutePath());
        } catch (UnsatisfiedLinkError ignored) {
            return null;
        } finally {
            if (cacheDir != null) deleteContents(cacheDir);
        }
    }

    private static void deleteContents(File directory) {
        File[] children = directory.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory()) deleteContents(child);
                child.delete();
            }
        }
        directory.delete();
    }

    @Keep
    private static native VulkanCapabilities nativeCheckVulkan(@Nullable String nativeDir, @Nullable String cacheDir);
}
