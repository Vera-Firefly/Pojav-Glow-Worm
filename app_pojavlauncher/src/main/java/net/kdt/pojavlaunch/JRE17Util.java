package net.kdt.pojavlaunch;

import android.R;
import android.app.Activity;
import android.content.res.AssetManager;
import android.util.Log;
import java.io.IOException;
import net.kdt.pojavlaunch.Architecture.archAsString;
import net.kdt.pojavlaunch.multirt.MultiRTUtils;
import net.kdt.pojavlaunch.multirt.Runtime;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

public class JRE17Util {
    public static final String NEW_JRE_NAME = "Internal-21";
    public static boolean checkInternalNewJre(AssetManager assetManager) {
        String launcher_jre17_version, launcher_jre21_version;
        String installed_jre17_version = MultiRTUtils.__internal__readBinpackVersion("Internal-17");
        String installed_jre21_version = MultiRTUtils.__internal__readBinpackVersion("Internal-21");
        try {
            launcher_jre17_version = Tools.read(assetManager.open("components/jre-17/version"));
            launcher_jre21_version = Tools.read(assetManager.open("components/jre-21/version"));
        } catch (IOException exc) {
            return installed_jre17_version != null || installed_jre21_version != null;
        }
        if(!launcher_jre17_version.equals(installed_jre17_version))
            return unpackJre17(assetManager, launcher_jre17_version);
        else if(!launcher_jre21_version.equals(installed_jre21_version))
            return unpackJre21(assetManager, launcher_jre21_version);
        else return true;
    }

    private static boolean unpackJre17(AssetManager assetManager, String rt_version) {
        try {
            MultiRTUtils.installRuntimeNamedBinpack(
                    assetManager.open("components/jre-17/universal.tar.xz"),
                    assetManager.open("components/jre-17/bin-" + archAsString(Tools.DEVICE_ARCHITECTURE) + ".tar.xz"),
                    "Internal-17", rt_version);
            MultiRTUtils.postPrepare("Internal-17");
            return true;
        }catch (IOException e) {
            Log.e("JRE17Auto", "Internal JRE unpack failed", e);
            return false;
        }
    }
    private static boolean unpackJre21(AssetManager assetManager, String rt_version) {
        try {
            MultiRTUtils.installRuntimeNamedBinpack(
                    assetManager.open("components/jre-21/universal.tar.xz"),
                    assetManager.open("components/jre-21/bin-" + archAsString(Tools.DEVICE_ARCHITECTURE) + ".tar.xz"),
                    "Internal-21", rt_version);
            MultiRTUtils.postPrepare("Internal-21");
            return true;
        }catch (IOException e) {
            Log.e("JRE21Auto", "Internal JRE unpack failed", e);
            return false;
        }
    }
    public static boolean isInternalNewJRE(String s_runtime) {
        Runtime runtime = MultiRTUtils.read(s_runtime);
        if(runtime == null) return false;
        return NEW_JRE_NAME.equals(runtime.name);
    }

    /** @return true if everything is good, false otherwise.  */
    public static boolean installNewJreIfNeeded(Activity activity, JMinecraftVersionList.Version versionInfo) {
        if (versionInfo.javaVersion == null || versionInfo.javaVersion.component.equalsIgnoreCase("jre-legacy"))
            return true;

        LauncherProfiles.load();
        MinecraftProfile minecraftProfile = LauncherProfiles.getCurrentProfile();

        String selectedRuntime = Tools.getSelectedRuntime(minecraftProfile);

        Runtime runtime = MultiRTUtils.read(selectedRuntime);
        if (runtime.javaVersion >= versionInfo.javaVersion.majorVersion) {
            return true;
        }

        String appropriateRuntime = MultiRTUtils.getNearestJreName(versionInfo.javaVersion.majorVersion);
        if (appropriateRuntime != null) {
            if (JRE17Util.isInternalNewJRE(appropriateRuntime)) {
                JRE17Util.checkInternalNewJre(activity.getAssets());
            }
            minecraftProfile.javaDir = Tools.LAUNCHERPROFILES_RTPREFIX + appropriateRuntime;
            LauncherProfiles.load();
        } else {
            if (versionInfo.javaVersion.majorVersion <= 17) {
                if (!JRE17Util.checkInternalNewJre(activity.getAssets())){
                    showRuntimeFail(activity, versionInfo);
                    return false;
                } else {
                    minecraftProfile.javaDir = Tools.LAUNCHERPROFILES_RTPREFIX + JRE17Util.NEW_JRE_NAME;
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