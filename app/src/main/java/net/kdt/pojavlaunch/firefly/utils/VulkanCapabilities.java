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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Keep
public final class VulkanCapabilities {
    public static final List<String> REQUIRED_EXTENSIONS = Collections.unmodifiableList(Arrays.asList(
            "VK_KHR_dynamic_rendering",
            "VK_KHR_push_descriptor",
            "VK_KHR_synchronization2",
            "VK_EXT_vertex_attribute_divisor",
            "VK_KHR_swapchain"
    ));
    public static final List<String> REQUIRED_FEATURES = Collections.unmodifiableList(Arrays.asList(
            "multiDrawIndirect",
            "fillModeNonSolid",
            "samplerAnisotropy",
            "shaderDrawParameters",
            "timelineSemaphore",
            "hostQueryReset",
            "synchronization2",
            "dynamicRendering",
            "vertexAttributeInstanceRateDivisor"
    ));

    private final int apiVersionMajor;
    private final int apiVersionMinor;
    private final int apiVersionPatch;
    private final List<String> extensions;
    private final Map<String, Boolean> features;

    @Keep
    public VulkanCapabilities(int apiVersionMajor, int apiVersionMinor, int apiVersionPatch,
                              List<String> extensions, Map<String, Boolean> features) {
        this.apiVersionMajor = apiVersionMajor;
        this.apiVersionMinor = apiVersionMinor;
        this.apiVersionPatch = apiVersionPatch;
        this.extensions = extensions;
        this.features = features;
    }

    public String getVersionString() {
        return apiVersionMajor + "." + apiVersionMinor + "." + apiVersionPatch;
    }

    public boolean isVersionSupported() {
        return apiVersionMajor > 1 || (apiVersionMajor == 1 && apiVersionMinor >= 2);
    }

    public List<String> getMissingExtensions() {
        return getMissingRequirements(REQUIRED_EXTENSIONS, extensions);
    }

    public List<String> getMissingFeatures() {
        java.util.ArrayList<String> missing = new java.util.ArrayList<>();
        for (String feature : REQUIRED_FEATURES) {
            if (!Boolean.TRUE.equals(features.get(feature))) missing.add(feature);
        }
        return missing;
    }

    public boolean isAllSupported() {
        return isVersionSupported() && getMissingExtensions().isEmpty() && getMissingFeatures().isEmpty();
    }

    private static List<String> getMissingRequirements(List<String> required, List<String> available) {
        java.util.ArrayList<String> missing = new java.util.ArrayList<>();
        for (String requirement : required) {
            if (!available.contains(requirement)) missing.add(requirement);
        }
        return missing;
    }
}
