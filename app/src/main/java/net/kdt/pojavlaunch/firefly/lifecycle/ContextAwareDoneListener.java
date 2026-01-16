package net.kdt.pojavlaunch.firefly.lifecycle;

import static net.kdt.pojavlaunch.firefly.MainActivity.INTENT_MINECRAFT_VERSION;
import static net.kdt.pojavlaunch.firefly.prefs.LauncherPreferences.PREF_QUIT_LAUNCHER;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;

import com.kdt.mcgui.ProgressLayout;
import com.movtery.context.ContextExecutor;
import com.movtery.feature.mod.parser.ModCheckResult;
import com.movtery.feature.mod.parser.ModChecker;
import com.movtery.feature.mod.parser.ModInfo;
import com.movtery.feature.mod.parser.ModParser;
import com.movtery.feature.mod.parser.ModParserListener;

import net.kdt.pojavlaunch.firefly.MainActivity;
import net.kdt.pojavlaunch.firefly.R;
import net.kdt.pojavlaunch.firefly.Tools;
import net.kdt.pojavlaunch.firefly.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.firefly.tasks.AsyncMinecraftDownloader;
import net.kdt.pojavlaunch.firefly.utils.NotificationUtils;
import net.kdt.pojavlaunch.firefly.value.launcherprofiles.LauncherProfiles;

import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class ContextAwareDoneListener implements AsyncMinecraftDownloader.DoneListener, ContextExecutorTask {
    private final String mErrorString;
    private final String mNormalizedVersionid;
    private static volatile boolean shouldQuitLauncher = false;

    public ContextAwareDoneListener(Context baseContext, String versionId) {
        this.mErrorString = baseContext.getString(R.string.mc_download_failed);
        this.mNormalizedVersionid = versionId;
    }

    private Intent createGameStartIntent(Context context) {
        Intent mainIntent = new Intent(context, MainActivity.class);
        mainIntent.putExtra(INTENT_MINECRAFT_VERSION, mNormalizedVersionid);
        mainIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return mainIntent;
    }

    private void onQuitLauncher() {
        shouldQuitLauncher = !PREF_QUIT_LAUNCHER;
    }

    private void executeTask() {
        ProgressKeeper.waitUntilDone(() -> ContextExecutor.executeTask(this));
    }

    @Override
    public void onDownloadDone() {
        if (!Tools.ENABLE_MODS_CHECK) {
            executeTask();
            return;
        }

        AtomicInteger progressCount = new AtomicInteger(0);
        ModParserListener listener = createModParserListener(progressCount);
        File gameDir = Tools.getGameDirPath(LauncherProfiles.getCurrentProfile());

        ModParser.checkAllMods(gameDir, listener);
    }

    private ModParserListener createModParserListener(AtomicInteger progressCount) {
        return new ModParserListener() {
            @Override
            public void onProgress(@NonNull ModInfo recentlyParsedModInfo, int totalFileCount) {
                updateProgress(progressCount, totalFileCount);
            }

            @Override
            public void onParseEnded(@NonNull List<? extends ModInfo> modInfoList) {
                handleParseResult(modInfoList);
            }
        };
    }

    private void updateProgress(AtomicInteger progressCount, int totalFileCount) {
        int i = progressCount.incrementAndGet();
        ProgressLayout.setProgress(
                ProgressLayout.INSTALL_MODPACK,
                i * 100 / totalFileCount,
                R.string.mod_check_progress_message,
                i,
                totalFileCount
        );
    }

    private void handleParseResult(List<? extends ModInfo> modInfoList) {
        ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);

        ContextExecutor.executeTaskWithAllContext(context -> {
            clearAndProcessModCheck(context, modInfoList);
        });
    }

    private void clearAndProcessModCheck(Context context, List<? extends ModInfo> modInfoList) {
        ModCheckResult.clear(context);
        if (modInfoList.isEmpty()) executeTask();
        else new ModChecker().check(context, modInfoList, result -> {
            executeTask();
            return null;
        });
    }

    @Override
    public void onDownloadFailed(Throwable throwable) {
        Tools.showErrorRemote(mErrorString, throwable);
    }

    @Override
    public void executeWithActivity(Activity activity) {
        try {
            onQuitLauncher();
            Intent gameStartIntent = createGameStartIntent(activity);
            activity.startActivity(gameStartIntent);
            if (!shouldQuitLauncher) {
                activity.finish();
                android.os.Process.killProcess(android.os.Process.myPid()); //You should kill yourself, NOW!
            } else {
                // Nothing to do here
            }
        } catch (Throwable e) {
            Tools.showError(activity.getBaseContext(), e);
        }
    }

    @Override
    public void executeWithApplication(Context context) {
        Intent gameStartIntent = createGameStartIntent(context);
        // Since the game is a separate process anyway, it does not matter if it gets invoked
        // from somewhere other than the launcher activity.
        // The only problem may arise if the launcher starts doing something when the user starts the notification.
        // So, the notification is automatically removed once there are tasks ongoing in the ProgressKeeper
        NotificationUtils.sendBasicNotification(context,
                R.string.notif_download_finished,
                R.string.notif_download_finished_desc,
                gameStartIntent,
                NotificationUtils.PENDINGINTENT_CODE_GAME_START,
                NotificationUtils.NOTIFICATION_ID_GAME_START
        );
        // You should keep yourself safe, NOW!
        // otherwise android does weird things...
    }
}
