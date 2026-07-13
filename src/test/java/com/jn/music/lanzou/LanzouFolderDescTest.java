package com.jn.music.lanzou;

import com.jn.music.lanzou.dto.LanzouFolder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
public class LanzouFolderDescTest {

    @Autowired
    private LanzouApiClient lanzouClient;

    @Test
    public void testListFoldersWithDesc() {
        // 列出根目录下的文件夹
        List<LanzouFolder> folders = lanzouClient.listFolders("-1");
        System.out.println("文件夹数量: " + folders.size());
        for (LanzouFolder f : folders) {
            System.out.println("ID: " + f.id() + ", Name: " + f.name());
        }
    }
}
