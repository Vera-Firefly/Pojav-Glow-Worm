package net.kdt.pojavlaunch.value.launcherprofiles;

import androidx.annotation.Keep;

import net.kdt.pojavlaunch.Tools;

import java.util.HashMap;
import java.util.Map;

@Keep
public class MinecraftLauncherProfiles {
    public Map<String, MinecraftProfile> profiles = new HashMap<>();
    public boolean profilesWereMigrated;
    public String clientToken;
    public Map<String, MinecraftAuthenticationDatabase> authenticationDatabase;
    // public Map launcherVersion;
    public MinecraftLauncherSettings settings;
    // public Map analyticsToken;
    public int analyticsFailcount;
    public MinecraftSelectedUser selectedUser;

    public String toJson() {
        return Tools.GLOBAL_GSON.toJson(this);
    }
}
