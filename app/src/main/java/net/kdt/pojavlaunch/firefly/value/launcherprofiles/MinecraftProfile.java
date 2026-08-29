package net.kdt.pojavlaunch.firefly.value.launcherprofiles;

import androidx.annotation.Keep;

@Keep
public class MinecraftProfile {

    public String name;
    public String type;
    public String created;
    public String lastUsed;
    public String icon;
    public int language;
    public String lastVersionId;
    public String gameDir;
    public String javaDir;
    public String javaArgs;
    public String logConfig;
    public boolean logConfigIsXML;
    public String pojavRendererName;
    public boolean enableModsCheck = true;
    public String controlFile;
    public MinecraftResolution[] resolution;

    /**
     * An in-memory game directory supplied by the installed-version configuration.
     * It is intentionally not serialized into legacy launcher profiles.
     */
    public transient String pgwManagedGameDir;

    /**
     * An in-memory graphics API supplied by the installed-version configuration.
     * It is intentionally not serialized into legacy launcher profiles.
     */
    public transient String pgwGraphicsApi;


    public static MinecraftProfile createTemplate() {
        MinecraftProfile TEMPLATE = new MinecraftProfile();
        TEMPLATE.name = "New";
        TEMPLATE.language = 0;
        TEMPLATE.lastVersionId = "Unknown";
        TEMPLATE.enableModsCheck = true;
        return TEMPLATE;
    }

    public static MinecraftProfile getDefaultProfile() {
        MinecraftProfile defaultProfile = new MinecraftProfile();
        defaultProfile.name = "Default";
        defaultProfile.language = 0;
        defaultProfile.lastVersionId = "1.7.10";
        defaultProfile.enableModsCheck = true;
        return defaultProfile;
    }

    public MinecraftProfile() {
    }

    public MinecraftProfile(MinecraftProfile profile) {
        name = profile.name;
        type = profile.type;
        created = profile.created;
        lastUsed = profile.lastUsed;
        icon = profile.icon;
        language = profile.language;
        lastVersionId = profile.lastVersionId;
        gameDir = profile.gameDir;
        javaDir = profile.javaDir;
        javaArgs = profile.javaArgs;
        logConfig = profile.logConfig;
        logConfigIsXML = profile.logConfigIsXML;
        pojavRendererName = profile.pojavRendererName;
        enableModsCheck = profile.enableModsCheck;
        controlFile = profile.controlFile;
        resolution = profile.resolution;
        pgwManagedGameDir = profile.pgwManagedGameDir;
        pgwGraphicsApi = profile.pgwGraphicsApi;
    }
}
