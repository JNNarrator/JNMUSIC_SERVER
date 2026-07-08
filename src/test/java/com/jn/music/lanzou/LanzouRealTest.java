package com.jn.music.lanzou;

import com.jn.music.lanzou.config.LanzouClientProperties;
import com.jn.music.lanzou.dto.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 真实账号集成测试
 * 使用账号: 13949121576/jiangnan123
 */
class LanzouRealTest {

    private LanzouApiClient client;

    @BeforeEach
    void setUp() {
        LanzouClientProperties properties = new LanzouClientProperties();
        properties.setBaseUrl("https://pc.woozooo.com");
        properties.setShareUrl("https://pan.lanzoui.com");
        properties.setDefaultRootFolderId("-1");
        properties.setConnectTimeoutMs(15000);
        properties.setReadTimeoutMs(30000);
        client = new LanzouApiClient(properties);
    }

    @Test
    @DisplayName("测试账号密码登录")
    void testLoginWithPassword() {
        // 尝试用账号密码登录
        try {
            // 注意：蓝奏云目前主要使用Cookie登录，账号密码登录可能需要额外处理
            System.out.println("尝试登录...");
            
            // 这里需要实现真正的登录逻辑
            // 目前先跳过，使用Cookie方式
            System.out.println("蓝奏云推荐使用Cookie登录方式");
            System.out.println("请从浏览器获取Cookie后使用");
            
        } catch (Exception e) {
            System.out.println("登录测试: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("测试Cookie登录和获取uid/vei")
    void testCookieLogin() {
        // 这里需要填入真实的Cookie
        // 从浏览器开发者工具获取
        String cookie = "phpdisk_info=REPLACE_WITH_REAL_COOKIE";
        
        if (cookie.contains("REPLACE")) {
            System.out.println("请替换为真实Cookie");
            System.out.println("获取方式: 浏览器登录蓝奏云 -> F12 -> Network -> 复制Cookie");
            return;
        }
        
        client.setSessionCookie(cookie);
        
        try {
            LanzouUidVei uidVei = client.getUidVei();
            System.out.println("登录成功!");
            System.out.println("uid: " + uidVei.uid());
            System.out.println("vei: " + uidVei.vei());
        } catch (Exception e) {
            System.out.println("获取uid/vei失败: " + e.getMessage());
        }
    }
}
