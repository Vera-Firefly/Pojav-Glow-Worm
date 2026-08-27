package net.kdt.pojavlaunch.firefly.utils;

import androidx.annotation.NonNull;

/** Applies the Minecraft graphics backend preference for a game directory. */
public final class MinecraftGraphicsApi {
    private static final String OPTION = "preferredGraphicsBackend";

    private MinecraftGraphicsApi() {
    }

    public static void apply(@NonNull String gameDirectory, @NonNull String mode) {
        if ("default".equals(mode)) return;

        MCOptionUtils.load(gameDirectory);
        if ("default_opengl".equals(mode) && MCOptionUtils.containsKey(OPTION)) return;

        String backend = "vulkan".equals(mode) ? "\"vulkan\"" : "\"opengl\"";
        MCOptionUtils.set(OPTION, backend);
        MCOptionUtils.save();
    }
}
