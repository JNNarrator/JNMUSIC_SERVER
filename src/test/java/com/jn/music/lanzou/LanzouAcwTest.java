package com.jn.music.lanzou;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试acw_sc__v2计算
 */
class LanzouAcwTest {

    @Test
    @DisplayName("测试computeAcwScV2FromHtml")
    void testComputeAcwScV2FromHtml() {
        // 这是从蓝奏云获取的真实反爬页面
        String html = "<html><script>var arg1='837525B2F4600C5AD4E42D7CFF564B57A8A1BCD6';...</script></html>";
        
        System.out.println("=== 测试computeAcwScV2FromHtml ===");
        System.out.println("输入HTML长度: " + html.length());
        
        try {
            String result = LanzouApiClient.computeAcwScV2FromHtml(html);
            System.out.println("✓ 结果: " + result);
            System.out.println("✓ 结果长度: " + result.length());
            assertNotNull(result);
            assertEquals(40, result.length());
        } catch (Exception e) {
            System.out.println("✗ 错误: " + e.getMessage());
            fail("计算失败");
        }
    }
}
