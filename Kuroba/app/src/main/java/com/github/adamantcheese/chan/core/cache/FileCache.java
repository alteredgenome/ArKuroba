/*
 * Kuroba - *chan browser https://github.com/Adamantcheese/Kuroba/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.adamantcheese.chan.core.cache;

import androidx.annotation.MainThread;

import com.github.adamantcheese.chan.utils.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FileCache implements FileCacheDownloader.Callback {
    private static final String TAG = "FileCache";
    private static final int DOWNLOAD_POOL_SIZE = 2;

    private final ExecutorService downloadPool = Executors.newFixedThreadPool(DOWNLOAD_POOL_SIZE);

    private final CacheHandler cacheHandler;

    private List<FileCacheDownloader> downloaders = new ArrayList<>();

    public FileCache(File directory) {
        cacheHandler = new CacheHandler(directory);
    }

    public void clearCache() {
        for (FileCacheDownloader downloader : downloaders) {
            downloader.cancel();
        }

        cacheHandler.clearCache();
    }

    /**
     * Start downloading the file located at the url.<br>
     * If the file is in the cache then the callback is executed immediately and null is
     * returned.<br>
     * Otherwise if the file is downloading or has not yet started downloading a
     * {@link FileCacheDownloader} is returned.<br>
     *
     * @param url      the url to download.
     * @param listener listener to execute callbacks on.
     * @return {@code null} if in the cache, {@link FileCacheDownloader} otherwise.
     */
    @MainThread
    public FileCacheDownloader downloadFile(String url, FileCacheListener listener) {
        FileCacheDownloader runningDownloaderForKey = getDownloaderByKey(url);
        if (runningDownloaderForKey != null) {
            if (listener != null) {
                runningDownloaderForKey.addListener(listener);
            }
            return runningDownloaderForKey;
        }

        File file = get(url);
        if (file.exists()) {
            handleFileImmediatelyAvailable(listener, file);
            return null;
        } else {
            return handleStartDownload(listener, file, url);
        }
    }

    public FileCacheDownloader getDownloaderByKey(String key) {
        for (FileCacheDownloader downloader : downloaders) {
            if (downloader.getUrl().equals(key)) {
                return downloader;
            }
        }
        return null;
    }

    @Override
    public void downloaderFinished(FileCacheDownloader fileCacheDownloader) {
        downloaders.remove(fileCacheDownloader);
    }

    @Override
    public void downloaderAddedFile(File file) {
        cacheHandler.fileWasAdded(file);
    }

    public boolean exists(String key) {
        return cacheHandler.exists(key);
    }

    public File get(String key) {
        return cacheHandler.get(key);
    }

    public long getFileCacheSize() {
        return cacheHandler.getSize().get();
    }

    private void handleFileImmediatelyAvailable(FileCacheListener listener, File file) {
        // TODO: setLastModified doesn't seem to work on Android...
        if (!file.setLastModified(System.currentTimeMillis())) {
            Logger.e(TAG, "Could not set last modified time on file");
        }
        if (listener != null) {
            listener.onSuccess(file);
            listener.onEnd();
        }
    }

    private FileCacheDownloader handleStartDownload(
            FileCacheListener listener, File file, String url) {
        FileCacheDownloader downloader = FileCacheDownloader.fromCallbackClientUrlOutputUserAgent(
                this, url, file);
        if (listener != null) {
            downloader.addListener(listener);
        }
        downloader.execute(downloadPool);
        downloaders.add(downloader);
        return downloader;
    }
}
