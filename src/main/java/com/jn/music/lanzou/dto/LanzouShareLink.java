package com.jn.music.lanzou.dto;

import java.time.Instant;

public record LanzouShareLink(String shareId, String shareUrl, String directUrl, boolean requirePassword) {}
