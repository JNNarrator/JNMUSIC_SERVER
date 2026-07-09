package com.jn.music.track.service.impl;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ParsedNameTest {

    static ParsedName parse(String desc, String folderName) {
        com.google.gson.Gson gson = new com.google.gson.Gson();
        
        if (desc != null && !desc.isBlank()) {
            desc = desc.trim();
            try {
                com.google.gson.JsonObject obj = gson.fromJson(desc, com.google.gson.JsonObject.class);
                if (obj != null) {
                    String artist = obj.has("artist") ? obj.get("artist").getAsString() : null;
                    String name = obj.has("name") ? obj.get("name").getAsString() : null;
                    if (name != null && !name.isBlank()) {
                        return new ParsedName(name.trim(), artist, "flac", false);
                    }
                }
            } catch (Exception ignored) {}
            
            String legacy = desc;
            if (legacy.startsWith("[") && legacy.endsWith("]")) {
                legacy = legacy.substring(1, legacy.length() - 1).trim();
            }
            int dash = legacy.indexOf('-');
            if (dash > 0) {
                String artist = legacy.substring(0, dash).trim();
                String name = legacy.substring(dash + 1).trim();
                if (!artist.isEmpty() && !name.isEmpty()) {
                    return new ParsedName(name, artist, "flac", false);
                }
            }
        }
        
        if (folderName != null && !folderName.isBlank()) {
            int dash = folderName.indexOf('-');
            if (dash > 0) {
                return new ParsedName(folderName.substring(dash + 1).trim(), folderName.substring(0, dash).trim(), "flac", false);
            }
            return new ParsedName(folderName, null, "flac", false);
        }
        return new ParsedName("", null, "", false);
    }

    record ParsedName(String name, String artist, String format, boolean isLyric) {}

    @Test
    void testJsonFormat() {
        var r = parse("{\"artist\":\"双笙\",\"name\":\"庙前\"}", "双笙-庙前");
        assertEquals("双笙", r.artist());
        assertEquals("庙前", r.name());
    }

    @Test
    void testJsonWithSpaces() {
        var r = parse("{\"artist\":\"A神\",\"name\":\"The Nights\"}", "A神-TheNights");
        assertEquals("A神", r.artist());
        assertEquals("The Nights", r.name());
    }

    @Test
    void testLegacyBracketFormat() {
        var r = parse("[A神 - Waiting For Love]", "A神-WaitingForLove");
        assertEquals("A神", r.artist());
        assertEquals("Waiting For Love", r.name());
    }

    @Test
    void testEmptyDescFallback() {
        var r = parse("", "犬儒乐队-志铭");
        assertEquals("犬儒乐队", r.artist());
        assertEquals("志铭", r.name());
    }
}
