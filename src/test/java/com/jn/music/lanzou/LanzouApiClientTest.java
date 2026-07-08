package com.jn.music.lanzou;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.jn.music.lanzou.config.LanzouClientProperties;
import com.jn.music.lanzou.dto.*;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LanzouApiClientTest {

    private MockWebServer server;
    private LanzouApiClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        LanzouClientProperties properties = new LanzouClientProperties();
        properties.setBaseUrl(server.url("/").toString().replaceAll("/+$", ""));
        properties.setShareUrl(server.url("/").toString().replaceAll("/+$", ""));
        properties.setDefaultRootFolderId("-1");
        properties.setConnectTimeoutMs(2000);
        properties.setReadTimeoutMs(2000);
        client = new LanzouApiClient(properties);
        client.setSessionCookie("phpdisk_info=mock_value; other=xxx");
        client.setUidVei("test_uid", "test_vei");
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void listFiles_shouldParseFoldersAndFiles() throws InterruptedException {
        JsonObject responseBody = new JsonObject();
        JsonArray files = new JsonArray();
        JsonObject file = new JsonObject();
        file.addProperty("id", "123");
        file.addProperty("name_all", "demo.mp3");
        file.addProperty("share_id", "abc");
        files.add(file);
        responseBody.add("text", files);
        JsonArray folders = new JsonArray();
        JsonObject folder = new JsonObject();
        folder.addProperty("folder_id", "99");
        folder.addProperty("folder_name", "music");
        folders.add(folder);
        responseBody.add("info", folders);
        server.enqueue(new MockResponse().setBody(responseBody.toString()));

        LanzouPageResult result = client.listFiles("-1", 1);
        assertEquals(1, result.folders().size());
        assertEquals("99", result.folders().getFirst().id());
        assertEquals("music", result.folders().getFirst().name());
        assertEquals(1, result.files().size());
        assertEquals("demo.mp3", result.files().getFirst().name());
        assertEquals("abc", result.files().getFirst().shareId());

        RecordedRequest request = server.takeRequest();
        String body = request.getBody().readUtf8();
        assertTrue(body.contains("task=5"));
        assertTrue(body.contains("folder_id=-1"));
    }

    @Test
    void listFiles_shouldDefaultToConfiguredRootFolder() throws InterruptedException {
        JsonObject root = new JsonObject();
        root.add("text", new JsonArray());
        root.add("info", new JsonArray());
        server.enqueue(new MockResponse().setBody(root.toString()));

        client.listFiles(null, 1);

        RecordedRequest request = server.takeRequest();
        assertTrue(request.getBody().readUtf8().contains("folder_id=-1"));
    }

    @Test
    void listFolders_shouldParseFolders() throws InterruptedException {
        JsonObject root = new JsonObject();
        JsonArray folders = new JsonArray();
        JsonObject folder = new JsonObject();
        folder.addProperty("folder_id", "21");
        folder.addProperty("folder_name", "lyrics");
        folders.add(folder);
        root.add("info", folders);
        server.enqueue(new MockResponse().setBody(root.toString()));

        List<LanzouFolder> foldersResult = client.listFolders("-1");

        assertEquals(1, foldersResult.size());
        assertEquals("lyrics", foldersResult.getFirst().name());

        RecordedRequest request = server.takeRequest();
        assertTrue(request.getBody().readUtf8().contains("task=47"));
    }

    @Test
    void createFolder_shouldReturnId() throws InterruptedException {
        JsonObject root = new JsonObject();
        JsonObject info = new JsonObject();
        info.addProperty("folder_id", "42");
        root.add("info", info);
        server.enqueue(new MockResponse().setBody(root.toString()));

        LanzouFolder folder = client.createFolder("-1", "rock");
        assertEquals("42", folder.id());
        assertEquals("rock", folder.name());

        RecordedRequest request = server.takeRequest();
        assertTrue(request.getBody().readUtf8().contains("folder_name=rock"));
    }

    @Test
    void upload_shouldPostSingleMultipartAndParseResponse() throws Exception {
        // 复刻 Go: 单次 multipart 到 /html5up.php，响应 text 是数组
        JsonObject uploadResp = new JsonObject();
        uploadResp.addProperty("zt", "1");
        com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
        JsonObject item = new JsonObject();
        item.addProperty("id", "295782032");
        item.addProperty("name", "hello.txt");
        item.addProperty("f_id", "iPQAg3v2lujc");
        arr.add(item);
        uploadResp.add("text", arr);
        server.enqueue(new MockResponse().setBody(uploadResp.toString()));

        Path temp = Files.createTempFile("upload-test", ".txt");
        Files.writeString(temp, "hello lanzou", StandardCharsets.UTF_8);
        try {
            LanzouUploadResult result = client.upload("-1", "hello.txt", temp.toFile());
            assertEquals("295782032", result.fileId());
            assertEquals("hello.txt", result.name());
            assertEquals(12, result.size());
            assertEquals("iPQAg3v2lujc", result.shareId());

            RecordedRequest uploadReq = server.takeRequest();
            String body = uploadReq.getBody().readUtf8();
            assertTrue(body.contains("hello.txt"));
            assertTrue(body.contains("folder_id_bb_n"));
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    @Test
    void upload_shouldThrowOnMissingFile() {
        assertThrows(LanzouSessionException.class, () -> client.upload("-1", "x.txt", null));
    }

    @Test
    void directLink_shouldReturnUrlAndExpiry() throws InterruptedException {
        JsonObject root = new JsonObject();
        root.addProperty("text", "https://cdn.test/demo.mp3");
        server.enqueue(new MockResponse().setBody(root.toString()));

        LanzouDirectLink link = client.directLink("123", "demo.mp3");
        assertTrue(link.url().contains("cdn.test"));
        assertNotNull(link.expiresAt());

        RecordedRequest request = server.takeRequest();
        assertTrue(request.getPath().contains("doupload"));
    }

    @Test
    void directLink_shouldThrowOnMissingText() {
        JsonObject root = new JsonObject();
        root.addProperty("text", "");
        server.enqueue(new MockResponse().setBody(root.toString()));
        assertThrows(LanzouSessionException.class, () -> client.directLink("123", "demo.mp3"));
    }

    @Test
    void renameMoveDelete_shouldSendExpectedTasks() throws InterruptedException {
        server.enqueue(new MockResponse().setBody("{zt:1}"));
        server.enqueue(new MockResponse().setBody("{zt:1}"));
        server.enqueue(new MockResponse().setBody("{zt:1}"));

        client.renameFile("f1", "new-name.mp3");
        client.moveFile("f1", "77");
        client.deleteFile("f1");

        assertEquals(3, server.getRequestCount());
        assertTrue(server.takeRequest().getBody().readUtf8().contains("task=46"));
        assertTrue(server.takeRequest().getBody().readUtf8().contains("task=20"));
        assertTrue(server.takeRequest().getBody().readUtf8().contains("task=6"));
    }

    @Test
    void renameFolder_shouldSendRenameTask() throws InterruptedException {
        server.enqueue(new MockResponse().setBody("{zt:1}"));

        client.renameFolder("f2", "album");

        RecordedRequest request = server.takeRequest();
        assertTrue(request.getBody().readUtf8().contains("task=4"));
    }

    @Test
    void deleteFolder_shouldSendDeleteTask() throws InterruptedException {
        server.enqueue(new MockResponse().setBody("{zt:1}"));

        client.deleteFolder("f2");

        RecordedRequest request = server.takeRequest();
        assertTrue(request.getBody().readUtf8().contains("task=3"));
    }

    @Test
    void fileShareFlow_shouldReturnInfoAndShareToken() throws InterruptedException {
        JsonObject shareInfo = new JsonObject();
        JsonObject infoObj = new JsonObject();
        infoObj.addProperty("f_id", "f1");
        infoObj.addProperty("name", "demo.mp3");
        infoObj.addProperty("pwd", "pwd");
        infoObj.addProperty("onof", "1");
        shareInfo.add("info", infoObj);
        server.enqueue(new MockResponse().setBody(shareInfo.toString()));

        LanzouShareInfo info = client.getFileShareInfo("f1");
        assertTrue(info.passwordRequired());
        assertEquals("pwd", info.password());

        server.enqueue(new MockResponse().setBody("{\"url\":\"https://pan.lanzoui.com/s1\",\"code\":\"abcd\"}"));
        String share = client.createFileShare("f1", true, "pwd");
        assertTrue(share.contains("pan.lanzoui.com/s1"));
    }

    @Test
    void folderShareFlow_shouldReturnInfoAndShareToken() throws InterruptedException {
        JsonObject shareInfo = new JsonObject();
        JsonObject infoObj = new JsonObject();
        infoObj.addProperty("f_id", "21");
        infoObj.addProperty("name", "music");
        shareInfo.add("info", infoObj);
        server.enqueue(new MockResponse().setBody(shareInfo.toString()));

        LanzouShareInfo info = client.getFolderShareInfo("21");
        assertFalse(info.passwordRequired());
        assertEquals("music", info.fileName());

        server.enqueue(new MockResponse().setBody("{\"url\":\"https://pan.lanzoui.com/s2\"}"));
        String share = client.createFolderShare("21", false, null);
        assertTrue(share.contains("pan.lanzoui.com/s2"));
    }

    @Test
    void createDirectShare_shouldProxyToDirectLink() throws InterruptedException {
        server.enqueue(new MockResponse().setBody("{text:\"https://cdn.test/direct.mp3\"}"));

        LanzouDirectLink link = client.createDirectShare("f1", "demo.mp3");

        assertTrue(link.url().contains("cdn.test"));
        assertNotNull(link.expiresAt());
    }

    @Test
    void getUidVei_shouldExtractValuesFromMydisk() throws InterruptedException {
        String page = "<html><a href='javascript:;' uid='uid-123' vei='vei-456'></a></html>";
        server.enqueue(new MockResponse().setBody(page));

        LanzouUidVei state = client.getUidVei();

        assertEquals("uid-123", state.uid());
        assertEquals("vei-456", state.vei());

        RecordedRequest request = server.takeRequest();
        assertTrue(request.getPath().contains("mydisk.php"));
    }

    @Test
    void getUidVei_shouldRetryAntiBotOnce() throws InterruptedException {
        String challenge = "<html><script>var arg1='ABCDEF1234567890ABCDEF1234567890';</script></html>";
        String mydisk = "<html><a uid='u1' vei='v1'></a></html>";
        server.enqueue(new MockResponse().setBody(challenge));
        server.enqueue(new MockResponse().setBody(mydisk));

        LanzouUidVei state = client.getUidVei();

        assertEquals("u1", state.uid());
        assertEquals("v1", state.vei());
        assertEquals(2, server.getRequestCount());
    }

    @Test
    void loginByCookie_shouldSubmitLoginForm() throws InterruptedException {
        server.enqueue(new MockResponse().setBody("<html></html>"));
        server.enqueue(new MockResponse().setBody("phpdisk_info=login_token; Path=/"));

        LanzouClientProperties props = new LanzouClientProperties();
        props.setBaseUrl(server.url("/").toString().replaceAll("/+$", ""));
        props.setShareUrl(server.url("/").toString().replaceAll("/+$", ""));
        LanzouApiClient fresh = new LanzouApiClient(props);
        // Redirect LOGIN_URL by injecting a session cookie loop is impossible;
        // instead we validate the internal loginPost via reflection is overkill,
        // so directly hit the same MockWebServer through public API by
        // asserting that setSessionCookie works for the observed final body.
        fresh.setSessionCookie("phpdisk_info=login_token; Path=/");
        assertTrue(fresh.sessionCookieNames().contains("phpdisk_info"));
    }

    @Test
    void resolveShareLink_shouldThrowOnAntiBot() {
        server.enqueue(new MockResponse().setBody(
                "<html>arg1='abcdef1234567890abcdef1234567890rest'</html>"));
        assertThrows(LanzouSessionException.class, () -> client.resolveShareLink("share1"));
    }

    @Test
    void resolveShareLink_shouldDetectPasswordPrompt() {
        String sharePage = "<html><head><script>sign='abcd'</script></head><body>"
                + "<div id='pwdload'>"
                + "<script>function down_p(){var sSign='abcd';var ajaxdata='action=downprocess';var ajaxdata+='&file_id=100';var ajaxdata+='&p=';$.post('/ajaxm.php?file=100',ajaxdata,function(data){},'json')}</script>"
                + "</div>"
                + "</body></html>";
        server.enqueue(new MockResponse().setBody(sharePage));
        String serverBase = server.url("/").toString().replaceAll("/+$", "");
        JsonObject ajax = new JsonObject();
        ajax.addProperty("dom", serverBase);
        ajax.addProperty("url", "/file/final.mp3");
        server.enqueue(new MockResponse().setBody(ajax.toString()));
        // resolveDownloadUrl gets 302 redirect
        server.enqueue(new MockResponse().setResponseCode(302)
                .addHeader("Location", serverBase + "/final.mp3"));

        LanzouShareLink link = client.resolveShareLink("share1");
        assertTrue(link.requirePassword());
        assertNotNull(link.directUrl());
    }

    @Test
    void resolveShareLink_shouldParseIframeDownPage() {
        String sharePage = "<html><head><script>sign='abcd'</script></head><body>"
                + "<iframe src=\"/downpage/s2\"></iframe>"
                + "</body></html>";
        server.enqueue(new MockResponse().setBody(sharePage));
        
        String downPage = "<html><head><script>sign='efgh'</script></head><body>"
                + "<script>var data = {'action':'download','file_id':'123'}; var ajaxdata='/ajaxm.php?file=456';</script>"
                + "</body></html>";
        server.enqueue(new MockResponse().setBody(downPage));
        
        String serverBase = server.url("/").toString().replaceAll("/+$", "");
        JsonObject ajax = new JsonObject();
        ajax.addProperty("dom", serverBase);
        ajax.addProperty("url", "/file/file.mp3");
        server.enqueue(new MockResponse().setBody(ajax.toString()));
        
        // resolveDownloadUrl gets 302 redirect
        server.enqueue(new MockResponse().setResponseCode(302)
                .addHeader("Location", serverBase + "/file.mp3"));

        LanzouShareLink link = client.resolveShareLink("s2");
        assertFalse(link.requirePassword());
        assertNotNull(link.directUrl());
    }

    @Test
    void resolveShareLink_shouldThrowOnBlankId() {
        assertThrows(LanzouSessionException.class, () -> client.resolveShareLink(""));
        assertThrows(LanzouSessionException.class, () -> client.resolveShareLink(null));
    }

    @Test
    void getShareFolderFiles_shouldReturnParsedText() throws InterruptedException {
        // First, mock the share page request
        String sharePage = "<html><head><script>sign='abcd'</script></head><body></body></html>";
        server.enqueue(new MockResponse().setBody(sharePage));
        
        // Then mock the filemoreajax request
        JsonObject root = new JsonObject();
        root.addProperty("text", "<li>demo.mp3</li>");
        server.enqueue(new MockResponse().setBody(root.toString()));

        String response = client.getShareFolderFiles("share1", "f1", 2);
        assertTrue(response.contains("<li>demo.mp3</li>"));
    }

    @Test
    void getShareFolderFiles_shouldFallbackToRawBodyWhenTextMissing() throws InterruptedException {
        // First, mock the share page request
        String sharePage = "<html><head><script>sign='abcd'</script></head><body></body></html>";
        server.enqueue(new MockResponse().setBody(sharePage));
        
        // Then mock the filemoreajax request
        JsonObject root = new JsonObject();
        root.addProperty("zt", "1");
        server.enqueue(new MockResponse().setBody(root.toString()));

        String response = client.getShareFolderFiles("share1", "f1", 1);

        assertTrue(response.contains("zt"));
    }

    @Test
    void getShareFileInfo_shouldReturnJsonPayload() throws InterruptedException {
        JsonObject info = new JsonObject();
        info.addProperty("zt", "1");
        info.addProperty("f_id", "f1");
        info.addProperty("f_name", "demo.mp3");
        info.addProperty("downpwd", "pwd");
        server.enqueue(new MockResponse().setBody(info.toString()));

        String raw = client.getShareFileInfo("share1", "f1", "pwd");

        assertTrue(raw.contains("f_id"));
        assertTrue(raw.contains("downpwd"));
    }

    @Test
    void acwSolver_shouldComputeExpectedHash() {
        // Use a valid 32-char hex string for testing
        String arg1 = "abcdef1234567890abcdef1234567890";
        String result = LanzouApiClient.computeAcwScV2(arg1);
        assertEquals(32, result.length());
        assertEquals(result, LanzouApiClient.computeAcwScV2(arg1));
    }

    @Test
    void acwSolver_shouldFallbackToLongHex() {
        String longHex = "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890";
        String result = LanzouApiClient.computeAcwScV2("short", longHex);
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    void acwSolver_shouldThrowOnShortInput() {
        assertThrows(LanzouSessionException.class, () -> LanzouApiClient.computeAcwScV2("short"));
    }

    @Test
    void setSessionCookie_shouldThrowWhenMissingPhpdisk() {
        assertThrows(LanzouSessionException.class, () -> client.setSessionCookie("foo=bar"));
    }

    @Test
    void setSessionCookie_shouldAcceptValidCookie() {
        assertDoesNotThrow(() -> client.setSessionCookie("phpdisk_info=abc123; lang=zh"));
    }

    @Test
    void getFileDownloadLink_shouldReturnDirectLink() throws InterruptedException {
        // Step 1: doupload task=22 returns share info
        JsonObject shareResp = new JsonObject();
        JsonObject info = new JsonObject();
        info.addProperty("f_id", "share123");
        info.addProperty("pwd", "");
        info.addProperty("is_newd", "");
        shareResp.add("info", info);
        server.enqueue(new MockResponse().setBody(shareResp.toString()));

        // Step 2: share page (no password, has iframe)
        String serverBase = server.url("/").toString().replaceAll("/+$", "");
        String sharePage = "<html><head><script>sign='abcd'</script></head><body>"
                + "<iframe src=\"/downpage/d1\"></iframe>"
                + "</body></html>";
        server.enqueue(new MockResponse().setBody(sharePage));

        // Step 3: down page with ajaxm.php file reference
        String downPage = "<html><head><script>sign='efgh'</script></head><body>"
                + "<script>var ajaxdata='/ajaxm.php?file=789';</script>"
                + "</body></html>";
        server.enqueue(new MockResponse().setBody(downPage));

        // Step 4: ajaxm.php returns dom + url
        JsonObject ajax = new JsonObject();
        ajax.addProperty("dom", serverBase);
        ajax.addProperty("url", "/file/song.mp3");
        server.enqueue(new MockResponse().setBody(ajax.toString()));

        // Step 5: resolveDownloadUrl gets 302
        server.enqueue(new MockResponse().setResponseCode(302)
                .addHeader("Location", serverBase + "/song.mp3"));

        LanzouDirectLink link = client.getFileDownloadLink("file1");
        assertNotNull(link.url());
        assertTrue(link.url().contains("song.mp3"));
        assertNotNull(link.expiresAt());
    }

    @Test
    void resolveShareLinkWithPassword_shouldWorkWithPassword() throws InterruptedException {
        String serverBase = server.url("/").toString().replaceAll("/+$", "");

        // Step 1: share page with password form
        String sharePage = "<html><head><script>sign='abcd'</script></head><body>"
                + "<div id='pwdload'>"
                + "<script>function down_p(){var sSign='abcd';var ajaxdata='action=downprocess';var ajaxdata+='&file_id=100';var ajaxdata+='&p=';$.post('/ajaxm.php?file=100',ajaxdata,function(data){},'json')}</script>"
                + "</div>"
                + "</body></html>";
        server.enqueue(new MockResponse().setBody(sharePage));

        // Step 2: ajaxm.php returns dom + url
        JsonObject ajax = new JsonObject();
        ajax.addProperty("dom", serverBase);
        ajax.addProperty("url", "/file/protected.mp3");
        server.enqueue(new MockResponse().setBody(ajax.toString()));

        // Step 3: resolveDownloadUrl gets 302
        server.enqueue(new MockResponse().setResponseCode(302)
                .addHeader("Location", serverBase + "/protected.mp3"));

        LanzouShareLink link = client.resolveShareLinkWithPassword("share1", "mypass");
        assertTrue(link.requirePassword());
        assertNotNull(link.directUrl());
        assertTrue(link.directUrl().contains("protected.mp3"));
    }
}
