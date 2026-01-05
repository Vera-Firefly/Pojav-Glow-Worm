package com.firefly.feature;

import android.content.Context;
import android.os.Build;
import android.os.Environment;

import com.firefly.utils.PGWTools;
import com.firefly.utils.MesaUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class MesaDownloader {
    private static final String BASE_URL = "github.com/Vera-Firefly/android-mesa-build/releases/download";
    private static final String VERSION_JSON_PATH = "/100000/version.json";
    private static final String DOWNLOAD_URL_TEMPLATE = "%s/%s/%s.zip";

    private static String DLS = "https://";
    private static File downloadDir;
    private static final Map<String, String> versionNameMap = new HashMap<>();
    private static final Map<String, String> mesaNameMap = new HashMap<>();
    private static Context appContext;

    // Initialize the downloader
    public static void initialize(Context context) {
        appContext = context.getApplicationContext();
        initDownloadDir();
    }

    // Initialize the download directory
    private static void initDownloadDir() {
        if (downloadDir == null) {
            downloadDir = new File(appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "Mesa");
            if (!downloadDir.exists() && !downloadDir.mkdirs()) {
                throw new IllegalStateException("Unable to create download directory: " + downloadDir.getAbsolutePath());
            }
        }
    }

    public static void cancelDownload() {
        PGWTools.onCancel();
    }

    public static boolean isDownloadCancelled() {
        return PGWTools.onCancelled();
    }

    // Get the list of Mesa versions
    public static List<String> getMesaList(int sourceType) {
        PGWTools.initCancel();
        String versionUrl = resolveVersionUrl(sourceType);

        if (versionUrl == null) {
            System.err.println("No valid version URL found.");
            return null;
        }

        File versionFile = getDownloadSubDir("version.json");
        if (!PGWTools.downloadFile(versionUrl, versionFile)) {
            return null;
        }

        return parseVersionFile(versionFile);
    }

    // Parse the version file
    private static List<String> parseVersionFile(File versionFile) {
        List<String> mesaVersions = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(versionFile))) {
            StringBuilder jsonBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonBuilder.append(line);
            }

            JSONObject jsonObject = new JSONObject(jsonBuilder.toString());
            JSONArray versionsArray = jsonObject.getJSONArray("versions");

            for (int i = 0; i < versionsArray.length(); i++) {
                JSONObject versionObject = versionsArray.getJSONObject(i);
                String version = versionObject.getString("version");
                String tag = versionObject.getString("tag");
                String fileName = versionObject.getString("fileName");

                versionNameMap.put(version, tag);
                mesaNameMap.put(tag, fileName);
                mesaVersions.add(version);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            PGWTools.cleanupFile(versionFile);
        }
        return mesaVersions;
    }

    // Download and extract the specified version
    public static boolean downloadMesaFile(String version) {
        String tag = versionNameMap.get(version);
        if (tag == null) {
            System.err.println("No tag found for version: " + version);
            return false;
        }

        String fileUrl = resolveDownloadUrl(tag, version + getArch());
        if (fileUrl == null) {
            System.err.println("No valid download URL found for version: " + version);
            return false;
        }

        File zipFile = getDownloadSubDir(version + getArch() + ".zip");
        if (!PGWTools.downloadFile(fileUrl, zipFile)) {
            return false;
        }

        File extractDir = getDownloadSubDir(version + getArch());
        if (!PGWTools.unzipFile(zipFile, extractDir)) {
            System.err.println("Failed to extract the file: " + zipFile.getAbsolutePath());
            return false;
        }

        return true;
    }

    // Save the extracted file
    public static boolean saveMesaFile(String version) {
        String tag = versionNameMap.get(version);
        String fileName = mesaNameMap.get(tag);
        if (tag == null || fileName == null) {
            System.err.println("Invalid version or file name.");
            return false;
        }

        File sourceFile = getDownloadSubDir(version + getArch() + "/" + fileName);
        return copyFileToMesaDir(sourceFile, version);
    }

    // Resolve the URL to fetch the version.json file
    private static String resolveVersionUrl(int sourceType) {
        String[] sources = {
            "https://",
            "https://mirror.ghproxy.com/"
        };
        if (sourceType > 0 && sourceType <= sources.length) {
            DLS = sources[sourceType - 1];
            return DLS + BASE_URL + VERSION_JSON_PATH;
        }
        for (String source : sources) {
            String testUrl = source + BASE_URL + VERSION_JSON_PATH;
            if (PGWTools.checkUrlAvailability(testUrl)) {
                DLS = source;
                return testUrl;
            }
        }
        return null;
    }

    // Resolve the download URL for the given version and tag
    private static String resolveDownloadUrl(String tag, String version) {
        String baseUrl = DLS + BASE_URL;
        String testUrl = String.format(DOWNLOAD_URL_TEMPLATE, baseUrl, tag, version);
        if (PGWTools.checkUrlAvailability(testUrl)) {
            return testUrl;
        }
        return null;
    }

    // Copy file to the target directory
    private static boolean copyFileToMesaDir(File sourceFile, String folderName) {
        File targetDir = new File(MesaUtils.INSTANCE.getMesaDir(), folderName);
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            return false;
        }
        File targetFile = new File(targetDir, "libOSMesa_8.so");
        try (InputStream inputStream = new FileInputStream(sourceFile);
             OutputStream outputStream = new FileOutputStream(targetFile)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        } finally {
            File sourceFiles = getDownloadSubDir(folderName + getArch());
            PGWTools.cleanupFile(sourceFiles);
        }
        return true;
    }

    // Helper methods
    private static File getDownloadSubDir(String subPath) {
        return new File(downloadDir, subPath);
    }

    private static String getArch() {
        String abi = Build.SUPPORTED_ABIS[0];
        if (abi.equals("arm64-v8a")) {
            return "-aarch64";
        } else if (abi.equals("armeabi-v7a")) {
            return "-arm32";
        } else {
            return "-x86_64";
        }
    }

}