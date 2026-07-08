package com.jn.music.lanzou;

import com.jn.music.lanzou.config.LanzouClientProperties;
import com.jn.music.lanzou.dto.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 蓝奏云集成测试 - 使用真实账号测试
 */
class LanzouIntegrationTest {

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
    @Disabled("需要真实Cookie才能运行")
    void testLoginWithCookie() {
        // 从浏览器获取的Cookie
        String cookie = "phpdisk_info=your_cookie_here";
        assertDoesNotThrow(() -> client.setSessionCookie(cookie));
        
        // 获取uid/vei
        LanzouUidVei uidVei = client.getUidVei();
        assertNotNull(uidVei.uid());
        assertNotNull(uidVei.vei());
        System.out.println("uid: " + uidVei.uid());
        System.out.println("vei: " + uidVei.vei());
    }

    @Test
    @Disabled("需要真实Cookie才能运行")
    void testListFiles() {
        // 先设置Cookie和uid/vei
        String cookie = "phpdisk_info=your_cookie_here";
        client.setSessionCookie(cookie);
        client.setUidVei("your_uid", "your_vei");
        
        // 列出根目录文件
        LanzouPageResult result = client.listFiles("-1", 1);
        assertNotNull(result);
        System.out.println("文件数量: " + result.files().size());
        System.out.println("文件夹数量: " + result.folders().size());
        
        result.files().forEach(f -> 
            System.out.println("文件: " + f.id() + " - " + f.name()));
        result.folders().forEach(f -> 
            System.out.println("文件夹: " + f.id() + " - " + f.name()));
    }

    @Test
    @Disabled("需要真实Cookie和文件ID才能运行")
    void testGetDirectLink() {
        // 先设置Cookie和uid/vei
        String cookie = "phpdisk_info=your_cookie_here";
        client.setSessionCookie(cookie);
        client.setUidVei("your_uid", "your_vei");
        
        // 获取文件直链
        String fileId = "your_file_id";
        LanzouDirectLink link = client.getFileDownloadLink(fileId);
        
        assertNotNull(link);
        assertNotNull(link.url());
        assertNotNull(link.expiresAt());
        System.out.println("直链: " + link.url());
        System.out.println("过期时间: " + link.expiresAt());
    }
}
