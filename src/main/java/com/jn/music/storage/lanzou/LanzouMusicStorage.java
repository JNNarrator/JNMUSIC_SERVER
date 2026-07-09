package com.jn.music.storage.lanzou;

import com.jn.music.lanzou.LanzouApiClient;
import com.jn.music.lanzou.dto.LanzouDirectLink;
import com.jn.music.lanzou.dto.LanzouFile;
import com.jn.music.lanzou.dto.LanzouFolder;
import com.jn.music.lanzou.dto.LanzouPageResult;
import com.jn.music.storage.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 蓝奏云存储实现
 */
@Component
public class LanzouMusicStorage implements MusicStorage {

    private final LanzouApiClient lanzouClient;
    private final ExecutorService executorService;

    public LanzouMusicStorage(LanzouApiClient lanzouClient) {
        this.lanzouClient = lanzouClient;
        this.executorService = Executors.newFixedThreadPool(10);
    }

    @Override
    public StorageListResult listFiles(String folderId, int page) {
        LanzouPageResult result = lanzouClient.listFiles(folderId, page);
        if (result == null) {
            return new StorageListResult(page, List.of(), List.of());
        }

        List<StorageFile> files = new ArrayList<>();
        if (result.files() != null) {
            for (LanzouFile f : result.files()) {
                files.add(new StorageFile(f.id(), f.name(), f.size()));
            }
        }

        List<StorageFolder> folders = new ArrayList<>();
        if (result.folders() != null) {
            for (LanzouFolder f : result.folders()) {
                folders.add(new StorageFolder(f.id(), f.name(), f.description()));
            }
        }

        return new StorageListResult(page, files, folders);
    }

    @Override
    public String getDownloadUrl(String fileId) {
        try {
            LanzouDirectLink link = lanzouClient.getFileDownloadLink(fileId);
            return link.url();
        } catch (Exception e) {
            throw new RuntimeException("获取下载链接失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, String> getDownloadUrls(List<String> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return Map.of();
        }

        Map<String, String> result = new ConcurrentHashMap<>();
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (String fileId : fileIds) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    String url = getDownloadUrl(fileId);
                    result.put(fileId, url);
                } catch (Exception e) {
                    // 单个失败不影响其他
                }
            }, executorService);
            futures.add(future);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        return result;
    }

    @Override
    public String getStorageName() {
        return "lanzou";
    }
}
