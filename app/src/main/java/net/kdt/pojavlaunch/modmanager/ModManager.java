package net.kdt.pojavlaunch.modmanager;

import android.content.Context;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * ModManager handles installation, loading, and management of Minecraft mods
 * Supports Forge and Fabric mod loaders
 */
public class ModManager {
    private final Context context;
    private final File modsDirectory;
    private final File modCacheDirectory;
    private final ExecutorService executorService;
    private final List<ModListener> listeners;
    private final Map<String, ModMetadata> loadedMods;

    public interface ModListener {
        void onModInstalled(ModMetadata mod);
        void onModRemoved(String modId);
        void onModsLoaded(List<ModMetadata> mods);
        void onModError(String modId, Exception error);
    }

    public ModManager(Context context, File gameDirectory) {
        this.context = context;
        this.modsDirectory = new File(gameDirectory, "mods");
        this.modCacheDirectory = new File(context.getCacheDir(), "mod_cache");
        this.executorService = Executors.newFixedThreadPool(2);
        this.listeners = Collections.synchronizedList(new ArrayList<>());
        this.loadedMods = new ConcurrentHashMap<>();

        // Create directories if they don't exist
        if (!modsDirectory.exists()) {
            modsDirectory.mkdirs();
        }
        if (!modCacheDirectory.exists()) {
            modCacheDirectory.mkdirs();
        }
    }

    /**
     * Load all mods from the mods directory
     */
    public void loadMods() {
        executorService.execute(() -> {
            try {
                loadedMods.clear();
                File[] modFiles = modsDirectory.listFiles((dir, name) -> 
                    name.endsWith(".jar") || name.endsWith(".mod"));

                if (modFiles != null) {
                    for (File modFile : modFiles) {
                        try {
                            ModMetadata metadata = ModMetadata.fromFile(modFile);
                            loadedMods.put(metadata.getId(), metadata);
                        } catch (Exception e) {
                            notifyModError(modFile.getName(), e);
                        }
                    }
                }

                notifyModsLoaded(new ArrayList<>(loadedMods.values()));
            } catch (Exception e) {
                notifyModError("general", e);
            }
        });
    }

    /**
     * Install a mod from a JAR file
     */
    public void installMod(File modFile) {
        executorService.execute(() -> {
            try {
                ModMetadata metadata = ModMetadata.fromFile(modFile);
                File targetFile = new File(modsDirectory, modFile.getName());

                // Copy mod file to mods directory
                Files.copy(modFile.toPath(), targetFile.toPath(), 
                    StandardCopyOption.REPLACE_EXISTING);

                loadedMods.put(metadata.getId(), metadata);
                notifyModInstalled(metadata);
            } catch (Exception e) {
                notifyModError("install", e);
            }
        });
    }

    /**
     * Uninstall a mod by ID
     */
    public void uninstallMod(String modId) {
        executorService.execute(() -> {
            try {
                ModMetadata mod = loadedMods.get(modId);
                if (mod != null) {
                    File modFile = new File(modsDirectory, mod.getFileName());
                    if (modFile.exists()) {
                        Files.delete(modFile.toPath());
                    }
                    loadedMods.remove(modId);
                    notifyModRemoved(modId);
                } else {
                    throw new FileNotFoundException("Mod not found: " + modId);
                }
            } catch (Exception e) {
                notifyModError(modId, e);
            }
        });
    }

    /**
     * Enable or disable a mod
     */
    public void setModEnabled(String modId, boolean enabled) {
        ModMetadata mod = loadedMods.get(modId);
        if (mod != null) {
            mod.setEnabled(enabled);
            // Persist state
            saveMod(mod);
        }
    }

    /**
     * Get a mod by ID
     */
    public ModMetadata getMod(String modId) {
        return loadedMods.get(modId);
    }

    /**
     * Get all loaded mods
     */
    public List<ModMetadata> getAllMods() {
        return new ArrayList<>(loadedMods.values());
    }

    /**
     * Get enabled mods only
     */
    public List<ModMetadata> getEnabledMods() {
        List<ModMetadata> enabled = new ArrayList<>();
        for (ModMetadata mod : loadedMods.values()) {
            if (mod.isEnabled()) {
                enabled.add(mod);
            }
        }
        return enabled;
    }

    /**
     * Check if mod loader (Forge/Fabric) is installed
     */
    public boolean isModLoaderInstalled(String loaderType) {
        for (ModMetadata mod : loadedMods.values()) {
            if (mod.getModLoader().equalsIgnoreCase(loaderType)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Add a listener for mod events
     */
    public void addListener(ModListener listener) {
        listeners.add(listener);
    }

    /**
     * Remove a listener
     */
    public void removeListener(ModListener listener) {
        listeners.remove(listener);
    }

    private void saveMod(ModMetadata mod) {
        // Implementation for persisting mod state
    }

    private void notifyModInstalled(ModMetadata mod) {
        for (ModListener listener : listeners) {
            listener.onModInstalled(mod);
        }
    }

    private void notifyModRemoved(String modId) {
        for (ModListener listener : listeners) {
            listener.onModRemoved(modId);
        }
    }

    private void notifyModsLoaded(List<ModMetadata> mods) {
        for (ModListener listener : listeners) {
            listener.onModsLoaded(mods);
        }
    }

    private void notifyModError(String modId, Exception error) {
        for (ModListener listener : listeners) {
            listener.onModError(modId, error);
        }
    }

    /**
     * Shutdown the mod manager
     */
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
