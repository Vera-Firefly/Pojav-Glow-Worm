package net.kdt.pojavlaunch;

import static net.kdt.pojavlaunch.Architecture.archAsString;

import android.app.Activity;
import android.content.res.AssetManager;
import android.util.Log;
import net.kdt.pojavlaunch.multirt.MultiRTUtils;
import net.kdt.pojavlaunch.multirt.Runtime;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.IOException;

public class JRE17Util {
    public static final String JRE_17_NAME = "Internal-17";
    private static boolean checkInternalJre17(AssetManager assetManager) {
        String launcher_jre17_version;
        String installed_jre17_version = MultiRTUtils.__internal__readBinpackVersion(JRE_17_NAME);
        try {
            launcher_jre17_version = Tools.read(assetManager.open("components/jre-17/version"));
        } catch (IOException exc) {
            return installed_jre17_version != null;
        }
        if (!launcher_jre17_version.equals(installed_jre17_version)) {
            return unpackJre17(assetManager, launcher_jre17_version);
        } else {
            return true;
        }
    }

    private static boolean unpackJre17(AssetManager assetManager, String rt_version) {
        try {
            MultiRTUtils.installRuntimeNamedBinpack(
                assetManager.open("components/jre-17/universal.tar.xz"),
                assetManager.open("components/jre-17/bin-" + archAsString(Tools.DEVICE_ARCHITECTURE) + ".tar.xz"),
                "Internal-17", rt_version);
            MultiRTUtils.postPrepare("Internal-17");
            return true;
        } catch (IOException e) {
            Log.e("JRE17Auto", "Internal JRE unpack failed", e);
            return false;
        }
    }
    public static boolean isInternalJRE17(String s_runtime) {
        Runtime runtime = MultiRTUtils.read(s_runtime);
        if(runtime == null) return false;
        return JRE_17_NAME.equals(runtime.name);
    }

    public static boolean installJre17IfNeeded(Activity activity, JMinecraftVersionList.Version versionInfo) {
        if (versionInfo.javaVersion == null || versionInfo.javaVersion.component.equalsIgnoreCase("jre-legacy"))
            return true;

        LauncherProfiles.load();
        MinecraftProfile minecraftProfile = LauncherProfiles.getCurrentProfile();
        String selectedRuntime = Tools.getSelectedRuntime(minecraftProfile);
        Runtime runtime = MultiRTUtils.read(selectedRuntime);

        if (runtime.javaVersion >= versionInfo.javaVersion.majorVersion)
            return true;

        String appropriateRuntime = MultiRTUtils.getNearestJreName(versionInfo.javaVersion.majorVersion);
        if (appropriateRuntime != null) {
            if (JRE17Util.isInternalJRE17(appropriateRuntime)) {
                JRE17Util.checkInternalJre17(activity.getAssets());
            }
            minecraftProfile.javaDir = Tools.LAUNCHERPROFILES_RTPREFIX + appropriateRuntime;
            LauncherProfiles.load();
        } else {
            if (versionInfo.javaVersion.majorVersion <= 17) {
                if (!JRE17Util.checkInternalJre17(activity.getAssets())) {
                    showRuntimeFail(activity, versionInfo);
                    return false;
                } else {
                    minecraftProfile.javaDir = Tools.LAUNCHERPROFILES_RTPREFIX + JRE17Util.JRE_17_NAME;
                    LauncherProfiles.load();
                }
            } else {
                showRuntimeFail(activity, versionInfo);
                return false;
            }
        }
        return true;
    }

    private static void showRuntimeFail(Activity activity, JMinecraftVersionList.Version verInfo) {
        Tools.dialogOnUiThread(activity, activity.getString(R.string.global_error),
                activity.getString(R.string.multirt_nocompartiblert, verInfo.javaVersion.majorVersion));
    }

}
