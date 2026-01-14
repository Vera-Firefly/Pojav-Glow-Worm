package com.movtery.feature.mod.parser;

import androidx.annotation.NonNull;

import java.io.File;
import java.util.Arrays;

public class ModInfo {
    private File file;
    private final String id;
    private final String version;
    private final String name;
    private final String description;
    private final String[] authors;

    public ModInfo(String id, String version, String name, String description, String[] authors) {
        this.id = id;
        this.version = version;
        this.name = name;
        this.description = description;
        this.authors = authors;
    }

    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
    }

    public String getId() {
        return id;
    }

    public String getVersion() {
        return version;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String[] getAuthors() {
        return authors;
    }

    @NonNull
    @Override
    public String toString() {
        return "ModInfo{" +
                "id='" + id + '\'' +
                ", version='" + version + '\'' +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", authors=" + Arrays.toString(authors) +
                '}';
    }
}
