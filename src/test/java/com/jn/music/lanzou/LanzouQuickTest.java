package com.jn.music.lanzou;

import com.jn.music.lanzou.config.LanzouClientProperties;
import com.jn.music.lanzou.dto.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 快速测试 - 验证核心功能
 */
class LanzouQuickTest {

    @Test
    @DisplayName("测试反爬算法")
    void testAcwAlgorithm() {
        System.out.println("=== 测试反爬算法 ===");
        
        // 测试用例1: 32位hex
        String arg1 = "abcdef1234567890abcdef1234567890";
        String result = LanzouApiClient.computeAcwScV2(arg1);
        System.out.println("输入: " + arg1);
        System.out.println("输出: " + result);
        assertEquals(32, result.length());
        
        // 测试用例2: 40位hex (使用unbox)
        String arg2 = "0123456789ABCDEF0123456789ABCDEF01234567";
        String result2 = LanzouApiClient.computeAcwScV2(arg2);
        System.out.println("\n输入: " + arg2);
        System.out.println("输出: " + result2);
        // 40位输入经过unbox后仍然是40位，pairHexXor也返回40位
        assertEquals(40, result2.length());
        
        System.out.println("\n✓ 反爬算法测试通过");
    }

    @Test
    @DisplayName("测试Cookie登录（需要真实Cookie）")
    void testCookieLogin() {
        System.out.println("=== 测试Cookie登录 ===");
        System.out.println("提示: 此测试需要真实Cookie才能运行");
        System.out.println();
        System.out.println("获取Cookie步骤:");
        System.out.println("1. 浏览器访问 https://pc.woozooo.com");
        System.out.println("2. 使用账号 13949121576 登录");
        System.out.println("3. F12 -> Network -> 复制Cookie");
        System.out.println("4. 替换下面的 cookie 变量");
        System.out.println();
        
        // 替换为真实Cookie后取消注释
        /*
        String cookie = "phpdisk_info=真实Cookie值";
        LanzouClientProperties properties = new LanzouClientProperties();
        LanzouApiClient client = new LanzouApiClient(properties);
        client.setSessionCookie(cookie);
        
        LanzouUidVei uidVei = client.getUidVei();
        System.out.println("登录成功!");
        System.out.println("uid: " + uidVei.uid());
        System.out.println("vei: " + uidVei.vei());
        */
    }
}
