package com.jn.music.lanzou;

import com.jn.music.lanzou.config.LanzouClientProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

/**
 * 蓝奏云客户端自动装配。
 * <p>启动时按配置的用户名/密码 + 本地 Cookie 缓存自动初始化会话；
 * 缓存无效或过期时尝试账号密码续期，失败时打印引导（不阻断应用启动）。</p>
 */
@Configuration
@ComponentScan(basePackages = "com.jn.music.lanzou")
public class LanzouClientAutoConfiguration {

    @Component
    public static class LanzouClientBootstrapper {
        private final LanzouApiClient client;
        private final LanzouClientProperties properties;

        public LanzouClientBootstrapper(LanzouApiClient client, LanzouClientProperties properties) {
            this.client = client;
            this.properties = properties;
        }

        @PostConstruct
        public void init() {
            if (!properties.isEagerBootstrap()) return;
            try {
                boolean ok = client.bootstrap();
                if (ok) {
                    System.out.println("[lanzou] session ready. cache=" + properties.getCookieCachePath());
                } else {
                    System.err.println("[lanzou] session NOT ready. 请配置 lanzou.client.username/password 或手动写入 "
                            + properties.getCookieCachePath());
                }
            } catch (Exception e) {
                // 不阻断应用启动；后续 API 调用命中失效时还会再触发一次 bootstrap。
                System.err.println("[lanzou] bootstrap failed: " + e.getMessage());
            }
        }
    }
}
