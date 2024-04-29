package com.qz.terminal2;

import android.os.Build;

import net.kdt.pojavlaunch.multirt.MultiRTUtils;
import net.kdt.pojavlaunch.multirt.Runtime;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

public class LocalEnv {
    
    public static final String ABI = Build.SUPPORTED_ABIS[0];
    
    public static String getJavaLibEnv(String javaHome) {
        String libString = null;
        
        String name = LauncherPreferences.PREF_DEFAULT_RUNTIME;
        Runtime javaRuntime = null;
        for (Runtime r : MultiRTUtils.getRuntimes()) {
            if (r.name.equals(name)) {
                javaRuntime = r;
                break;
            }
        }
        String path = javaHome + "/lib";
        
        if (javaRuntime.javaVersion == 8) {
            String arch = null;
            if (javaRuntime.arch.equals("i586")) {
                arch = "i386";
            } else {
                arch = javaRuntime.arch;
            }
            path = path + "/" + arch;
            libString = path + ":" +
                        path + "/jli:" + 
                        path + "/client:";
        } else {
            libString = path + ":" +
                        path + "/server:";
            if (javaRuntime.javaVersion == 11) {
                libString = libString + path + "/jli:";
            }
        }
        
        if (libString == null ) return "";
        return libString;
    }
    
    public static String getGccBin(String localPath) {
        localPath = localPath + "/gcc/";
        
        String name = null;
        String version = null;
        
        StringBuilder gccBin = new StringBuilder();
        
        if (ABI.equals("arm64-v8a")) {
            name = "aarch64-linux-android";
            version = "10.2.0";
        } else if (ABI.equals("armeabi-v7a")) {
            name = "arm-linux-androideabi";
            version = "9.1.0";
        } else if (ABI.equals("x86")) {
            name = "i686-linux-android";
            version = "9.1.0";
        } else {
            name = "x86_64-linux-android";
            version = "10.2.0";
        }
        gccBin.append(localPath).append("bin:")
            .append(localPath).append(name).append("/bin:")
            .append(localPath).append("libexec/gcc/").append(name)
            .append("/").append(version).append(":");
        return gccBin.toString();
    }
    
    // Python development environments may be added, but... emmm, the positioning of the terminal is not clear at present
}
