package com.jn.music.lanzou;

import com.jn.music.lanzou.config.LanzouClientProperties;
import com.jn.music.lanzou.dto.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 完整登录流程测试
 */
class LanzouLoginFlowTest {

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
    @DisplayName("完整流程：登录 → 列文件 → 获取下载直链")
    void testFullFlow() {
        System.out.println("=== 蓝奏云完整流程测试 ===");
        System.out.println();

        // Step 1: 登录
        System.out.println("Step 1: 登录...");
        try {
            client.login("13949121576", "jiangnan123");
            System.out.println("✓ 登录成功");
        } catch (Exception e) {
            System.out.println("✗ 登录失败: " + e.getMessage());
            fail("登录失败");
        }

        // Step 2: 获取uid/vei
        System.out.println("\nStep 2: 获取uid/vei...");
        try {
            LanzouUidVei uidVei = client.getUidVei();
            System.out.println("✓ uid: " + uidVei.uid());
            System.out.println("✓ vei: " + uidVei.vei());
        } catch (Exception e) {
            System.out.println("✗ 获取uid/vei失败: " + e.getMessage());
            fail("获取uid/vei失败");
        }

        // Step 3: 列出文件
        System.out.println("\nStep 3: 列出文件...");
        LanzouPageResult result = null;
        try {
            result = client.listFiles("-1", 1);
            System.out.println("✓ 文件数量: " + result.files().size());
            System.out.println("✓ 文件夹数量: " + result.folders().size());

            if (!result.files().isEmpty()) {
                System.out.println("\n文件列表:");
                result.files().forEach(f ->
                    System.out.println("  [" + f.id() + "] " + f.name()));
            }
        } catch (Exception e) {
            System.out.println("✗ 列出文件失败: " + e.getMessage());
            fail("列出文件失败");
        }

        // Step 4: 获取下载直链
        if (result != null && !result.files().isEmpty()) {
            System.out.println("\nStep 4: 获取下载直链...");
            LanzouFile firstFile = result.files().getFirst();
            System.out.println("目标文件: " + firstFile.name() + " (ID=" + firstFile.id() + ")");

            try {
                LanzouDirectLink link = client.getFileDownloadLink(firstFile.id());
                System.out.println("✓ 直链: " + link.url());
                System.out.println("✓ 过期时间: " + link.expiresAt());

                assertNotNull(link.url());
                assertTrue(link.url().startsWith("http"));
            } catch (Exception e) {
                System.out.println("✗ 获取下载直链失败: " + e.getMessage());
                e.printStackTrace();
                fail("获取下载直链失败");
            }
        }

        System.out.println("\n=== 测试完成 ===");
    }
}
