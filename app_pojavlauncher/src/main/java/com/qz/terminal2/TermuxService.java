package com.qz.terminal2;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.util.Log;

import androidx.core.content.FileProvider;

import com.qz.utils.Utils;
import com.termux.terminal.EmulatorDebug;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSession.SessionChangedCallback;

import static net.kdt.pojavlaunch.R.string;
import net.kdt.pojavlaunch.JavaGUILauncherActivity;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class TermuxService extends Service implements SessionChangedCallback {

    private static final String ACTION_STOP_SERVICE = "com.termux.aservice_stop";
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
    
    private int port;

    @SuppressLint("Wakelock")
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Utils.setFolderPermissions(Tools.MULTIRT_HOME);

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
            startTerminal(intent);
        } else if (action != null) {
            Log.e(EmulatorDebug.LOG_TAG, "Unknown TermuxService action: '" + action + "'");
        }

        return Service.START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }
    
    
    public TerminalSession getTermSession() {
        return mTerminalSessions;
    }
    
    private File homeFile;
    private File cacheFile;
    private File binFile;
    private File libFile;
    private File localFile;
    
    private String javaHome;
    
    private ServerSocket serverSocket;
    private Socket clientSocket;
    
    @Override
    public void onCreate() {
        super.onCreate();
        homeFile = new File(Tools.DIR_DATA, "files");
        cacheFile = Tools.DIR_CACHE;
        binFile = new File(cacheFile, "bin");
        libFile = new File(cacheFile, "lib");
        localFile = new File(cacheFile, "local");
        
        javaHome = Tools.MULTIRT_HOME + "/" + LauncherPreferences.PREF_DEFAULT_RUNTIME;
        
        startSocket();
    }
    
    
    @Override
    public void onDestroy() {
        if (mWakeLock != null) mWakeLock.release();
        if (mWifiLock != null) mWifiLock.release();
        
        try {
            if (serverSocket != null) serverSocket.close();
            if (clientSocket != null) clientSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        mTerminalSessions.finishIfRunning();
    }

    TerminalSession createTermSession(String executablePath, String[] arguments, String cwd, boolean failSafe) {
        if (cwd == null)
            cwd = homeFile.getAbsolutePath();

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
    
    private void startSocket() {
        new Thread(() ->{
            try {
                serverSocket = new ServerSocket(0);
                port = serverSocket.getLocalPort();
                while(true) {
                	clientSocket = serverSocket.accept();
                    Log.d("TermuxServer", "Socket Running...");
                    BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                    StringBuilder cmd = new StringBuilder();
                    String inputLine;
                    while ((inputLine = in.readLine()) != null) {
                        cmd.append(inputLine);
                    }
                    Message message = new Message();
                    message.obj = cmd.toString();
                    handler.sendMessage(message);
                }
            } catch (IOException e) {
                    Log.e("TermuxService", e.getMessage());
            }
        }).start();
    }
    
    private Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message message) {
            super.handleMessage(message);
            execCommand((String)message.obj);
        }
    };
    
    private void execCommand(String cmd) {
        String[] args = cmd.split(" ");
        if (args.length > 0) {
            Intent intent  = new Intent(this, JavaGUILauncherActivity.class);
            Bundle bundle = new Bundle();
            StringBuilder javaArgs = new StringBuilder();
            String type = args[0];
            if (type.equals("jar")) {
                for (int i = 0; i < args.length; i++) {
                    javaArgs.append(args[i]).append(" ");
                }
                bundle.putString("javaArgs", javaArgs.toString());
            } else {
                File jarFile = new File(type);
                if (jarFile.exists()) {
                    Uri modUri = FileProvider.getUriForFile(this, getString(string.fileProviderAuthorities), jarFile);
                    bundle.putParcelable("modUri", modUri);
                }
            }
            intent.putExtras(bundle);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }
    }

    private void startTerminal(Intent intent) {
        CountDownLatch latch = new CountDownLatch(1);
        
        if (!binFile.exists() || binFile.length() == 0 || 
            !libFile.exists() || libFile.length() == 0) {
            
            File boot = new File(Tools.NATIVE_LIB_DIR, "libterm-boot.so");
            ExecutorService executorService = Executors.newSingleThreadExecutor();
            executorService.execute(() -> {
                try {
                    Utils.unzip(boot.getAbsolutePath(), cacheFile.getAbsolutePath());
                    Utils.unZipFromAssets(getApplicationContext(), "components/term/term.zip", homeFile.getAbsolutePath());
                } catch (IOException ig) {
                    ig.printStackTrace();
                } finally {
                    latch.countDown();
                }
             });
             executorService.shutdown();
        } else {
            latch.countDown();
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        File term = new File(binFile, "tcsh");
        String executablePath = term.getAbsolutePath();

        String[] arguments = {};

        String cwd = intent.getStringExtra(EXTRA_CURRENT_WORKING_DIRECTORY);
        mTerminalSessions = createTermSession(executablePath, arguments, cwd, true);

        if (executablePath != null) {
            int lastSlash = executablePath.lastIndexOf('/');
            String name = (lastSlash == -1) ? executablePath : executablePath.substring(lastSlash + 1);
            name = name.replace('-', ' ');
            mTerminalSessions.mSessionName = name;
        }
    }

    private String[] buildEnvironment(boolean failSafe, String cwd) {
        if (cwd == null) cwd = homeFile.getAbsolutePath();

        final String termEnv = "TERM=xterm-256color";
        final String homeEnv = "HOME=" + homeFile;
        final String androidRootEnv = "ANDROID_ROOT=" + System.getenv("ANDROID_ROOT");
        final String androidDataEnv = "ANDROID_DATA=" + System.getenv("ANDROID_DATA");
        // EXTERNAL_STORAGE is needed for /system/bin/am to work on at least
        // Samsung S7 - see https://plus.google.com/110070148244138185604/posts/gp8Lk3aCGp3.
        final String externalStorageEnv = "EXTERNAL_STORAGE=" + System.getenv("EXTERNAL_STORAGE");
        final String serverJars = "STANDALONE_SYSTEMSERVER_JARS=" + System.getenv("STANDALONE_SYSTEMSERVER_JARS");
        final String dex2oatClassPath = "DEX2OATBOOTCLASSPATH=" + System.getenv("DEX2OATBOOTCLASSPATH");
        final String bootClassPath = "BOOTCLASSPATH=" + System.getenv("BOOTCLASSPATH");
        final String serverClassPath = "SYSTEMSERVERCLASSPATH=" + System.getenv("SYSTEMSERVERCLASSPATH");
        final String packageName = "PACKAGE_NAME=" + getPackageName();
        final String localPort = "LOCAL_PORT=" + port;
        final String javaHomeEnv = "JAVA_HOME=" +  javaHome;
        final String binPath = "PATH=" + System.getenv("PATH") + ":" + binFile.getAbsolutePath() + ":" + javaHome + "/bin:" +
                                LocalEnv.getGccBin(localFile.getAbsolutePath());
        
        final String libPath = "LD_LIBRARY_PATH=" + libFile.getAbsolutePath() + ":" + 
                                LocalEnv.getJavaLibEnv(javaHome);
        if (failSafe) {
            // Keep the default path so that system binaries can be used in the failsafe session.
            final String pathEnv = "PATH=" + System.getenv("PATH");
            return new String[]{
                termEnv, homeEnv, 
                androidRootEnv, androidDataEnv, 
                pathEnv, externalStorageEnv,
                serverJars, dex2oatClassPath, 
                bootClassPath, serverClassPath,
                packageName, localPort,
                javaHomeEnv,
                binPath, libPath
            };
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
            try {
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
