package com.firefly.utils;

import com.movtery.plugins.renderer.RendererPlugin;

import java.util.*;

public class RendererUtils {
    private static final Map<String, String> pluginRendererValue = new HashMap<>();
    private static final Map<String, String> launcherRendererIds = new HashMap<>();

    public static void setPluginRendererArray() {
        if (RendererPlugin.isAvailable()) {
            RendererPlugin.getRendererList().forEach(renderer -> {
                if (renderer.getIdName() != null && renderer.getDes() != null) {
                    pluginRendererValue.put(renderer.getIdName(), renderer.getDes());
                }
            });
        }
    }

    public static void generateAndStoreUUIDs() {
        if (RendererPlugin.isAvailable()) {
            RendererPlugin.getRendererList().forEach(renderer -> {
                if (renderer.getIdName() != null && renderer.getId() != null) {
                    launcherRendererIds.put(renderer.getIdName(), renderer.getId());
                }
            });
        }
    }

    public static List<String> rendererIds() {
        List<String> ids = new ArrayList<>();
        launcherRendererIds.forEach((key, value) -> ids.add(key));
        return ids;
    }

    private static final Set<String> GALLIUM_RENDERERS = Set.of(
    "virgl", "zink", "freedreno", "panfrost", "softpipe", "llvmpipe"
    );

    public static boolean isGalliumRenderer(String envValue) {
        return GALLIUM_RENDERERS.stream().anyMatch(envValue::contains);
    }

    public static String getGalliumRenderer(String envValue) {
        return GALLIUM_RENDERERS.stream()
                .filter(envValue::contains)
                .findFirst()
                .orElse("zink");
    }

}