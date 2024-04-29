package com.qz.utils;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

public class Utils {
    private static final String TAG = Utils.class.getName();

    public static void unzip(String zipFilePath, String destDirPath) throws IOException {
        File destDir = new File(destDirPath);
        if (!destDir.exists()) {
            destDir.mkdir();
        }
        try (ZipFile zipFile = new ZipFile(zipFilePath)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String filePath = destDirPath + File.separator + entry.getName();
                if (!entry.isDirectory()) {
                    // 如果是文件，提取文件
                    extractFile(zipFile, entry, filePath);
                } else {
                    // 如果是目录，创建目录
                    File dir = new File(filePath);
                    dir.mkdir();
                }
            }
        }
        setFolderPermissions(destDirPath);
    }

    private static void extractFile(ZipFile zipFile, ZipEntry zipEntry, String filePath) throws IOException {
        if (zipEntry.isDirectory()) {
            return;
        }
        File newFile = new File(filePath);
        try (InputStream fis = zipFile.getInputStream(zipEntry);
             FileOutputStream fos = new FileOutputStream(newFile)) {
            byte[] bytes = new byte[1024];
            int length;
            while ((length = fis.read(bytes)) >= 0) {
                fos.write(bytes, 0, length);
            }
        }
    }
    
    public static void unZipFromAssets(Context context, String zipFileName, String outputDir) {
        AssetManager assetManager = context.getAssets();
        try {
            InputStream inputStream = assetManager.open(zipFileName);
            File outDir = new File(outputDir);
            if (!outDir.exists()) {
                outDir.mkdirs();
            }
            ZipInputStream zipInputStream = new ZipInputStream(inputStream);
            ZipEntry zipEntry;
            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                String fileName = zipEntry.getName();
                File file = new File(outDir, fileName);
                if (zipEntry.isDirectory()) {
                    file.mkdirs();
                } else {
                    File parent = file.getParentFile();
                    if (!parent.exists()) {
                        parent.mkdirs();
                    }
                    FileOutputStream fileOutputStream = new FileOutputStream(file);
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = zipInputStream.read(buffer)) > 0) {
                        fileOutputStream.write(buffer, 0, len);
                    }
                    fileOutputStream.close();
                }
                zipInputStream.closeEntry();
            }
            zipInputStream.close();
            inputStream.close();
        } catch (IOException e) {
            Log.e(TAG, "Failed to unzip file: " + zipFileName, e);
        }
    }
    
    public static void setFolderPermissions(String folderPath) {
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
}
