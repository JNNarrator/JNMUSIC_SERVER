package com.jn.music.lanzou.dto;

public record LanzouFolder(String id, String name, String description) {
    public LanzouFolder(String id, String name) {
        this(id, name, "");
    }
}
