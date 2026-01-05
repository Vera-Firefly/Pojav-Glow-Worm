package net.kdt.pojavlaunch.firefly.tasks;

import static net.kdt.pojavlaunch.firefly.PojavApplication.sExecutorService;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.Build;
import android.util.Log;

import com.kdt.mcgui.ProgressLayout;
import com.movtery.ui.subassembly.customprofilepath.ProfilePathHome;

import net.kdt.pojavlaunch.firefly.Tools;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class AsyncAssetManager {

    private AsyncAssetManager() {
    }

    /**
     * Unpack single files, with no regard to version tracking
     */
    public static void unpackSingleFiles(Context ctx) {
        ProgressLayout.setProgress(ProgressLayout.EXTRACT_SINGLE_FILES, 0);
        sExecutorService.execute(() -> {
            try {
                Tools.copyAssetFile(ctx, "options.txt", ProfilePathHome.getGameHome(), false);
                Tools.copyAssetFile(ctx, "default.json", Tools.CTRLMAP_PATH, false);

                Tools.copyAssetFile(ctx, "launcher_profiles.json", ProfilePathHome.getGameHome(), false);
                Tools.copyAssetFile(ctx, "resolv.conf", Tools.DIR_DATA, false);

                unpackRendererFiles(ctx);
                unpackOtherLoginFiles(ctx);
            } catch (IOException e) {
                Log.e("AsyncAssetManager", "Failed to unpack critical components !");
            }
            ProgressLayout.clearProgress(ProgressLayout.EXTRACT_SINGLE_FILES);
        });
    }

    private static void  unpackRendererFiles(Context ctx) {
        String abi = "/" + Build.SUPPORTED_ABIS[0];
        String NGGL_PATH = "renderer/nggl4es";
        String MGES_PATH = "renderer/mobileglues";
        String MGGL_PATH = "renderer/mobilegl";

        String mesa2304_suffix = "2304";
        String mesa2520_suffix = "2520";
        String MESA_PATH = "renderer/mesa";
        boolean ngglVersion = getRendererVersionFromAssets(ctx, Tools.NG_GL4ES_DIR, NGGL_PATH);
        boolean mgesVersion = getRendererVersionFromAssets(ctx, Tools.MOBILEGLES_DIR, MGES_PATH);
        boolean mgglVersion = getRendererVersionFromAssets(ctx, Tools.MOBILEGL_DIR, MGGL_PATH);
        boolean mesa2304Version = getRendererVersionFromAssets(ctx, Tools.MESA_EGL_DIR + mesa2304_suffix, MESA_PATH + mesa2304_suffix);
        boolean mesa2520Version = getRendererVersionFromAssets(ctx, Tools.MESA_EGL_DIR + mesa2520_suffix, MESA_PATH + mesa2520_suffix);
        try {
            Tools.copyAssetFolder(ctx, NGGL_PATH + abi, Tools.NG_GL4ES_DIR, ngglVersion);
            Tools.copyAssetFolder(ctx, MGES_PATH + abi, Tools.MOBILEGLES_DIR, mgesVersion);
            Tools.copyAssetFolder(ctx, MGGL_PATH + abi, Tools.MOBILEGL_DIR, mgglVersion);
            Tools.copyAssetFolder(ctx, MESA_PATH + mesa2304_suffix + abi, Tools.MESA_EGL_DIR + mesa2304_suffix, mesa2304Version);
            Tools.copyAssetFolder(ctx, MESA_PATH + mesa2520_suffix + abi, Tools.MESA_EGL_DIR + mesa2520_suffix, mesa2520Version);
        } catch (IOException ignored) {

        }
    }

    private static boolean getRendererVersionFromAssets(Context ctx, String DIR, String RendererPath) {
        boolean overwrite = false;
        try {
            String assetsVersionFile = RendererPath + "/version";
            String outVersionFile = DIR + "/version";
            File versionFile = new File(DIR + "/version");
            Tools.copyAssetFile(ctx, assetsVersionFile, versionFile.getParent(), false);
            InputStream in = ctx.getAssets().open(assetsVersionFile);
            byte[] b = new byte[in.available()];
            in.read(b);
            int newVersion = Integer.parseInt(new String(b));
            in.close();
            versionFile.getParentFile().mkdirs();
            int oldVersion = Integer.parseInt(Tools.read(outVersionFile).trim());
            overwrite = newVersion > oldVersion;
            Tools.copyAssetFile(ctx, assetsVersionFile, versionFile.getParent(), overwrite);
        } catch (IOException e) {
            overwrite = true;
            Log.e("AsyncAssetManager", "Failed to read VersionFile !");
        }
        return overwrite;
    }

    private static void unpackOtherLoginFiles(Context ctx) {
        try {
            File path = new File(Tools.DIR_GAME_HOME + "/login/version");
            Tools.copyAssetFile(ctx, "login/version", path.getParent(), false);
            InputStream in = ctx.getAssets().open("login/version");
            byte[] b = new byte[in.available()];
            in.read(b);
            int newVersion = Integer.parseInt(new String(b));
            in.close();
            path.getParentFile().mkdirs();
            int oldVersion = Integer.parseInt(Tools.read(Tools.DIR_GAME_HOME + "/login/version").trim());
            boolean overwrite = newVersion > oldVersion;
            Tools.copyAssetFile(ctx, "login/version", path.getParent(), overwrite);
            Tools.copyAssetFile(ctx, "login/nide8auth.jar", path.getParent(), overwrite);
            Tools.copyAssetFile(ctx, "login/authlib-injector.jar", path.getParent(), overwrite);
        } catch (IOException e) {
            Log.e("AsyncAssetManager", "Failed to unpack critical components !");
        }
    }

    public static void unpackComponents(Context ctx) {
        ProgressLayout.setProgress(ProgressLayout.EXTRACT_COMPONENTS, 0);
        sExecutorService.execute(() -> {
            try {
                unpackComponent(ctx, "caciocavallo", false);
                unpackComponent(ctx, "caciocavallo11", false);
                unpackComponent(ctx, "caciocavallo18", false);
                unpackComponent(ctx, "caciocavallo19", false);
                unpackComponent(ctx, "patcher", false);
                // Since the Java module system doesn't allow multiple JARs to declare the same module,
                // we repack them to a single file here
                unpackComponent(ctx, "lwjgl3", false);
                unpackComponent(ctx, "security", true);
                unpackComponent(ctx, "arc_dns_injector", true);
                unpackComponent(ctx, "forge_installer", true);
            } catch (IOException e) {
                Log.e("AsyncAssetManager", "Failed o unpack components !", e);
            }
            ProgressLayout.clearProgress(ProgressLayout.EXTRACT_COMPONENTS);
        });
    }

    private static void unpackComponent(Context ctx, String component, boolean privateDirectory) throws IOException {
        AssetManager am = ctx.getAssets();
        String rootDir = privateDirectory ? Tools.DIR_DATA : Tools.DIR_GAME_HOME;

        File versionFile = new File(rootDir + "/" + component + "/version");
        InputStream is = am.open("components/" + component + "/version");
        if (!versionFile.exists()) {
            if (versionFile.getParentFile().exists() && versionFile.getParentFile().isDirectory()) {
                FileUtils.deleteDirectory(versionFile.getParentFile());
            }
            versionFile.getParentFile().mkdir();

            Log.i("UnpackPrep", component + ": Pack was installed manually, or does not exist, unpacking new...");
            String[] fileList = am.list("components/" + component);
            for (String s : fileList) {
                Tools.copyAssetFile(ctx, "components/" + component + "/" + s, rootDir + "/" + component, true);
            }
        } else {
            FileInputStream fis = new FileInputStream(versionFile);
            String release1 = Tools.read(is);
            String release2 = Tools.read(fis);
            if (!release1.equals(release2)) {
                if (versionFile.getParentFile().exists() && versionFile.getParentFile().isDirectory()) {
                    FileUtils.deleteDirectory(versionFile.getParentFile());
                }
                versionFile.getParentFile().mkdir();

                String[] fileList = am.list("components/" + component);
                for (String fileName : fileList) {
                    Tools.copyAssetFile(ctx, "components/" + component + "/" + fileName, rootDir + "/" + component, true);
                }
            } else {
                Log.i("UnpackPrep", component + ": Pack is up-to-date with the launcher, continuing...");
            }
        }
    }
}
