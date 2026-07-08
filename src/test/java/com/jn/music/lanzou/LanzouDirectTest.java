package com.jn.music.lanzou;

import okhttp3.*;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 直接测试OkHttp请求
 */
class LanzouDirectTest {

    @Test
    @DisplayName("直接测试OkHttp请求")
    void testDirectOkHttp() throws IOException {
        System.out.println("=== 直接测试OkHttp请求 ===");
        
        OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(Duration.ofMillis(15000))
            .readTimeout(Duration.ofMillis(30000))
            .followRedirects(true)
            .build();
        
        String url = "https://up.woozooo.com/mlogin.php";
        
        // Step 1: GET without cookie
        System.out.println("Step 1: GET without cookie...");
        Request request1 = new Request.Builder()
            .url(url)
            .header("Referer", "https://pc.woozooo.com")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .build();
        
        try (Response response1 = client.newCall(request1).execute()) {
            String body1 = response1.body().string();
            System.out.println("  Response length: " + body1.length());
            System.out.println("  Contains arg1: " + body1.contains("arg1="));
            
            if (body1.contains("arg1=")) {
                // Extract arg1 and calculate acw_sc__v2
                String acw = LanzouApiClient.computeAcwScV2FromHtml(body1);
                System.out.println("  acw_sc__v2: " + acw);
                
                // Step 2: GET with acw_sc__v2
                System.out.println("\nStep 2: GET with acw_sc__v2...");
                Request request2 = new Request.Builder()
                    .url(url)
                    .header("Referer", "https://pc.woozooo.com")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Cookie", "acw_sc__v2=" + acw)
                    .build();
                
                try (Response response2 = client.newCall(request2).execute()) {
                    String body2 = response2.body().string();
                    System.out.println("  Response length: " + body2.length());
                    System.out.println("  Contains arg1: " + body2.contains("arg1="));
                    System.out.println("  Contains acw_sc__v2: " + body2.contains("acw_sc__v2"));
                    
                    if (!body2.contains("arg1=") && !body2.contains("acw_sc__v2")) {
                        System.out.println("  ✓ Anti-bot bypassed!");
                    } else {
                        System.out.println("  ✗ Anti-bot not bypassed");
                    }
                }
            }
        }
    }
}
