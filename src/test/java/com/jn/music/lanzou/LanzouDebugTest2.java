package com.jn.music.lanzou;

import com.jn.music.lanzou.config.LanzouClientProperties;
import okhttp3.*;
import org.junit.jupiter.api.*;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 详细调试测试
 */
class LanzouDebugTest2 {

    @Test
    @DisplayName("详细调试登录流程")
    void testLoginDebugDetailed() throws Exception {
        System.out.println("=== 详细调试登录流程 ===");
        
        LanzouClientProperties properties = new LanzouClientProperties();
        properties.setBaseUrl("https://pc.woozooo.com");
        properties.setShareUrl("https://pan.lanzoui.com");
        properties.setDefaultRootFolderId("-1");
        properties.setConnectTimeoutMs(15000);
        properties.setReadTimeoutMs(30000);
        
        LanzouApiClient client = new LanzouApiClient(properties);
        
        // 手动测试requestGet
        System.out.println("1. 手动测试反爬处理...");
        
        OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(java.time.Duration.ofMillis(15000))
            .readTimeout(java.time.Duration.ofMillis(30000))
            .followRedirects(true)
            .build();
        
        String url = "https://up.woozooo.com/mlogin.php";
        
        // Step 1: GET without cookie
        System.out.println("  Step 1: GET without cookie...");
        Request request1 = new Request.Builder()
            .url(url)
            .header("Referer", "https://pc.woozooo.com")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .build();
        
        try (Response response1 = httpClient.newCall(request1).execute()) {
            String body1 = response1.body().string();
            System.out.println("    Response length: " + body1.length());
            System.out.println("    Contains arg1: " + body1.contains("arg1="));
            
            if (body1.contains("arg1=")) {
                // Extract arg1 and calculate acw_sc__v2
                String acw = LanzouApiClient.computeAcwScV2FromHtml(body1);
                System.out.println("    acw_sc__v2: " + acw);
                
                // Step 2: GET with acw_sc__v2
                System.out.println("\n  Step 2: GET with acw_sc__v2...");
                Request request2 = new Request.Builder()
                    .url(url)
                    .header("Referer", "https://pc.woozooo.com")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Cookie", "acw_sc__v2=" + acw)
                    .build();
                
                try (Response response2 = httpClient.newCall(request2).execute()) {
                    String body2 = response2.body().string();
                    System.out.println("    Response length: " + body2.length());
                    System.out.println("    Contains arg1: " + body2.contains("arg1="));
                    System.out.println("    Contains acw_sc__v2: " + body2.contains("acw_sc__v2"));
                    
                    if (!body2.contains("arg1=") && !body2.contains("acw_sc__v2")) {
                        System.out.println("    ✓ Anti-bot bypassed!");
                    } else {
                        System.out.println("    ✗ Anti-bot not bypassed");
                    }
                }
            }
        }
    }
}
