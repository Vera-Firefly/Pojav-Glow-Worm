package net.kdt.pojavlaunch.firefly.value;

import androidx.annotation.Keep;

import net.kdt.pojavlaunch.firefly.JMinecraftVersionList.Arguments.ArgValue.ArgRules;

@Keep
public class DependentLibrary {
    public ArgRules[] rules;
    public String name;
    public LibraryDownloads downloads;
    public String url;

    @Keep
    public static class LibraryDownloads {
        public final MinecraftLibraryArtifact artifact;

        public LibraryDownloads(MinecraftLibraryArtifact artifact) {
            this.artifact = artifact;
        }
    }
}

