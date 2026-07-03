package com.jn.music.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 文件服务地址配置：内部地址给 Spring Boot 访问 dufs，公开地址返回给客户端。
 */
@Component
@ConfigurationProperties(prefix = "jnmusic.file-server")
public class FileServerProperties {

    private String internalBaseUrl = "http://jn_file.88933.vip:27472";

    private String publicBaseUrl = "http://jn_file.88933.vip:27472";

    public String getInternalBaseUrl() {
        return normalizeBaseUrl(internalBaseUrl);
    }

    public void setInternalBaseUrl(String internalBaseUrl) {
        this.internalBaseUrl = internalBaseUrl;
    }

    public String getPublicBaseUrl() {
        return normalizeBaseUrl(publicBaseUrl);
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    public String internalUrl(String path) {
        return getInternalBaseUrl() + normalizePath(path);
    }

    public String publicUrl(String path) {
        return getPublicBaseUrl() + normalizePath(path);
    }

    private static String normalizeBaseUrl(String value) {
        String baseUrl = StringUtils.hasText(value) ? value.trim() : "http://jn_file.88933.vip:27472";
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }

    private static String normalizePath(String path) {
        if (!StringUtils.hasText(path)) {
            return "";
        }
        String trimmed = path.trim();
        return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
    }
}
