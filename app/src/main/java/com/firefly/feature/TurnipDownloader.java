package com.firefly.feature;

import android.content.Context;
import android.os.Environment;

import com.firefly.utils.PGWTools;
import com.firefly.utils.TurnipUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class TurnipDownloader {
    private static final String BASE_URL = "github.com/Vera-Firefly/TurnipDriver-CI/releases/download";
    private static final String FALLBACK_BASE_URL = "github.com/K11MCH1/AdrenoToolsDrivers/releases/download";
    private static final String VERSION_JSON_PATH = "/100000/version.json";
    private static final String DOWNLOAD_URL_TEMPLATE = "%s/%s/%s.zip";

    private static String DLS = "https://";
    private static File downloadDir;
    private static final Map<String, String> versionNameMap = new HashMap<>();
    private static final Map<String, String> turnipNameMap = new HashMap<>();
    private static Context appContext;

    // Initialize the downloader
    public static void initialize(Context context) {
        appContext = context.getApplicationContext();
        initDownloadDir();
    }

    // Initialize the download directory
    private static void initDownloadDir() {
        if (downloadDir == null) {
            downloadDir = new File(appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "Turnip");
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

    // Get the list of Turnip versions
    public static List<String> getTurnipList(int sourceType) {
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
        List<String> turnipVersions = new ArrayList<>();
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
                turnipNameMap.put(tag, fileName);
                turnipVersions.add(version);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            PGWTools.cleanupFile(versionFile);
        }
        return turnipVersions;
    }

    // Download and extract the specified version
    public static boolean downloadTurnipFile(String version) {
        String tag = versionNameMap.get(version);
        if (tag == null) {
            System.err.println("No tag found for version: " + version);
            return false;
        }

        String fileUrl = resolveDownloadUrl(tag, version);
        if (fileUrl == null) {
            System.err.println("No valid download URL found for version: " + version);
            return false;
        }

        File zipFile = getDownloadSubDir(version + ".zip");
        if (!PGWTools.downloadFile(fileUrl, zipFile)) {
            return false;
        }

        File extractDir = getDownloadSubDir(version);
        if (!PGWTools.unzipFile(zipFile, extractDir)) {
            System.err.println("Failed to extract the file: " + zipFile.getAbsolutePath());
            return false;
        }

        return true;
    }

    // Save the extracted file
    public static boolean saveTurnipFile(String version) {
        String tag = versionNameMap.get(version);
        String fileName = turnipNameMap.get(tag);
        if (tag == null || fileName == null) {
            System.err.println("Invalid version or file name.");
            return false;
        }

        File sourceFile = getDownloadSubDir(version + "/" + fileName);
        return copyFileToTurnipDir(sourceFile, version);
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
        String[] baseUrls = {
            DLS + BASE_URL,
            DLS + FALLBACK_BASE_URL
        };
        for (String baseUrl : baseUrls) {
            String testUrl = String.format(DOWNLOAD_URL_TEMPLATE, baseUrl, tag, version);
            if (PGWTools.checkUrlAvailability(testUrl)) {
                return testUrl;
            }
        }
        return null;
    }

    // Copy file to the target directory
    private static boolean copyFileToTurnipDir(File sourceFile, String folderName) {
        File targetDir = new File(TurnipUtils.INSTANCE.getTurnipDir(), folderName);
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            return false;
        }
        File targetFile = new File(targetDir, "libvulkan_freedreno.so");
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
            File sourceFiles = getDownloadSubDir(folderName);
            PGWTools.cleanupFile(sourceFiles);
        }
        return true;
    }

    // Helper methods
    private static File getDownloadSubDir(String subPath) {
        return new File(downloadDir, subPath);
    }

}