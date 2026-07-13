package com.jn.music.lanzou;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class LanzouShowDescTest {

    @Autowired
    private LanzouApiClient lanzouClient;

    @Test
    public void showFolderDesc() throws Exception {
        java.lang.reflect.Method method = LanzouApiClient.class.getDeclaredMethod("douploadPost", java.util.Map.class);
        method.setAccessible(true);
        
        var result = method.invoke(lanzouClient, java.util.Map.of(
            "task", "47",
            "folder_id", "-1"
        ));
        
        JsonObject root = JsonParser.parseString(result.toString()).getAsJsonObject();
        JsonArray arr = root.getAsJsonArray("text");
        
        for (JsonElement el : arr) {
            JsonObject o = el.getAsJsonObject();
            String name = o.has("name") ? o.get("name").getAsString() : "";
            String desc = o.has("folder_des") ? o.get("folder_des").getAsString() : "";
            System.out.printf("%-25s | desc: [%s]%n", name, desc);
        }
    }
}
