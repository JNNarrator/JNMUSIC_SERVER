package com.jn.music.lanzou;

import com.jn.music.lanzou.config.LanzouClientProperties;
import com.jn.music.lanzou.dto.*;

/**
 * 命令行测试工具
 * 用法: mvn test -Dtest='LanzouCommandLineTest' -Dcookie='你的Cookie'
 */
public class LanzouCommandLineTest {
    
    public static void main(String[] args) {
        System.out.println("=== 蓝奏云命令行测试工具 ===");
        System.out.println();
        
        // 从系统属性获取Cookie
        String cookie = System.getProperty("cookie", "");
        if (cookie.isEmpty()) {
            System.out.println("用法: mvn test -Dtest='LanzouCommandLineTest' -Dcookie='你的Cookie'");
            System.out.println();
            System.out.println("获取Cookie步骤:");
            System.out.println("1. 浏览器访问 https://pc.woozooo.com");
            System.out.println("2. 使用账号 13949121576 登录");
            System.out.println("3. F12 -> Network -> 复制Cookie");
            return;
        }
        
        // 配置
        LanzouClientProperties properties = new LanzouClientProperties();
        properties.setBaseUrl("https://pc.woozooo.com");
        properties.setShareUrl("https://pan.lanzoui.com");
        properties.setDefaultRootFolderId("-1");
        properties.setConnectTimeoutMs(15000);
        properties.setReadTimeoutMs(30000);
        
        // 创建客户端
        LanzouApiClient client = new LanzouApiClient(properties);
        
        try {
            // Cookie登录
            System.out.println("1. 使用Cookie登录...");
            client.setSessionCookie(cookie);
            
            // 获取uid/vei
            System.out.println("2. 获取uid/vei...");
            LanzouUidVei uidVei = client.getUidVei();
            System.out.println("   uid: " + uidVei.uid());
            System.out.println("   vei: " + uidVei.vei());
            
            // 列出文件
            System.out.println("3. 列出根目录文件...");
            LanzouPageResult result = client.listFiles("-1", 1);
            System.out.println("   文件数量: " + result.files().size());
            System.out.println("   文件夹数量: " + result.folders().size());
            
            if (!result.files().isEmpty()) {
                System.out.println("\n   文件列表:");
                result.files().forEach(f -> 
                    System.out.println("   - " + f.id() + ": " + f.name()));
                
                // 获取第一个文件的直链
                System.out.println("\n4. 测试获取下载直链...");
                LanzouFile firstFile = result.files().getFirst();
                System.out.println("   文件: " + firstFile.name() + " (ID: " + firstFile.id() + ")");
                
                LanzouDirectLink link = client.getFileDownloadLink(firstFile.id());
                System.out.println("   ✓ 获取成功!");
                System.out.println("   直链: " + link.url());
                System.out.println("   过期时间: " + link.expiresAt());
                
                // 验证直链
                System.out.println("\n5. 验证直链...");
                System.out.println("   可以使用以下命令验证:");
                System.out.println("   curl -I -L \"" + link.url() + "\"");
            } else {
                System.out.println("\n   没有文件，无法测试下载直链");
            }
            
        } catch (Exception e) {
            System.out.println("\n✗ 测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
