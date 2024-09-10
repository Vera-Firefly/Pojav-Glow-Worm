package com.firefly.feature;

import static net.kdt.pojavlaunch.Architecture.*;

import androidx.core.content.FileProvider;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.widget.Toast;

import com.firefly.ui.dialog.CustomDialog;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class UpdateLauncher {
    private static final String GITHUB_API = "https://api.github.com/repos/Vera-Firefly/Pojav-Glow-Worm/releases/latest";
    private static final String GITHUB_RELEASE_URL = "github.com/Vera-Firefly/Pojav-Glow-Worm/releases/download/%s/Pojav-Glow-Worm-%s-%s.apk";
    private Context context;
    private int localVersionCode;

    public UpdateLauncher(Context context) {
        this.context = context;
        try {
            String versionCodeString = context.getString(R.string.base_version_code);
            this.localVersionCode = Integer.parseInt(versionCodeString);
        } catch (NumberFormatException e) {
            e.printStackTrace();
            this.localVersionCode = 0;
        }
    }

    public void checkCachedApk() {
        File dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        File apkFile = new File(dir, "cache.apk");
        File apkVersionFile = new File(dir, "apk_version");

        if (apkFile.exists() && apkVersionFile.exists()) {
            try {
                String savedTagName = new String(java.nio.file.Files.readAllBytes(apkVersionFile.toPath()));
                int savedVersionCode = Integer.parseInt(savedTagName.replaceAll("[^\\d]", ""));
                if (savedVersionCode > localVersionCode) {
                    new CustomDialog.Builder(context)
                        .setTitle(context.getString(R.string.pgw_settings_updatelauncher_install_prompt_title))
                        .setMessage(context.getString(R.string.pgw_settings_updatelauncher_install_prompt_message, apkFile.getAbsolutePath()))
                        .setConfirmListener(R.string.pgw_settings_updatelauncher_install, customView -> {
                            installApk(apkFile);
                            return true;
                        })
                        .setCancelListener(R.string.alertdialog_cancel, customView -> true)
                        .setCancelable(false)
                        .build()
                        .show();
                } else {
                    apkFile.delete();
                    apkVersionFile.delete();
                    checkForUpdates();
                }
            } catch (IOException | NumberFormatException e) {
                e.printStackTrace();
            }
        } else if (apkFile.exists() || apkVersionFile.exists()) {
            if (apkFile.exists()) apkFile.delete();
            if (apkVersionFile.exists()) apkVersionFile.delete();
            checkForUpdates();
        } else {
            checkForUpdates();
        }
    }

    private void checkForUpdates() {
        new GetLatestReleaseTask().execute(GITHUB_API);
    }

    private class GetLatestReleaseTask extends AsyncTask<String, Void, JSONObject> {
        @Override
        protected JSONObject doInBackground(String... urls) {
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder().url(urls[0]).build();
            try {
                Response response = client.newCall(request).execute();
                if (response.isSuccessful()) {
                    return new JSONObject(response.body().string());
                }
            } catch (IOException | JSONException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(JSONObject result) {
            if (result != null) {
                try {
                    int remoteVersionCode = Integer.parseInt(result.getString("tag_name").replaceAll("[^\\d]", ""));
                    String version = String.valueOf(localVersionCode);
                    if (remoteVersionCode > localVersionCode) {
                        showUpdateDialog(result);
                    } else {
                        Toast.makeText(context, context.getString(R.string.pgw_settings_updatelauncher_updated, version), Toast.LENGTH_SHORT).show();
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void showUpdateDialog(JSONObject releaseInfo) {
        try {
            String tagName = releaseInfo.getString("tag_name");
            String versionName = releaseInfo.getString("name");
            String releaseNotes = releaseInfo.getString("body");
            String archModel = getArchModel();

            CustomDialog.Builder builder = new CustomDialog.Builder(context);
            builder.setTitle(context.getString(R.string.pgw_settings_updatelauncher_new_version, versionName))
                .setMessage(releaseNotes)
                .setConfirmListener(R.string.pgw_settings_updatelauncher_update, customView -> {
                    showDownloadSourceDialog(tagName, versionName, archModel);
                    return true;
                })
                .setButton1Listener(context.getString(R.string.pgw_settings_updatelauncher_cancel), customView -> true)
                .setCancelListener(R.string.alertdialog_cancel, customView -> true)
                .build()
                .show();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void showDownloadSourceDialog(String tagName, String versionName, String archModel) {
        String[] downloadSources = {"GitHub", "GHPROXY"};
        String githubUrl = String.format(GITHUB_RELEASE_URL, tagName, versionName, archModel);

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.pgw_settings_updatelauncher_source)
            .setCancelable(false)
            .setSingleChoiceItems(downloadSources, -1, (dialog, which) -> {
                String selectedSource = downloadSources[which];
                String apkUrl;
                switch (selectedSource) {
                    case "GitHub":
                        apkUrl = "https://" + githubUrl;
                        break;
                    case "GHPROXY":
                        apkUrl = "https://mirror.ghproxy.com/" + githubUrl;
                        break;
                    default:
                        apkUrl = null;
                }

                if (apkUrl != null) {
                    dialog.dismiss();
                    startDownload(apkUrl, tagName);
                }
            })
            .setNegativeButton(R.string.alertdialog_cancel, (dialog, id) -> dialog.cancel())
            .show();
    }

    private void startDownload(String apkUrl, String tagName) {
        new DownloadApkTask(tagName).execute(apkUrl);
    }

    private class DownloadApkTask extends AsyncTask<String, Integer, File> {
        private String tagName;
        ProgressDialog progressDialog;

        public DownloadApkTask(String tagName) {
            this.tagName = tagName;
        }

        @Override
        protected void onPreExecute() {
            progressDialog = new ProgressDialog(context);
            progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            progressDialog.setTitle(R.string.pgw_settings_updatelauncher_downloading);
            progressDialog.setCancelable(false);
            progressDialog.show();
        }

        @Override
        protected File doInBackground(String... urls) {
            String apkUrl = urls[0];
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder().url(apkUrl).build();
            File apkFile = null;

            try {
                Response response = client.newCall(request).execute();
                if (response.isSuccessful()) {
                    File dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                    apkFile = new File(dir, "cache.apk");
                    File apkVersionFile = new File(dir, "apk_version");

                    InputStream inputStream = response.body().byteStream();
                    FileOutputStream outputStream = new FileOutputStream(apkFile);
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    long totalBytesRead = 0;
                    long totalBytes = response.body().contentLength();

                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        totalBytesRead += bytesRead;
                        outputStream.write(buffer, 0, bytesRead);
                        publishProgress((int) ((totalBytesRead * 100) / totalBytes));
                    }

                    FileOutputStream versionOutputStream = new FileOutputStream(apkVersionFile);
                    versionOutputStream.write(tagName.getBytes());
                    versionOutputStream.flush();
                    versionOutputStream.close();

                    outputStream.flush();
                    outputStream.close();
                    inputStream.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            return apkFile;
        }

        @Override
        protected void onProgressUpdate(Integer... progress) {
            progressDialog.setProgress(progress[0]);
        }

        @Override
        protected void onPostExecute(File apkFile) {
            progressDialog.dismiss();
            if (apkFile != null) {
                new CustomDialog.Builder(context)
                    .setTitle(context.getString(R.string.pgw_settings_updatelauncher_download_complete))
                    .setMessage(context.getString(R.string.pgw_settings_updatelauncher_file_location, apkFile.getAbsolutePath()))
                    .setConfirmListener(R.string.pgw_settings_updatelauncher_install, customView -> {
                        installApk(apkFile);
                        return true;
                    })
                    .setCancelListener(R.string.alertdialog_cancel, customView -> true)
                    .setCancelable(false)
                    .build()
                    .show();
            } else {
                Toast.makeText(context, context.getString(R.string.pgw_settings_updatelauncher_download_fail), Toast.LENGTH_SHORT).show();
            }
        }
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
        if(arch == ARCH_ARM64) return "arm64-v8a";
        if(arch == ARCH_ARM) return "armeabi-v7a";
        if(arch == ARCH_X86_64) return "x86_64";
        if(arch == ARCH_X86) return "x86";
        return "all";
    }
}