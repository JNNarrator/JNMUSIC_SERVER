package com.jn.music.lanzou;

import com.jn.music.lanzou.config.LanzouClientProperties;
import com.jn.music.lanzou.LanzouUidVei;
import org.junit.jupiter.api.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 Cookie 缓存 + bootstrap 自动装配闭环。
 * 步骤：
 *  1) 手动设置 Cookie → saveCookieCache() 落盘 → 断言文件存在且含 phpdisk_info
 *  2) 新建 client，只有缓存路径没有 setSessionCookie → bootstrap() → 应能从缓存恢复并拿到 uid/vei
 *  3) 删除缓存文件 → 再新建 client → bootstrap() 应返回 false（没有账号密码不做真登录）
 */
class LanzouSessionBootstrapTest {

    private static final String COOKIE =
            "PHPSESSID=9t3hv0lnci25verm18cqc79oj9q6bbo3; ylogin=5132788; " +
            "ylogins=79e9a37efd2a1e1f10bcac579ed9c16f; " +
            "uag=055d181bb351bd072329e713e678bcf8; folder_id_c=-1; " +
            "phpdisk_info=AzICMQVkBTsCNwFpDG4BUlQwDQZdNVA0BD4DZwQ7VmVVY15tUTYMN1VhB14NYFAzUjtSYwBtADUCMlQwBTtQMQM%2FAjcFZwU5AjABaQw3AWJUYA02XTJQMAQwA2UENlYxVTNeP1E0DGZVYwc1DV5QO1IxUmkAbQBuAjBUNgUyUGcDMAI2BV4FOAI3AWkMZQFoVD0NOV0wUDEENg%3D%3D";

    private LanzouClientProperties freshProperties(Path cachePath) {
        LanzouClientProperties p = new LanzouClientProperties();
        p.setBaseUrl("https://pc.woozooo.com");
        p.setShareUrl("https://pan.lanzoui.com");
        p.setDefaultRootFolderId("-1");
        p.setConnectTimeoutMs(15000);
        p.setReadTimeoutMs(30000);
        p.setCookieCachePath(cachePath.toString());
        p.setAutoRelogin(false); // 不触发账号密码登录路径，专测缓存机制
        p.setEagerBootstrap(false);
        return p;
    }

    @Test
    @DisplayName("Cookie 落盘→重新加载→bootstrap 命中缓存")
    void bootstrapReusesCookieCache() throws Exception {
        Path tmpCache = Files.createTempFile("lanzou-cookie-", ".json");
        Files.deleteIfExists(tmpCache);

        // 1) 第一次 client：手动灌 cookie + 落盘
        {
            LanzouClientProperties p1 = freshProperties(tmpCache);
            LanzouApiClient c1 = new LanzouApiClient(p1);
            c1.setSessionCookie(COOKIE);
            c1.saveCookieCache();
            assertTrue(Files.exists(tmpCache), "cookie cache 应写入");
            String text = Files.readString(tmpCache);
            assertTrue(text.contains("phpdisk_info"), "缓存 JSON 应包含 phpdisk_info");
        }

        // 2) 第二次 client：只有缓存路径 → bootstrap 应加载并通过 getUidVei
        {
            LanzouClientProperties p2 = freshProperties(tmpCache);
            LanzouApiClient c2 = new LanzouApiClient(p2);
            assertTrue(c2.bootstrap(), "带有效缓存的 bootstrap 应成功");
            LanzouUidVei uv = c2.getUidVei();
            assertEquals("5132788", uv.uid());
            assertNotNull(uv.vei());
        }

        // 3) 第三次 client：清掉缓存 + 无账号密码 → bootstrap 应 false 且不抛
        {
            Files.deleteIfExists(tmpCache);
            LanzouClientProperties p3 = freshProperties(tmpCache);
            LanzouApiClient c3 = new LanzouApiClient(p3);
            assertFalse(c3.bootstrap(), "无缓存且不允许自动登录时应返回 false");
        }

        Files.deleteIfExists(tmpCache);
    }
}
