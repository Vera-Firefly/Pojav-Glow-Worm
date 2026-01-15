package com.firefly.feature;

import static net.kdt.pojavlaunch.firefly.Architecture.ARCH_ARM;
import static net.kdt.pojavlaunch.firefly.Architecture.ARCH_ARM64;
import static net.kdt.pojavlaunch.firefly.Architecture.ARCH_X86;
import static net.kdt.pojavlaunch.firefly.Architecture.ARCH_X86_64;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.firefly.ui.dialog.CustomDialog;
import com.firefly.utils.PGWTools;

import net.kdt.pojavlaunch.firefly.R;
import net.kdt.pojavlaunch.firefly.Tools;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;	
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class UpdateLauncher {

    private static final String GITHUB_API = "https://api.github.com/repos/Vera-Firefly/Pojav-Glow-Worm/releases/latest";
    private static final String GITHUB_RELEASE_URL = "github.com/Vera-Firefly/Pojav-Glow-Worm/releases/download/%s/Pojav-Glow-Worm-%s-%s.apk";
    private static final String CACHE_APK_NAME = "cache.apk";
    private static final String APK_VERSION_FILE_NAME = "apk_version";
    private static final String IGNORE_VERSION_FILE_NAME = "ignore_version";

    private final Context context;
    private final File dir;
    private final int localVersionCode;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean isCancelled = false;

    public UpdateLauncher(Context context) {
        this.context = context;
        this.dir = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "Launcher");
        this.localVersionCode = getLocalVersionCode();
    }

    public void checkForUpdates(boolean ignore) {
        if (!dir.exists() && !dir.mkdirs()) {
            handleException(new IOException("Unable to create directory: " + dir.getAbsolutePath()));
            return;
        }

        executor.execute(() -> {
            JSONObject releaseInfo = fetchReleaseInfo();
            if (releaseInfo != null) handleUpdateCheck(releaseInfo, ignore);
        });
    }

    private int getLocalVersionCode() {
        try {
            return Integer.parseInt(context.getString(R.string.base_version_code));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private JSONObject fetchReleaseInfo() {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(GITHUB_API).build();
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                return new JSONObject(response.body().string());
            }
        } catch (IOException | JSONException e) {
            handleException(e);
        }
        return null;
    }

    private void handleUpdateCheck(JSONObject releaseInfo, boolean ignore) {
        try {
            int remoteVersionCode = Integer.parseInt(releaseInfo.getString("tag_name").replaceAll("[^\\d]", ""));
            if (remoteVersionCode > localVersionCode) {
                handleCachedApk(releaseInfo, ignore, false);
            } else {
                if (!ignore) showToast(R.string.pgw_settings_updatelauncher_updated, String.valueOf(localVersionCode));
                handleCachedApk(releaseInfo, ignore, true);
            }
        } catch (IOException | JSONException e) {
            handleException(e);
        }
    }

    private void handleCachedApk(JSONObject releaseInfo, boolean ignore, boolean check) throws JSONException, IOException {
        String tagName = releaseInfo.getString("tag_name");
        String versionName = releaseInfo.getString("name");
        String releaseNotes = releaseInfo.getString("body");
        File apkFile = new File(dir, CACHE_APK_NAME);
        File apkVersionFile = new File(dir, APK_VERSION_FILE_NAME);
        File ignoreVersionFile = new File(dir, IGNORE_VERSION_FILE_NAME);

        if (ignoreVersionFile.exists() && shouldIgnoreVersion(ignoreVersionFile, tagName) && ignore && !check) return;

        if (apkFile.exists() && apkVersionFile.exists() && cachedVersionIsValid(apkVersionFile, tagName) && !check) {
            new Handler(Looper.getMainLooper()).post(() -> showInstallDialog(apkFile));
        } else {
            deleteFileIfExists(apkFile);
            deleteFileIfExists(apkVersionFile);
            if (!check) new Handler(Looper.getMainLooper()).post(() -> showUpdateDialog(tagName, versionName, releaseNotes));
        }
    }

    private boolean shouldIgnoreVersion(File ignoreVersionFile, String tagName) throws IOException {
        String savedIgnoreVersion = Tools.read(ignoreVersionFile);
        int savedIgnoreVersionCode = Integer.parseInt(savedIgnoreVersion.replaceAll("[^\\d]", ""));
        int releaseVersionCode = Integer.parseInt(tagName.replaceAll("[^\\d]", ""));
        if (savedIgnoreVersionCode < releaseVersionCode) deleteFileIfExists(ignoreVersionFile);
        return savedIgnoreVersionCode >= releaseVersionCode;
    }

    private boolean cachedVersionIsValid(File apkVersionFile, String tagName) throws IOException {
        String savedTagName = Tools.read(apkVersionFile);
        int savedVersionCode = Integer.parseInt(savedTagName.replaceAll("[^\\d]", ""));
        int releaseVersionCode = Integer.parseInt(tagName.replaceAll("[^\\d]", ""));
        return savedVersionCode >= releaseVersionCode;
    }

    private void showUpdateDialog(String tagName, String versionName, String releaseNotes) {
        String archModel = getArchModel();
        String processedNotes = extractReleaseNotesByLocale(releaseNotes);

        new CustomDialog.Builder(context)
            .setTitle(context.getString(R.string.pgw_settings_updatelauncher_new_version, versionName))
            .setScrollMessage(processedNotes)
            .setConfirmListener(R.string.pgw_settings_updatelauncher_update, customView -> {
                showDownloadSourceDialog(tagName, versionName, archModel);
                return true;
            })
            .setButton1Listener(context.getString(R.string.pgw_settings_updatelauncher_cancel), customView -> {
                saveIgnoreVersion(tagName);
                return true;
            })
            .setCancelListener(R.string.alertdialog_cancel, customView -> true)
            .build()
            .show();
    }

    private String extractReleaseNotesByLocale(String releaseNotes) {
        // 获取当前系统语言
        String systemLang = Locale.getDefault().getLanguage();
        String systemCountry = Locale.getDefault().getCountry();

        // 检查是否为中文（中国）
        boolean isChineseCN = "zh".equals(systemLang) && "CN".equals(systemCountry);

        // 判断使用哪种语言的内容
        String targetTag = isChineseCN ? "[CN]" : "[EN]";
        String endTag = isChineseCN ? "[CN]" : "[EN]";

        // 尝试提取目标语言的内容
        String extractedContent = extractContentByTags(releaseNotes, targetTag, endTag);

        // 如果提取失败或内容为空，尝试另一种语言作为备选
        if (extractedContent == null || extractedContent.trim().isEmpty()) {
            String fallbackTag = isChineseCN ? "[EN]" : "[CN]";
            String fallbackEndTag = isChineseCN ? "[EN]" : "[CN]";
            extractedContent = extractContentByTags(releaseNotes, fallbackTag, fallbackEndTag);
        }

        // 如果都没有提取到，返回原始内容
        return extractedContent != null ? extractedContent : releaseNotes;
    }

    /**
     * 根据标签提取内容
     */
    private String extractContentByTags(String content, String startTag, String endTag) {
        if (content == null || content.isEmpty()) {
            return null;
        }

        // 查找第一个开始标签的位置
        int startIndex = content.indexOf(startTag);
        if (startIndex == -1) {
            return null;
        }

        // 查找开始标签后的第一个结束标签
        int endIndex = content.indexOf(endTag, startIndex + startTag.length());
        if (endIndex == -1) {
            return null;
        }

        // 提取开始标签和结束标签之间的内容
        String extracted = content.substring(startIndex + startTag.length(), endIndex).trim();

        return extracted;
    }

    private void showDownloadSourceDialog(String tagName, String versionName, String archModel) {
        String githubUrl = String.format(GITHUB_RELEASE_URL, tagName, versionName, archModel);

        new CustomDialog.Builder(context)
            .setTitle(context.getString(R.string.pgw_settings_updatelauncher_source))
            .setCancelable(false)
            .setItems(new String[]{"GitHub", "GHPROXY"}, (selectedSource, i) -> {
                String apkUrl = selectedSource.equals("GitHub") ?
                        "https://" + githubUrl :
                        "https://mirror.ghproxy.com/" + githubUrl;
                startDownload(apkUrl, tagName);
                isCancelled = false;
            })
            .setConfirmListener(R.string.alertdialog_cancel, customView -> true)
            .build()
            .show();
    }

    private void startDownload(String apkUrl, String tagName) {
        ProgressDialog progressDialog = new ProgressDialog(context);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setTitle(R.string.pgw_settings_updatelauncher_downloading);
        progressDialog.setCancelable(false);
        progressDialog.setIndeterminate(false);
        progressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, context.getString(android.R.string.cancel), (dialog, which) -> {
            isCancelled = true;
        });
        progressDialog.setProgress(0);
        progressDialog.show();

        executor.execute(() -> downloadApk(apkUrl, tagName, progressDialog));
    }

    private void downloadApk(String apkUrl, String tagName, ProgressDialog progressDialog) {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(apkUrl).build();
        File apkFile = new File(dir, CACHE_APK_NAME);
        File apkVersionFile = new File(dir, APK_VERSION_FILE_NAME);

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                long totalBytes = response.body().contentLength();
                byte[] buffer = new byte[8192];
                long downloadedBytes = 0;

                try (InputStream inputStream = response.body().byteStream();
                    FileOutputStream outputStream = new FileOutputStream(apkFile)) {

                    int read;
                    while ((read = inputStream.read(buffer)) != -1) {
                        if (isCancelled) {
                            outputStream.close();
                            apkFile.delete();
                            return;
                        }

                        outputStream.write(buffer, 0, read);
                        downloadedBytes += read;

                        int progress = (int) (100 * downloadedBytes / totalBytes);
                        updateProgressDialog(progressDialog, progress);
                    }
                }

                Tools.write(apkVersionFile, tagName);
                new Handler(Looper.getMainLooper()).post(() -> showDownloadCompleteDialog(apkFile));
            } else {
                showToast(R.string.pgw_settings_updatelauncher_download_fail);
            }
        } catch (IOException e) {
            handleException(e);
        } finally {
            new Handler(Looper.getMainLooper()).post(progressDialog::dismiss);
        }
    }

    private void updateProgressDialog(ProgressDialog progressDialog, int progress) {
        new Handler(Looper.getMainLooper()).post(() -> {
            progressDialog.setProgress(progress);
        });
    }

    private void showDownloadCompleteDialog(File apkFile) {
        new CustomDialog.Builder(context)
            .setTitle(context.getString(R.string.pgw_settings_updatelauncher_download_complete))
            .setMessage(context.getString(R.string.pgw_settings_updatelauncher_file_location, apkFile.getAbsolutePath()))
            .setConfirmListener(R.string.pgw_settings_updatelauncher_install, customView -> {
                installApk(apkFile);
                return true;
            })
            .setButton1Listener(context.getString(R.string.pgw_settings_updatelauncher_re_download), costomView -> {
                reDownloadApk();
                return true;
            })
            .setCancelListener(R.string.alertdialog_cancel, customView -> true)
            .setCancelable(false)
            .build()
            .show();
    }

    private void installApk(File apkFile) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        Uri apkUri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", apkFile);
        intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        context.startActivity(intent);
    }

    private String getArchModel() {
        int arch = Tools.DEVICE_ARCHITECTURE;
        if (arch == ARCH_ARM64) return "arm64-v8a";
        if (arch == ARCH_ARM) return "armeabi-v7a";
        if (arch == ARCH_X86_64) return "x86_64";
        if (arch == ARCH_X86) return "x86";
        return "all";
    }

    private void saveIgnoreVersion(String tagName) {
        File ignoreVersionFile = new File(dir, IGNORE_VERSION_FILE_NAME);
        try {
            Tools.write(ignoreVersionFile, tagName);
        } catch (IOException e) {
            handleException(e);
        }
    }

    private void handleException(Exception e) {
        new Handler(Looper.getMainLooper()).post(() ->
            Toast.makeText(context, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    private void showToast(int messageResId) {
        new Handler(Looper.getMainLooper()).post(() ->
            Toast.makeText(context, context.getString(messageResId), Toast.LENGTH_SHORT).show());
    }

    private void showToast(int messageResId, Object... formatArgs) {
        new Handler(Looper.getMainLooper()).post(() -> {
            String message = context.getString(messageResId, formatArgs);
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        });
    }

    private void deleteFileIfExists(File file) {
        if (file.exists()) file.delete();
    }

    private void showInstallDialog(File apkFile) {
        new CustomDialog.Builder(context)
            .setTitle(context.getString(R.string.pgw_settings_updatelauncher_install_prompt_title))
            .setMessage(context.getString(R.string.pgw_settings_updatelauncher_install_prompt_message, apkFile.getAbsolutePath()))
            .setConfirmListener(R.string.pgw_settings_updatelauncher_install, customView -> {
                installApk(apkFile);
                return true;
            })
            .setButton1Listener(context.getString(R.string.pgw_settings_updatelauncher_re_download), costomView -> {
                reDownloadApk();
                return true;
            })
            .setCancelListener(R.string.alertdialog_cancel, customView -> true)
            .setCancelable(false)
            .build()
            .show();
    }

    private void reDownloadApk() {
        PGWTools.cleanupFile(dir);
        checkForUpdates(true);
    }

}