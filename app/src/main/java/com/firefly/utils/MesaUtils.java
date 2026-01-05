package com.firefly.utils;

import android.content.Context;
import android.net.Uri;


import net.kdt.pojavlaunch.firefly.Tools;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class MesaUtils {
    public static final MesaUtils INSTANCE = new MesaUtils();
    private final File mesaDir;

    private MesaUtils() {
        this.mesaDir = new File(Tools.MESA_DIR);
        if (!mesaDir.exists() && !mesaDir.mkdirs()) {
            throw new RuntimeException("Failed to create mesa directory");
        }
    }

    public File getMesaDir() {
        return mesaDir;
    }

    public List<String> getMesaLibList() {
        List<String> list = new ArrayList<>();
        File[] files = mesaDir.listFiles();
        for (File file : files) {
            if (file.isDirectory() && new File(file.getAbsolutePath() + "/libOSMesa_8.so").exists()) {
                list.add(file.getName());
            }
        }

        return list;
    }

    public String getMesaLib(String version) {
        return Tools.MESA_DIR + "/" + version + "/libOSMesa_8.so";
    }

    public boolean saveMesaVersion(Context context, Uri fileUri, String folderName) {
        try (InputStream inputStream = context.getContentResolver().openInputStream(fileUri)) {
            if (inputStream == null || !PGWTools.isELFFile(inputStream)) {
                return false;
            }
        
            inputStream.close(); // Close an open validation file stream
            InputStream newInputStream = context.getContentResolver().openInputStream(fileUri);
       
            File targetDir = new File(mesaDir, folderName);
            if (!targetDir.exists() && !targetDir.mkdirs()) {
                return false;
            }

            File targetFile = new File(targetDir, "libOSMesa_8.so");
            try (OutputStream outputStream = new FileOutputStream(targetFile)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = newInputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean deleteMesaLib(String version) {
        File libDir = new File(mesaDir, version);
        if (libDir.exists()) {
            return deleteDirectory(libDir);
        }
        return false;
    }

    private boolean deleteDirectory(File directoryToBeDeleted) {
        File[] allContents = directoryToBeDeleted.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteDirectory(file);
            }
        }
        return directoryToBeDeleted.delete();
    }

}
