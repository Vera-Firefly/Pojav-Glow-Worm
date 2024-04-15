package com.qz.terminal2;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import com.termux.terminal.EmulatorDebug;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSession.SessionChangedCallback;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.kdt.pojavlaunch.Tools;

public final class TermuxService extends Service implements SessionChangedCallback {

    /** Note that this is a symlink on the Android M preview. */
    
    public String HOME_PATH = new File(Tools.DIR_DATA, "files").getAbsolutePath();

    private static final String ACTION_STOP_SERVICE = "com.termux.service_stop";
    private static final String ACTION_LOCK_WAKE = "com.termux.service_wake_lock";
    private static final String ACTION_UNLOCK_WAKE = "com.termux.service_wake_unlock";
    /** Intent action to launch a new terminal session. Executed from TermuxWidgetProvider. */
    public static final String ACTION_EXECUTE = "com.termux.service_execute";

    public static final String EXTRA_ARGUMENTS = "com.termux.execute.arguments";

    public static final String EXTRA_CURRENT_WORKING_DIRECTORY = "com.termux.execute.cwd";
    
    /** This service is only bound from inside the same process and never uses IPC. */
    class LocalBinder extends Binder {
        public final TermuxService service = TermuxService.this;
    }

    private final IBinder mBinder = new LocalBinder();

    private final Handler mHandler = new Handler();

    private TerminalSession mTerminalSessions = null;

    SessionChangedCallback mSessionChangeCallback;

    /** The wake lock and wifi lock are always acquired and released together. */
    private PowerManager.WakeLock mWakeLock;
    private WifiManager.WifiLock mWifiLock;

    /** If the user has executed the {@link #ACTION_STOP_SERVICE} intent. */
    boolean mWantsToStop = false;

    @SuppressLint("Wakelock")
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        setFolderPermissions(Tools.MULTIRT_HOME);
        
        String action = intent.getAction();
        if (ACTION_STOP_SERVICE.equals(action)) {
            mWantsToStop = true;
            mTerminalSessions.finishIfRunning();
            stopSelf();
        } else if (ACTION_LOCK_WAKE.equals(action)) {
            if (mWakeLock == null) {
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, EmulatorDebug.LOG_TAG);
                mWakeLock.acquire();

                // http://tools.android.com/tech-docs/lint-in-studio-2-3#TOC-WifiManager-Leak
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                mWifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, EmulatorDebug.LOG_TAG);
                mWifiLock.acquire();

            }
        } else if (ACTION_UNLOCK_WAKE.equals(action)) {
            if (mWakeLock != null) {
                mWakeLock.release();
                mWakeLock = null;

                mWifiLock.release();
                mWifiLock = null;

            }
        } else if (ACTION_EXECUTE.equals(action)) {
            
            Uri executableUri = intent.getData();
            String executablePath = (executableUri == null ? null : executableUri.getPath());

            String[] arguments = (executableUri == null ? null : intent.getStringArrayExtra(EXTRA_ARGUMENTS));
            String cwd = intent.getStringExtra(EXTRA_CURRENT_WORKING_DIRECTORY);

            mTerminalSessions = createTermSession(executablePath, arguments, cwd, true);
            
            if (executablePath != null) {
                int lastSlash = executablePath.lastIndexOf('/');
                String name = (lastSlash == -1) ? executablePath : executablePath.substring(lastSlash + 1);
                name = name.replace('-', ' ');
                mTerminalSessions.mSessionName = name;
            }
        } else if (action != null) {
            Log.e(EmulatorDebug.LOG_TAG, "Unknown TermuxService action: '" + action + "'");
        }


        // If this service really do get killed, there is no point restarting it automatically - let the user do on next
        // start of {@link Term):
        return Service.START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {

    }


    public TerminalSession getTermSession()
    {
        return mTerminalSessions;
    }
    @Override
    public void onDestroy() {
        if (mWakeLock != null) mWakeLock.release();
        if (mWifiLock != null) mWifiLock.release();

        stopForeground(true);
        mTerminalSessions.finishIfRunning();

    }

    TerminalSession createTermSession(String executablePath, String[] arguments, String cwd, boolean failSafe) {
       File f= new File(HOME_PATH);
        if(!f.exists())
            f.mkdirs();

        if (cwd == null)
            cwd = HOME_PATH;

        String[] env = buildEnvironment(failSafe, cwd);
        boolean isLoginShell = false;

        if (executablePath == null) {
            executablePath = "/system/bin/sh";
            isLoginShell = true;
        }

        String[] processArgs = setupProcessArgs(executablePath, arguments);
        executablePath = processArgs[0];
        int lastSlashIndex = executablePath.lastIndexOf('/');
        String processName = (isLoginShell ? "-" : "") +
            (lastSlashIndex == -1 ? executablePath : executablePath.substring(lastSlashIndex + 1));

        String[] args = new String[processArgs.length];
        args[0] = processName;
        if (processArgs.length > 1) System.arraycopy(processArgs, 1, args, 1, processArgs.length - 1);

        TerminalSession session = new TerminalSession(executablePath, cwd, args, env, this);
        return session;
    }

    public void removeTermSession() {
            stopSelf();
    }

    @Override
    public void onTitleChanged(TerminalSession changedSession) {
        if (mSessionChangeCallback != null) mSessionChangeCallback.onTitleChanged(changedSession);
    }

    @Override
    public void onSessionFinished(final TerminalSession finishedSession) {
        if (mSessionChangeCallback != null)
            mSessionChangeCallback.onSessionFinished(finishedSession);
    }

    @Override
    public void onTextChanged(TerminalSession changedSession) {
        if (mSessionChangeCallback != null) mSessionChangeCallback.onTextChanged(changedSession);
    }

    @Override
    public void onClipboardText(TerminalSession session, String text) {
        if (mSessionChangeCallback != null) mSessionChangeCallback.onClipboardText(session, text);
    }

    @Override
    public void onBell(TerminalSession session) {
        if (mSessionChangeCallback != null) mSessionChangeCallback.onBell(session);
    }

    @Override
    public void onColorsChanged(TerminalSession session) {
        if (mSessionChangeCallback != null) mSessionChangeCallback.onColorsChanged(session);
    }
    
    private void setFolderPermissions(String folderPath) {
        File folder = new File(folderPath);
        folder.setReadable(true, false);
        folder.setWritable(true, false);
        folder.setExecutable(true, false);
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    setFolderPermissions(file.getAbsolutePath());
                } else {
                    file.setReadable(true, false);
                    file.setWritable(true, false);
                    file.setExecutable(true, false);
                }
            }
        }
    }
    
    private String[] buildEnvironment(boolean failSafe, String cwd) {
        if (cwd == null) cwd = HOME_PATH;

        final String termEnv = "TERM=xterm-256color";
        final String homeEnv = "HOME=" + HOME_PATH;
        final String androidRootEnv = "ANDROID_ROOT=" + System.getenv("ANDROID_ROOT");
        final String androidDataEnv = "ANDROID_DATA=" + System.getenv("ANDROID_DATA");
        // EXTERNAL_STORAGE is needed for /system/bin/am to work on at least
        // Samsung S7 - see https://plus.google.com/110070148244138185604/posts/gp8Lk3aCGp3.
        final String externalStorageEnv = "EXTERNAL_STORAGE=" + System.getenv("EXTERNAL_STORAGE");
        if (failSafe) {
            // Keep the default path so that system binaries can be used in the failsafe session.
            final String pathEnv = "PATH=" + System.getenv("PATH");
            return new String[]{termEnv, homeEnv, androidRootEnv, androidDataEnv, pathEnv, externalStorageEnv};
        } else {
            final String ps1Env = "PS1=$ ";
            final String langEnv = "LANG=en_US.UTF-8";
            final String pwdEnv = "PWD=" + cwd;

            return new String[]{termEnv, homeEnv, ps1Env, langEnv, pwdEnv, androidRootEnv, androidDataEnv, externalStorageEnv};
        }
    }

    private String[] setupProcessArgs(String fileToExecute, String[] args) {
        // The file to execute may either be:
        // - An elf file, in which we execute it directly.
        // - A script file without shebang, which we execute with our standard shell $PREFIX/bin/sh instead of the
        //   system /system/bin/sh. The system shell may vary and may not work at all due to LD_LIBRARY_PATH.
        // - A file with shebang, which we try to handle with e.g. /bin/foo -> $PREFIX/bin/foo.
        String interpreter = null;
        try {
            File file = new File(fileToExecute);
            FileInputStream in = new FileInputStream(file);
            try{
                byte[] buffer = new byte[256];
                int bytesRead = in.read(buffer);
                if (bytesRead > 4) {
                    if (buffer[0] == 0x7F && buffer[1] == 'E' && buffer[2] == 'L' && buffer[3] == 'F') {
                        // Elf file, do nothing.
                    } else if (buffer[0] == '#' && buffer[1] == '!') {
                        // Try to parse shebang.
                        StringBuilder builder = new StringBuilder();
                        for (int i = 2; i < bytesRead; i++) {
                            char c = (char) buffer[i];
                            if (c != ' ' || c != '\n') {
                                builder.append(c);
                            }
                        }
                    }
                }
            } catch (IOException e) {
            // Ignore.
            }
        } catch (IOException e) {
            // Ignore.
        }

        List<String> result = new ArrayList<>();
        if (interpreter != null) result.add(interpreter);
        result.add(fileToExecute);
        if (args != null) Collections.addAll(result, args);
        return result.toArray(new String[result.size()]);
    }
    
}
