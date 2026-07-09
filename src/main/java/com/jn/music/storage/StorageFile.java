package com.jn.music.storage;

/**
 * 存储文件信息
 */
public record StorageFile(
    String id,
    String name,
    long size
) {}

