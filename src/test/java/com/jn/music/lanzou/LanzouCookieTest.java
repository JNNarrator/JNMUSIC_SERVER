package com.jn.music.lanzou;

import com.jn.music.lanzou.config.LanzouClientProperties;
import com.jn.music.lanzou.dto.*;
import org.junit.jupiter.api.*;

import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cookie登录测试
 * 提示用户输入Cookie进行测试
 */
class LanzouCookieTest {

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
    @DisplayName("测试Cookie登录")
    void testCookieLogin() {
        System.out.println("=== 蓝奏云Cookie登录测试 ===");
        System.out.println();
        System.out.println("请按以下步骤获取Cookie:");
        System.out.println("1. 打开浏览器访问 https://pc.woozooo.com");
        System.out.println("2. 使用账号 13949121576 登录");
        System.out.println("3. 按F12打开开发者工具");
        System.out.println("4. 切换到Network标签");
        System.out.println("5. 刷新页面");
        System.out.println("6. 点击任意请求，复制Cookie");
        System.out.println();
        
        // 注意：在自动化测试中，我们需要硬编码Cookie
        // 实际使用时需要替换为真实Cookie
        String cookie =
		        "PHPSESSID=9t3hv0lnci25verm18cqc79oj9q6bbo3; ylogin=5132788; ylogins=79e9a37efd2a1e1f10bcac579ed9c16f; uag=055d181bb351bd072329e713e678bcf8; folder_id_c=-1; phpdisk_info=AzICMQVkBTsCNwFpDG4BUlQwDQZdNVA0BD4DZwQ7VmVVY15tUTYMN1VhB14NYFAzUjtSYwBtADUCMlQwBTtQMQM%2FAjcFZwU5AjABaQw3AWJUYA02XTJQMAQwA2UENlYxVTNeP1E0DGZVYwc1DV5QO1IxUmkAbQBuAjBUNgUyUGcDMAI2BV4FOAI3AWkMZQFoVD0NOV0wUDEENg%3D%3D";
        
        if (cookie.contains("REPLACE")) {
            System.out.println("请在代码中替换为真实Cookie后运行测试");
            System.out.println("文件位置: src/test/java/com/jn/music/lanzou/LanzouCookieTest.java");
            return;
        }
        
        client.setSessionCookie(cookie);
        
        try {
            LanzouUidVei uidVei = client.getUidVei();
            System.out.println("✓ Cookie登录成功!");
            System.out.println("  uid: " + uidVei.uid());
            System.out.println("  vei: " + uidVei.vei());
            
            // 测试列出文件
            System.out.println("\n测试列出文件...");
            LanzouPageResult result = client.listFiles("-1", 1);
            System.out.println("✓ 获取文件列表成功");
            System.out.println("  文件数量: " + result.files().size());
            System.out.println("  文件夹数量: " + result.folders().size());
            
            if (!result.files().isEmpty()) {
                System.out.println("\n文件列表:");
                result.files().forEach(f -> 
                    System.out.println("  - " + f.id() + ": " + f.name()));
                
                // 测试获取下载直链
                System.out.println("\n测试获取下载直链...");
                LanzouFile firstFile = result.files().getFirst();
                LanzouDirectLink link = client.getFileDownloadLink(firstFile.id());
                System.out.println("✓ 获取下载直链成功!");
                System.out.println("  文件: " + firstFile.name());
                System.out.println("  直链: " + link.url());
                System.out.println("  过期时间: " + link.expiresAt());
            }
            
        } catch (Exception e) {
            System.out.println("✗ 测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
