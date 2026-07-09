package com.jn.music.storage;

public record StorageFolder(String id, String name, String description) {
    public StorageFolder(String id, String name) {
        this(id, name, "");
    }
}
