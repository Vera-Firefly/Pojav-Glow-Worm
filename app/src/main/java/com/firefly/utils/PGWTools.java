package com.firefly.utils;

import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.GLES20;
import android.util.Log;

import com.movtery.feature.mod.parser.ModChecker;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import net.kdt.pojavlaunch.firefly.Logger;

import org.jetbrains.annotations.Nullable;

public class PGWTools {

    private static volatile boolean isCancelled = false;

    public static void initCancel() {
        isCancelled = false;
    }

    public static void onCancel() {
        isCancelled = true;
    }

    public static boolean onCancelled() {
        return isCancelled;
    }

    public static void onAppendToLog(String message) {
        Logger.appendToLog("==================== " + message + " ====================");
    }

    // Check for AdrenoGPU
    public static boolean isAdrenoGPU() {
        EGLDisplay eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
            Log.e("CheckVendor", "Failed to get EGL display");
            return false;
        }

        if (!EGL14.eglInitialize(eglDisplay, null, 0, null, 0)) {
            Log.e("CheckVendor", "Failed to initialize EGL");
            EGL14.eglTerminate(eglDisplay);
            return false;
        }

        int[] eglAttributes = new int[]{
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_NONE
        };

        EGLConfig[] configs = new EGLConfig[1];
        int[] numConfigs = new int[1];
        if (!EGL14.eglChooseConfig(eglDisplay, eglAttributes, 0, configs, 0, 1, numConfigs, 0) || numConfigs[0] == 0) {
            EGL14.eglTerminate(eglDisplay);
            Log.e("CheckVendor", "Failed to choose EGL config");
            return false;
        }

        int[] contextAttributes = new int[]{
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,  // OpenGL ES 2.0
            EGL14.EGL_NONE
        };

        EGLContext context = EGL14.eglCreateContext(eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT, contextAttributes, 0);
        if (context == EGL14.EGL_NO_CONTEXT) {
            EGL14.eglTerminate(eglDisplay);
            Log.e("CheckVendor", "Failed to create EGL context");
            return false;
        }

        if (!EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, context)) {
            EGL14.eglDestroyContext(eglDisplay, context);
            EGL14.eglTerminate(eglDisplay);
            Log.e("CheckVendor", "Failed to make EGL context current");
            return false;
        }

        String vendor = GLES20.glGetString(GLES20.GL_VENDOR);
        String renderer = GLES20.glGetString(GLES20.GL_RENDERER);
        boolean isAdreno = (vendor != null && renderer != null &&
                vendor.equalsIgnoreCase("Qualcomm") &&
                renderer.toLowerCase().contains("adreno"));

        // Cleanup
        EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
        EGL14.eglDestroyContext(eglDisplay, context);
        EGL14.eglTerminate(eglDisplay);

        Log.d("CheckVendor", "Running on Adreno GPU: " + isAdreno);
        return isAdreno;
    }

    // Check for binary executables
    public static boolean isELFFile(InputStream inputStream) {
        try {
            byte[] elfMagic = new byte[4];
            int bytesRead = inputStream.read(elfMagic);

            return bytesRead == 4 &&
                   elfMagic[0] == 0x7F &&
                   elfMagic[1] == 'E' &&
                   elfMagic[2] == 'L' &&
                   elfMagic[3] == 'F';
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // Generic file download method
    public static boolean downloadFile(String fileUrl, File targetFile) {
        try (InputStream inputStream = new URL(fileUrl).openStream();
             FileOutputStream fileOutputStream = new FileOutputStream(targetFile)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                if (isCancelled) {
                    cleanupFile(targetFile);
                    return false;
                }
                fileOutputStream.write(buffer, 0, bytesRead);
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            cleanupFile(targetFile);
            return false;
        }
    }

    // Unzip the file
    public static boolean unzipFile(File zipFile, File targetDir) {
        if (!zipFile.exists() || !zipFile.isFile()) {
            Log.e("PGWTools", "Invalid ZIP file: " + zipFile.getAbsolutePath());
            return false;
        }

        if (!targetDir.exists() && !targetDir.mkdirs()) {
            Log.e("PGWTools", "Failed to create extraction target directory: " + targetDir.getAbsolutePath());
            return false;
        }

        try (ZipInputStream zipInputStream = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                File outFile = new File(targetDir, entry.getName());
                String canonicalTargetPath = targetDir.getCanonicalPath();
                String canonicalOutPath = outFile.getCanonicalPath();

                if (!canonicalOutPath.startsWith(canonicalTargetPath + File.separator)) {
                    throw new IOException("Illegal Zip entry path: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    if (!outFile.mkdirs() && !outFile.isDirectory()) {
                        throw new IOException("Failed to create directory: " + outFile.getAbsolutePath());
                    }
                } else {
                    try (FileOutputStream fileOutputStream = new FileOutputStream(outFile)) {
                        byte[] buffer = new byte[4096];
                        int len;
                        while ((len = zipInputStream.read(buffer)) > 0) {
                            fileOutputStream.write(buffer, 0, len);
                        }
                    }
                }
                zipInputStream.closeEntry();
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        } finally {
            cleanupFile(zipFile);
        }
        return true;
    }

    // Check if a URL is valid
    public static boolean checkUrlAvailability(String urlString) {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            return connection.getResponseCode() >= 200 && connection.getResponseCode() < 400;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    // Clean file or directory
    public static void cleanupFile(File... files) {
        for (File file : files) {
            if (file != null && file.exists()) {
                deleteRecursively(file);
            }
        }
    }

    private static boolean deleteRecursively(File fileOrDir) {
        if (fileOrDir.isDirectory()) {
            File[] children = fileOrDir.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        return fileOrDir.delete();
    }

    public static class MG_GLVersionSetting {
        // value -> index
        public static int getIndexByValue(Map<String, Integer> glVersionMap, int value) {
            ArrayList<Integer> values = new ArrayList<>(glVersionMap.values());
            return values.indexOf(value);
        }

        // index -> value
        public static int getValueByIndex(Map<String, Integer> glVersionMap, int index) {
            ArrayList<Integer> values = new ArrayList<>(glVersionMap.values());
            if (index >= 0 && index < values.size()) {
                return values.get(index);
            }
            return -1; // 无效索引
        }

    }

}
