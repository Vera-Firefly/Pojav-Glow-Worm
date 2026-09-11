package net.kdt.pojavlaunch.modmanager;

import android.util.Log;
import java.io.*;
import java.util.jar.*;
import java.util.zip.*;
import org.json.*;

/**
 * ModMetadata represents metadata information for a Minecraft mod
 * Supports both Forge and Fabric mod formats
 */
public class ModMetadata {
    private static final String TAG = "ModMetadata";

    private String id;
    private String name;
    private String version;
    private String modLoader; // "forge", "fabric", or "forge/fabric"
    private String description;
    private String[] authors;
    private String[] minecraftVersions;
    private boolean enabled;
    private String fileName;
    private long fileSize;
    private long installedTime;

    public ModMetadata() {
        this.enabled = true;
        this.installedTime = System.currentTimeMillis();
        this.authors = new String[0];
        this.minecraftVersions = new String[0];
    }

    /**
     * Parse mod metadata from a JAR file
     */
    public static ModMetadata fromFile(File modFile) throws Exception {
        ModMetadata metadata = new ModMetadata();
        metadata.setFileName(modFile.getName());
        metadata.setFileSize(modFile.length());

        try (ZipFile zipFile = new ZipFile(modFile)) {
            // Try Fabric mod metadata first
            ZipEntry fabricEntry = zipFile.getEntry("fabric.mod.json");
            if (fabricEntry != null) {
                return parseFabricMod(metadata, zipFile, fabricEntry);
            }

            // Try Forge mod metadata
            ZipEntry forgeEntry = zipFile.getEntry("META-INF/mods.toml");
            if (forgeEntry != null) {
                return parseForgeMod(metadata, zipFile, forgeEntry);
            }

            // Fallback: try mcmod.info
            ZipEntry mcmodEntry = zipFile.getEntry("mcmod.info");
            if (mcmodEntry != null) {
                return parseMcmodInfo(metadata, zipFile, mcmodEntry);
            }

            // If no metadata found, use file name
            metadata.setId(modFile.getName().replace(".jar", "").replace(".mod", ""));
            metadata.setName(metadata.getId());
            metadata.setVersion("unknown");
            metadata.setModLoader("unknown");
        }

        return metadata;
    }

    private static ModMetadata parseFabricMod(ModMetadata metadata, ZipFile zipFile, 
                                             ZipEntry entry) throws Exception {
        try (InputStream is = zipFile.getInputStream(entry)) {
            String content = readStream(is);
            JSONObject json = new JSONObject(content);

            metadata.setId(json.getString("id"));
            metadata.setName(json.getString("name"));
            metadata.setVersion(json.getString("version"));
            metadata.setModLoader("fabric");
            
            if (json.has("description")) {
                metadata.setDescription(json.getString("description"));
            }

            if (json.has("authors")) {
                Object authorsObj = json.get("authors");
                if (authorsObj instanceof JSONArray) {
                    JSONArray authorsArray = (JSONArray) authorsObj;
                    String[] authors = new String[authorsArray.length()];
                    for (int i = 0; i < authorsArray.length(); i++) {
                        authors[i] = authorsArray.getString(i);
                    }
                    metadata.setAuthors(authors);
                }
            }
        }
        return metadata;
    }

    private static ModMetadata parseForgeMod(ModMetadata metadata, ZipFile zipFile, 
                                            ZipEntry entry) throws Exception {
        try (InputStream is = zipFile.getInputStream(entry)) {
            String content = readStream(is);
            String[] lines = content.split("\n");
            
            for (String line : lines) {
                line = line.trim();
                if (line.startsWith("modId")) {
                    metadata.setId(extractValue(line));
                } else if (line.startsWith("displayName")) {
                    metadata.setName(extractValue(line));
                } else if (line.startsWith("version")) {
                    metadata.setVersion(extractValue(line));
                } else if (line.startsWith("description")) {
                    metadata.setDescription(extractValue(line));
                }
            }

            metadata.setModLoader("forge");
            if (metadata.getId() == null) {
                metadata.setId("forge_mod_" + System.currentTimeMillis());
            }
            if (metadata.getName() == null) {
                metadata.setName(metadata.getId());
            }
        }
        return metadata;
    }

    private static ModMetadata parseMcmodInfo(ModMetadata metadata, ZipFile zipFile, 
                                             ZipEntry entry) throws Exception {
        try (InputStream is = zipFile.getInputStream(entry)) {
            String content = readStream(is);
            JSONArray jsonArray = new JSONArray(content);
            if (jsonArray.length() > 0) {
                JSONObject json = jsonArray.getJSONObject(0);
                metadata.setId(json.getString("modid"));
                metadata.setName(json.getString("name"));
                metadata.setVersion(json.getString("version"));
                metadata.setModLoader("forge");
                if (json.has("description")) {
                    metadata.setDescription(json.getString("description"));
                }
            }
        }
        return metadata;
    }

    private static String extractValue(String line) {
        int equalsIndex = line.indexOf('=');
        if (equalsIndex != -1) {
            String value = line.substring(equalsIndex + 1).trim();
            // Remove quotes if present
            if (value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1);
            }
            return value;
        }
        return null;
    }

    private static String readStream(InputStream is) throws IOException {
        StringBuilder sb = new StringBuilder();
        byte[] buffer = new byte[1024];
        int length;
        while ((length = is.read(buffer)) != -1) {
            sb.append(new String(buffer, 0, length));
        }
        return sb.toString();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getModLoader() { return modLoader; }
    public void setModLoader(String modLoader) { this.modLoader = modLoader; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String[] getAuthors() { return authors; }
    public void setAuthors(String[] authors) { this.authors = authors; }

    public String[] getMinecraftVersions() { return minecraftVersions; }
    public void setMinecraftVersions(String[] versions) { this.minecraftVersions = versions; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }

    public long getInstalledTime() { return installedTime; }
    public void setInstalledTime(long installedTime) { this.installedTime = installedTime; }

    @Override
    public String toString() {
        return "ModMetadata{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", version='" + version + '\'' +
                ", modLoader='" + modLoader + '\'' +
                ", enabled=" + enabled +
                '}';
    }
}
