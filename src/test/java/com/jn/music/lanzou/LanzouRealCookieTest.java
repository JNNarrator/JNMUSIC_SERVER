package com.jn.music.lanzou;

import com.jn.music.lanzou.config.LanzouClientProperties;
import com.jn.music.lanzou.dto.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 使用真实Cookie测试蓝奏云核心功能
 */
class LanzouRealCookieTest {

    private static final String COOKIE =
            "PHPSESSID=9t3hv0lnci25verm18cqc79oj9q6bbo3; "
            + "ylogin=5132788; "
            + "ylogins=79e9a37efd2a1e1f10bcac579ed9c16f; "
            + "uag=055d181bb351bd072329e713e678bcf8; "
            + "folder_id_c=-1; "
            + "phpdisk_info=AzICMQVkBTsCNwFpDG4BUlQwDQZdNVA0BD4DZwQ7VmVVY15tUTYMN1VhB14NYFAzUjtSYwBtADUCMlQwBTtQMQM%2FjcFZwU5AjABaQw3AWJUYA02XTJQMAQwA2UENlYxVTNeP1E0DGZVYwc1DV5QO1IxUmkAbQBuAjBUNgUyUGcDMAI2BV4FOAI3AWkMZQFoVD0NOV0wUDEENg%3D%3D";

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
        client.setSessionCookie(COOKIE);
    }

    @Test
    @DisplayName("1. 获取uid/vei")
    void testGetUidVei() {
        System.out.println("=== 测试获取uid/vei ===");
        try {
            LanzouUidVei uidVei = client.getUidVei();
            System.out.println("✓ uid: " + uidVei.uid());
            System.out.println("✓ vei: " + uidVei.vei());
            assertNotNull(uidVei.uid());
            assertNotNull(uidVei.vei());
        } catch (Exception e) {
            System.out.println("✗ 失败: " + e.getMessage());
            fail("获取uid/vei失败: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("2. 列出根目录文件")
    void testListFiles() {
        System.out.println("=== 测试列出文件 ===");
        try {
            LanzouPageResult result = client.listFiles("-1", 1);
            System.out.println("✓ 文件数量: " + result.files().size());
            System.out.println("✓ 文件夹数量: " + result.folders().size());

            if (!result.folders().isEmpty()) {
                System.out.println("\n文件夹:");
                result.folders().forEach(f ->
                    System.out.println("  [" + f.id() + "] " + f.name()));
            }
            if (!result.files().isEmpty()) {
                System.out.println("\n文件:");
                result.files().forEach(f ->
                    System.out.println("  [" + f.id() + "] " + f.name() + " (shareId=" + f.shareId() + ")"));
            }
        } catch (Exception e) {
            System.out.println("✗ 失败: " + e.getMessage());
            e.printStackTrace();
            fail("列出文件失败: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("3. 获取下载直链（核心功能）")
    void testGetDirectLink() {
        System.out.println("=== 测试获取下载直链 ===");
        try {
            // 先列出文件
            LanzouPageResult result = client.listFiles("-1", 1);
            if (result.files().isEmpty()) {
                System.out.println("没有文件，跳过测试");
                return;
            }

            LanzouFile firstFile = result.files().getFirst();
            System.out.println("目标文件: " + firstFile.name() + " (ID=" + firstFile.id() + ")");

            // 获取下载直链
            LanzouDirectLink link = client.getFileDownloadLink(firstFile.id());
            System.out.println("✓ 直链: " + link.url());
            System.out.println("✓ 过期时间: " + link.expiresAt());

            assertNotNull(link.url());
            assertTrue(link.url().startsWith("http"));
        } catch (Exception e) {
            System.out.println("✗ 失败: " + e.getMessage());
            e.printStackTrace();
            fail("获取下载直链失败: " + e.getMessage());
        }
    }
}
