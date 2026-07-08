package com.jn.music.lanzou;

import com.jn.music.lanzou.config.LanzouClientProperties;
import org.junit.jupiter.api.*;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 调试测试
 */
class LanzouDebugTest {

    @Test
    @DisplayName("调试登录流程")
    void testLoginDebug() throws Exception {
        System.out.println("=== 调试登录流程 ===");
        
        LanzouClientProperties properties = new LanzouClientProperties();
        properties.setBaseUrl("https://pc.woozooo.com");
        properties.setShareUrl("https://pan.lanzoui.com");
        properties.setDefaultRootFolderId("-1");
        properties.setConnectTimeoutMs(15000);
        properties.setReadTimeoutMs(30000);
        
        LanzouApiClient client = new LanzouApiClient(properties);
        
        // 测试requestGet
        System.out.println("1. 测试requestGet...");
        try {
            Method requestGet = LanzouApiClient.class.getDeclaredMethod("requestGet", String.class);
            requestGet.setAccessible(true);
            String body = (String) requestGet.invoke(client, "https://up.woozooo.com/mlogin.php");
            System.out.println("  响应长度: " + body.length());
            System.out.println("  包含arg1: " + body.contains("arg1="));
            System.out.println("  包含acw_sc__v2: " + body.contains("acw_sc__v2"));
        } catch (Exception e) {
            System.out.println("  错误: " + e.getMessage());
            if (e.getCause() != null) {
                System.out.println("  原因: " + e.getCause().getMessage());
            }
        }
        
        // 测试sessionCookies
        System.out.println("\n2. 检查sessionCookies...");
        System.out.println("  Cookie数量: " + client.sessionCookieNames().size());
        System.out.println("  Cookie名称: " + client.sessionCookieNames());
        
        // 尝试登录
        System.out.println("\n3. 尝试登录...");
        try {
            client.login("13949121576", "jiangnan123");
            System.out.println("  ✓ 登录成功");
        } catch (Exception e) {
            System.out.println("  ✗ 登录失败: " + e.getMessage());
        }
    }
}
