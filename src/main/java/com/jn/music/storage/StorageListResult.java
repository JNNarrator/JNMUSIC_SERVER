package com.jn.music.storage;

import java.util.List;

/**
 * 存储列表结果
 */
public record StorageListResult(
    int page,
    List<StorageFile> files,
    List<StorageFolder> folders
) {}

