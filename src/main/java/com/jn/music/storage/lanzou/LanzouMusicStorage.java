package com.jn.music.storage.lanzou;

import com.jn.music.lanzou.LanzouApiClient;
import com.jn.music.lanzou.dto.LanzouDirectLink;
import com.jn.music.lanzou.dto.LanzouFile;
import com.jn.music.lanzou.dto.LanzouFolder;
import com.jn.music.lanzou.dto.LanzouPageResult;
import com.jn.music.storage.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 蓝奏云存储实现
 */
@Component
public class LanzouMusicStorage implements MusicStorage {

    private final LanzouApiClient lanzouClient;

    public LanzouMusicStorage(LanzouApiClient lanzouClient) {
        this.lanzouClient = lanzouClient;
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
                folders.add(new StorageFolder(f.id(), f.name()));
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
    public String getStorageName() {
        return "lanzou";
    }
}
