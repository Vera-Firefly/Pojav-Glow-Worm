package net.kdt.pojavlaunch.firefly.modloaders;

import java.io.File;

public interface ModloaderDownloadListener {
    void onDownloadFinished(File downloadedFile);

    void onDataNotAvailable();

    void onDownloadError(Exception e);
}
