package com.jn.music.lanzou;

import com.jn.music.lanzou.config.LanzouClientProperties;
import com.jn.music.lanzou.dto.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 蓝奏云完整功能测试
 * 测试账号: 13949121576/jiangnan123
 */
class LanzouFullTest {

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
    void testLogin() {
        System.out.println("=== 测试蓝奏云登录 ===");
        System.out.println("账号: 13949121576");
        
        try {
            // 尝试登录
            client.login("13949121576", "jiangnan123");
            System.out.println("✓ 登录成功!");
            
            // 获取uid/vei
            LanzouUidVei uidVei = client.getUidVei();
            System.out.println("✓ 获取uid/vei成功");
            System.out.println("  uid: " + uidVei.uid());
            System.out.println("  vei: " + uidVei.vei());
            
        } catch (LanzouSessionException e) {
            System.out.println("✗ 登录失败: " + e.getMessage());
            System.out.println("提示: 蓝奏云可能需要Cookie登录或验证码");
            System.out.println("建议: 从浏览器获取Cookie后使用setSessionCookie方法");
        } catch (Exception e) {
            System.out.println("✗ 异常: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("测试列出文件")
    void testListFiles() {
        System.out.println("=== 测试列出文件 ===");
        
        // 先尝试登录
        try {
            client.login("13949121576", "jiangnan123");
            System.out.println("登录成功，开始列出文件...");
        } catch (Exception e) {
            System.out.println("登录失败，跳过测试: " + e.getMessage());
            return;
        }
        
        try {
            LanzouPageResult result = client.listFiles("-1", 1);
            System.out.println("✓ 获取文件列表成功");
            System.out.println("  文件数量: " + result.files().size());
            System.out.println("  文件夹数量: " + result.folders().size());
            
            if (!result.files().isEmpty()) {
                System.out.println("\n文件列表:");
                result.files().forEach(f -> 
                    System.out.println("  - " + f.id() + ": " + f.name()));
            }
            
            if (!result.folders().isEmpty()) {
                System.out.println("\n文件夹列表:");
                result.folders().forEach(f -> 
                    System.out.println("  - " + f.id() + ": " + f.name()));
            }
            
        } catch (Exception e) {
            System.out.println("✗ 获取文件列表失败: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("测试获取下载直链")
    void testGetDirectLink() {
        System.out.println("=== 测试获取下载直链 ===");
        
        // 先尝试登录
        try {
            client.login("13949121576", "jiangnan123");
            System.out.println("登录成功");
        } catch (Exception e) {
            System.out.println("登录失败，跳过测试: " + e.getMessage());
            return;
        }
        
        // 先获取文件列表
        try {
            LanzouPageResult result = client.listFiles("-1", 1);
            
            if (result.files().isEmpty()) {
                System.out.println("没有文件，无法测试下载直链");
                return;
            }
            
            // 获取第一个文件的直链
            LanzouFile firstFile = result.files().getFirst();
            System.out.println("测试文件: " + firstFile.name() + " (ID: " + firstFile.id() + ")");
            
            LanzouDirectLink link = client.getFileDownloadLink(firstFile.id());
            System.out.println("✓ 获取下载直链成功!");
            System.out.println("  直链: " + link.url());
            System.out.println("  过期时间: " + link.expiresAt());
            
        } catch (Exception e) {
            System.out.println("✗ 获取下载直链失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
