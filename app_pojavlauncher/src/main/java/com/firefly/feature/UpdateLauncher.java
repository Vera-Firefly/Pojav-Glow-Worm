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

public class UpdateLauncher {
    private static final String GITHUB_API = "https://api.github.com/repos/Vera-Firefly/Pojav-Glow-Worm/releases/latest";
    private static final String GITHUB_RELEASE_URL = "github.com/Vera-Firefly/Pojav-Glow-Worm/releases/download/%s/Pojav-Glow-Worm-%s-%s.apk";
    private Context context;
    private int localVersionCode;

    // 构造函数直接获取资源中的 base_version_code
    public UpdateLauncher(Context context) {
        this.context = context;
        // 从资源中获取 base_version_code（处理为 string 并转换为 int）
        try {
            String versionCodeString = context.getString(R.string.base_version_code);
            this.localVersionCode = Integer.parseInt(versionCodeString);
        } catch (NumberFormatException e) {
            e.printStackTrace();
            this.localVersionCode = 0; // 转换失败
        }
    }

    public void checkForUpdates() {
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
                    // 比较 GitHub 获取的版本号和本地版本号
                    if (remoteVersionCode > localVersionCode) {
                        showUpdateDialog(result);
                    } else {
                        Toast.makeText(context, "已经是最新版本", Toast.LENGTH_SHORT).show();
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

            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle("检测到更新" + versionName)
                    .setMessage(releaseNotes)
                    .setCancelable(true)
                    .setPositiveButton("更新", (dialog, id) -> showDownloadSourceDialog(tagName, versionName, archModel))
                    .setNegativeButton("忽略", (dialog, id) -> dialog.cancel())
                    .show();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void showDownloadSourceDialog(String tagName, String versionName, String archModel) {
        String[] downloadSources = {"GitHub", "GHPROXY"};
        String githubUrl = String.format(GITHUB_RELEASE_URL, tagName, versionName, archModel);

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("选择下载源")
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
                    startDownload(apkUrl);
                }
            })
            .setNegativeButton("取消", (dialog, id) -> dialog.cancel())
            .show();
    }

    private void startDownload(String apkUrl) {
        new DownloadApkTask().execute(apkUrl);
    }

    private class DownloadApkTask extends AsyncTask<String, Integer, File> {
        ProgressDialog progressDialog;

        @Override
        protected void onPreExecute() {
            progressDialog = new ProgressDialog(context);
            progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            progressDialog.setTitle("下载更新");
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
                    apkFile = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "cache.apk");
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
                installApk(apkFile);
            } else {
                Toast.makeText(context, "下载失败", Toast.LENGTH_SHORT).show();
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