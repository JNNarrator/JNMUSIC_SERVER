package com.jn.music.lanzou;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class LanzouRawResponseTest {

    @Autowired
    private LanzouApiClient lanzouClient;

    @Test
    public void testListFoldersRaw() throws Exception {
        // 使用反射调用私有方法来获取原始响应
        java.lang.reflect.Method method = LanzouApiClient.class.getDeclaredMethod("douploadPost", java.util.Map.class);
        method.setAccessible(true);
        
        // task 47 是获取文件夹列表
        var result = method.invoke(lanzouClient, java.util.Map.of(
            "task", "47",
            "folder_id", "-1"
        ));
        
        System.out.println("原始响应:");
        System.out.println(result);
    }
}
