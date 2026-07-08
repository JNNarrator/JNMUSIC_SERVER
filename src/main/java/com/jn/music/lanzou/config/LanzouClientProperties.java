package com.jn.music.lanzou.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 蓝奏云客户端配置。
 *
 * <p>推荐用法：家用宽带出口 + 单账号，把 {@code username}/{@code password} 配上，
 * 客户端会自动做 Cookie 缓存 + 失效续期。日常无感，Cookie 过期时才会走真登录，
 * 若真登录被反爬拦截（滑块），会抛异常提示手动灌 Cookie。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "lanzou.client")
public class LanzouClientProperties {
    private String baseUrl = "https://pc.woozooo.com";
    private String shareUrl = "https://pan.lanzoui.com";
    private String defaultRootFolderId = "-1";
    private long connectTimeoutMs = 10000;
    private long readTimeoutMs = 30000;

    // ===== 账号/凭据 =====

    /** 蓝奏云账号（手机号或用户名）。可选：仅在启用自动登录时需要。 */
    private String username;

    /** 蓝奏云密码。可选：仅在启用自动登录时需要。 */
    private String password;

    /**
     * Cookie 缓存文件路径。默认写入用户主目录下 {@code ~/.config/music/lanzou-cookie.json}。
     * 家用 Ubuntu 部署时建议保持默认即可。
     */
    private String cookieCachePath = System.getProperty("user.home") + "/.config/music/lanzou-cookie.json";

    /** 是否在启动/失效时自动使用账号密码续期。默认开启（有 username/password 时才真正生效）。 */
    private boolean autoRelogin = true;

    /** 两次自动登录尝试之间的最小间隔（毫秒），避免频控。 */
    private long reloginCooldownMs = 5 * 60 * 1000L;

    /** 启动时是否阻塞式初始化会话（bootstrap）。设为 false 时改为懒加载：第一次调用 API 时才尝试。 */
    private boolean eagerBootstrap = true;
}
