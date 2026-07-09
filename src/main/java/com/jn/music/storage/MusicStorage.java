package com.jn.music.storage;

/**
 * 音乐存储抽象接口
 */
public interface MusicStorage {

    /**
     * 列出指定文件夹下的文件和子文件夹
     */
    StorageListResult listFiles(String folderId, int page);

    /**
     * 获取文件的下载直链
     */
    String getDownloadUrl(String fileId);

    /**
     * 获取存储实现名称
     */
    String getStorageName();
}
