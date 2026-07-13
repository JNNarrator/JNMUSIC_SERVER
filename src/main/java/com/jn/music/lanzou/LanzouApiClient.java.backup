package com.jn.music.lanzou;

import com.google.gson.*;
import com.jn.music.lanzou.config.LanzouClientProperties;
import com.jn.music.lanzou.dto.*;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.*;

/**
 * 基于 OkHttp 4.x 的蓝奏云客户端，完整复刻 Go 参考实现 (lanzou/util.go)。
 *
 * <h3>核心能力</h3>
 * <ul>
 *   <li>账号密码登录（NoRedirect + 反爬重试 5 次）</li>
 *   <li>Cookie 登录态注入</li>
 *   <li>acw_sc__v2 反爬计算（unbox + pairHexXor，与 Go CalcAcwScV2 一致）</li>
 *   <li>通用请求层自动反爬重试（最多 3 次）</li>
 *   <li>管理接口：列文件/列文件夹/创建/删除/重命名/移动</li>
 *   <li>上传（prepare + multipart）</li>
 *   <li>分享页解析 + 下载直链获取（302 / ajax.php 两阶段）</li>
 * </ul>
 */
@Service
public class LanzouApiClient {

    // ==================== 常量 ====================

    private static final String REFERER = "https://pc.woozooo.com";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36";
    private static final String LOGIN_URL = "https://up.woozooo.com/mlogin.php";
    private static final String UPLOAD_URL_PATH = "/html5up.php";
    private static final String MANAGEMENT_BASE = "/doupload.php";
    private static final String AJAXM_PATH = "/ajaxm.php";
    private static final String AJAX_PATH = "/ajax.php";
    private static final String FILE_MORE_AJAX_PATH = "/filemoreajax.php";

    // task IDs
    private static final String TASK_LOGIN = "3";
    private static final String TASK_LIST_FILES = "5";
    private static final String TASK_LIST_FOLDERS = "47";
    private static final String TASK_CREATE_FOLDER = "2";
    private static final String TASK_DELETE_FILE = "6";
    private static final String TASK_DELETE_FOLDER = "3";
    private static final String TASK_RENAME_FILE = "46";
    private static final String TASK_RENAME_FOLDER = "4";
    private static final String TASK_MOVE_FILE = "20";
    private static final String TASK_DIRECT_LINK = "12";
    private static final String TASK_FILE_SHARE_INFO = "22";
    private static final String TASK_FILE_SHARE_PWD = "23";
    private static final String TASK_FOLDER_SHARE_INFO = "18";
    private static final String TASK_FOLDER_SHARE_PWD = "16";

    private static final int MAX_ANTI_BOT_RETRIES = 3;
    private static final int MAX_LOGIN_RETRIES = 5;
    private static final long ANTI_BOT_SLEEP_MS = 800;
    private static final long AJAX_SLEEP_MS = 2000;

    // ==================== 正则 ====================

    private static final Pattern UID_PATTERN = Pattern.compile("uid=['\"]?([^'\"&; ]+)");
    private static final Pattern VEI_PATTERN = Pattern.compile("vei=['\"]?([^'\"&; ]+)");
    private static final Pattern ARG1_PATTERN = Pattern.compile("arg1='([0-9A-Z]+)'");
    private static final Pattern LONG_HEX_PATTERN = Pattern.compile("[0-9A-F]{32,}");
    private static final Pattern JS_DATA_PATTERN = Pattern.compile("data[:\\s]+(\\{[^}]+})");
    private static final Pattern JS_KV_PATTERN = Pattern.compile("'(.+?)':('?([^' },]*)'?)");
    private static final Pattern JS_VAR_FUNC_PATTERN = Pattern.compile("var\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s*=\\s*['\"]?([^;'\"}]+)['\"]?;");
    private static final Pattern FIND_FILE_ID_PATTERN = Pattern.compile("['\"]/ajaxm\\.php\\?file=(\\d+)['\"]");

    private static final Pattern FIND_DOWN_PAGE_PARAM = Pattern.compile("<iframe.*?src=\"([^\"]+)\"");
    private static final Pattern NAME_FIND_PATTERN = Pattern.compile(
            "<title>(.+?) - 蓝奏云</title>|id=\"filenajax\">(.+?)</div>|var filename = '(.+?)';|<div style=\"font-size[^>]*>([^<>].+?)</div>|<div class=\"filethetext\"[^>]*>([^<>]+?)</div>");
    private static final Pattern SIZE_FIND_PATTERN = Pattern.compile("(?i)大小\\W*([0-9.]+\\s*[bkm]+)");
    private static final Pattern IS_FILE_PATTERN = Pattern.compile("class=\"fileinfo\"|id=\"file\"|文件描述");
    private static final Pattern FIND_SUB_FOLDER_PATTERN = Pattern.compile("(?i)(?:folderlink|mbxfolder)[^>]+href=\"/([^\"]+)\"[^>]*(?:class=\"filename\")?>([^<]+)<");

    // acw_sc__v2 密钥
    private static final String ACW_SECRET = "3000176000856006061501533003690027800375";
    private static final int[] UNBOX_TABLE = {6,28,34,31,33,18,30,23,9,8,19,38,17,24,0,5,32,21,10,22,25,14,15,3,16,27,13,35,2,29,11,26,4,36,1,39,37,7,20,12};

    // ==================== 实例字段 ====================

    private final LanzouClientProperties properties;
    private final OkHttpClient httpClient;         // 跟随重定向
    private final OkHttpClient noRedirectClient;   // 不跟随重定向（登录检测302）
    private final Gson gson = new GsonBuilder().create();
    private final Map<String, String> sessionCookies = new ConcurrentHashMap<>();
    private String uid = "";
    private String vei = "";

    // ==================== 构造函数 ====================

    public LanzouApiClient(LanzouClientProperties properties) {
        this.properties = properties;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .readTimeout(Duration.ofMillis(properties.getReadTimeoutMs()))
                .followRedirects(true)
                .followSslRedirects(true)
                .build();

        this.noRedirectClient = httpClient.newBuilder()
                .followRedirects(false)
                .followSslRedirects(false)
                .build();
    }

    // ==================== 会话生命周期（bootstrap / 自动续期）====================

    /** 上次自动登录尝试时间戳（用于冷却）。 */
    private final java.util.concurrent.atomic.AtomicLong lastReloginAt = new java.util.concurrent.atomic.AtomicLong(0);
    /** bootstrap 互斥锁，避免并发续期。 */
    private final Object bootstrapLock = new Object();

    /**
     * 会话初始化：优先复用本地缓存 Cookie，失效则尝试账号密码登录并落盘。
     * <p>幂等：多次调用只有第一次真跑；线程安全。</p>
     * @return true 表示会话可用；false 表示需要人工干预（滑块/凭据错误等）。
     */
    public boolean bootstrap() {
        synchronized (bootstrapLock) {
            // 1) 已经拿到 uid/vei → 认为会话可用
            if (!uid.isEmpty() && !vei.isEmpty()) return true;

            // 2) 尝试加载缓存 Cookie
            if (loadCookieCache()) {
                try {
                    getUidVei();
                    return true;
                } catch (Exception e) {
                    // 缓存失效，落到下面账号密码登录
                    sessionCookies.clear();
                    uid = ""; vei = "";
                }
            }

            // 3) 账号密码自动登录
            if (!properties.isAutoRelogin()) return false;
            if (properties.getUsername() == null || properties.getUsername().isBlank()
                    || properties.getPassword() == null || properties.getPassword().isBlank()) {
                return false;
            }
            long now = System.currentTimeMillis();
            long last = lastReloginAt.get();
            if (last > 0 && now - last < properties.getReloginCooldownMs()) {
                // 冷却中不重复触发
                return false;
            }
            lastReloginAt.set(now);
            try {
                login(properties.getUsername(), properties.getPassword());
                getUidVei();
                saveCookieCache();
                return true;
            } catch (Exception e) {
                sessionCookies.clear();
                uid = ""; vei = "";
                throw new LanzouSessionException(
                        "自动登录失败（可能被反爬拦截或凭据错误）。请浏览器登录 "
                                + "https://pc.woozooo.com 后，把 Cookie 手动写入 "
                                + properties.getCookieCachePath()
                                + " 或调用 client.setSessionCookie(...)。原因: " + e.getMessage(), e);
            }
        }
    }

    /**
     * 使用给定 supplier 执行 API 调用；若判定为会话失效则自动 bootstrap 并重试一次。
     * 供内部包装用。
     */
    private <T> T withAutoRelogin(java.util.function.Supplier<T> action) {
        try {
            return action.get();
        } catch (LanzouSessionException e) {
            if (!isSessionExpired(e)) throw e;
            if (!properties.isAutoRelogin()) throw e;
            // 清掉本地状态，走 bootstrap
            uid = ""; vei = ""; sessionCookies.clear();
            boolean ok = bootstrap();
            if (!ok) throw e;
            return action.get();
        }
    }

    /** 判定异常是否表明会话失效（未登录 / cookie 失效）。 */
    private static boolean isSessionExpired(LanzouSessionException e) {
        String m = e.getMessage();
        if (m == null) return false;
        String s = m.toLowerCase();
        return s.contains("login not") || s.contains("not login")
                || s.contains("extract uid/vei failed")
                || s.contains("missing cookie: phpdisk_info")
                || s.contains("\"zt\":9");
    }

    // ==================== Cookie 持久化 ====================

    /** 把当前 sessionCookies 落盘到 properties.cookieCachePath。 */
    public void saveCookieCache() {
        try {
            String path = properties.getCookieCachePath();
            if (path == null || path.isBlank()) return;
            java.nio.file.Path p = java.nio.file.Paths.get(expandUser(path));
            if (p.getParent() != null) java.nio.file.Files.createDirectories(p.getParent());
            JsonObject obj = new JsonObject();
            sessionCookies.forEach(obj::addProperty);
            java.nio.file.Files.writeString(p, gson.toJson(obj),
                    java.nio.charset.StandardCharsets.UTF_8);
            // 权限收紧：仅当前用户可读写
            try { java.nio.file.Files.setPosixFilePermissions(p,
                    java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")); }
            catch (Throwable ignored) { /* 非 POSIX 文件系统时跳过 */ }
        } catch (Exception e) {
            // 保存失败不阻断主流程
            System.err.println("[lanzou] warn: save cookie cache failed: " + e.getMessage());
        }
    }

    /** 从 properties.cookieCachePath 加载 Cookie；返回 true 表示至少加载到 phpdisk_info。 */
    public boolean loadCookieCache() {
        try {
            String path = properties.getCookieCachePath();
            if (path == null || path.isBlank()) return false;
            java.nio.file.Path p = java.nio.file.Paths.get(expandUser(path));
            if (!java.nio.file.Files.exists(p)) return false;
            String text = java.nio.file.Files.readString(p, java.nio.charset.StandardCharsets.UTF_8);
            JsonObject obj = gson.fromJson(text, JsonObject.class);
            if (obj == null) return false;
            sessionCookies.clear();
            obj.entrySet().forEach(e -> {
                if (e.getValue() != null && e.getValue().isJsonPrimitive()) {
                    sessionCookies.put(e.getKey(), e.getValue().getAsString());
                }
            });
            return sessionCookies.containsKey("phpdisk_info");
        } catch (Exception e) {
            return false;
        }
    }

    private static String expandUser(String path) {
        if (path.startsWith("~/") || path.equals("~")) {
            return System.getProperty("user.home") + path.substring(1);
        }
        return path;
    }

    // ==================== Cookie 管理 ====================

    public void setSessionCookie(String cookieHeader) {
        sessionCookies.clear();
        for (String pair : cookieHeader.split(";")) {
            String[] kv = pair.trim().split("=", 2);
            if (kv.length == 2 && !kv[0].isBlank()) {
                sessionCookies.put(kv[0].trim(), kv[1].trim());
            }
        }
        if (!sessionCookies.containsKey("phpdisk_info")) {
            throw new LanzouSessionException("missing cookie: phpdisk_info");
        }
    }

    public void setAcwScV2(String value) {
        sessionCookies.put("acw_sc__v2", value);
    }

    public Set<String> sessionCookieNames() {
        return Collections.unmodifiableSet(sessionCookies.keySet());
    }

    public String getSessionCookie() {
        return buildCookieHeader("");
    }

    /** 构建 Cookie 请求头；下载类 /file/ 路径自动追加 down_ip=1 */
    private String buildCookieHeader(String path) {
        Map<String, String> merged = new LinkedHashMap<>(sessionCookies);
        StringBuilder sb = new StringBuilder();
        merged.forEach((k, v) -> {
            if (!sb.isEmpty()) sb.append("; ");
            sb.append(k).append('=').append(v);
        });
        if (path != null && path.contains("/file/")) {
            if (!sb.isEmpty()) sb.append("; ");
            sb.append("down_ip=1");
        }
        return sb.toString();
    }

    // ==================== 登录 ====================

    /**
     * 账号密码登录。完整复刻 Go Login()。
     * <ol>
     *   <li>GET 预热登录页（走通用反爬）</li>
     *   <li>POST 表单，NoRedirect 检测 302→mydisk/myfile = 成功</li>
     *   <li>遇 acw_sc__v2 反爬页 → 计算后重试，最多 5 次，每次 sleep 800ms</li>
     * </ol>
     */
    public void login(String username, String password) {
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(password, "password");

        // Step 1: GET 预热，走通用 request（会自动处理反爬）
        requestGet(LOGIN_URL);

        // Step 2: POST 登录，NoRedirect
        String vs = "";
        for (int retry = 0; retry < MAX_LOGIN_RETRIES; retry++) {
            FormBody formBody = new FormBody.Builder()
                    .add("task", TASK_LOGIN)
                    .add("uid", username)
                    .add("pwd", password)
                    .add("setSessionId", "")
                    .add("setSig", "")
                    .add("setScene", "")
                    .add("setTocen", "")
                    .add("formhash", "")
                    .build();

            String cookie = sessionCookies.isEmpty() ? "" : getSessionCookie();
            if (!vs.isEmpty()) {
                cookie = mergeCookieValue(cookie, "acw_sc__v2", vs);
            }

            Request request = new Request.Builder()
                    .url(LOGIN_URL)
                    .header("Referer", REFERER)
                    .header("User-Agent", USER_AGENT)
                    .header("Cookie", cookie)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .post(formBody)
                    .build();

            try (Response response = noRedirectClient.newCall(request).execute()) {
                // 合并 Set-Cookie
                mergeCookiesFromResponse(response);

                // 检查 302 → mydisk/myfile = 登录成功
                if (response.code() >= 300 && response.code() < 400) {
                    String location = response.header("Location");
                    if (isLoginRedirectSuccess(location)) {
                        return;
                    }
                }

                // 检查响应体
                String body = response.body() != null ? response.body().string() : "";

                // 302 但 body 里也可能包含重定向目标
                if (body.toLowerCase().contains("myfile.php") || body.toLowerCase().contains("mydisk.php")) {
                    return;
                }

                // 反爬页
                if (bodyContainsAntiBot(body)) {
                    vs = calcAcwScV2FromBody(body);
                    sleep(ANTI_BOT_SLEEP_MS);
                    continue;
                }

                // JSON 响应
                if (body.trim().startsWith("{")) {
                    JsonObject root = gson.fromJson(body, JsonObject.class);
                    if (root != null && root.has("zt") && root.get("zt").getAsInt() == 1) {
                        return;
                    }
                    String info = str(root, "inf");
                    if (info.isEmpty()) info = str(root, "info");
                    throw new LanzouSessionException("login failed: " + info);
                }

                throw new LanzouSessionException("login failed: unexpected response");
            } catch (IOException e) {
                // OkHttp NoRedirect 可能抛 redirect 异常（Go 也是这么处理的）
                String msg = e.getMessage();
                if (msg != null && msg.toLowerCase().contains("redirect")
                        && (msg.toLowerCase().contains("myfile.php") || msg.toLowerCase().contains("mydisk.php"))) {
                    return;
                }
                throw new LanzouSessionException("login request failed", e);
            }
        }
        throw new LanzouSessionException("登录反爬验证失败，请改用 Cookie 登录");
    }

    /** Cookie 登录的别名，保持向后兼容 */
    public void loginByCookie(String username, String password) {
        login(username, password);
    }

    private boolean isLoginRedirectSuccess(String location) {
        if (location == null) return false;
        String loc = location.toLowerCase();
        return loc.contains("myfile.php") || loc.contains("mydisk.php");
    }

    private void mergeCookiesFromResponse(Response response) {
        for (String setCookie : response.headers("Set-Cookie")) {
            String[] parts = setCookie.split(";")[0].split("=", 2);
            if (parts.length == 2 && !parts[0].isBlank()) {
                sessionCookies.put(parts[0].trim(), parts[1].trim());
            }
        }
    }

    // ==================== uid / vei ====================

    public LanzouUidVei getUidVei() {
        String base = properties.getBaseUrl().replaceAll("/+$", "");
        String url = base + "/mydisk.php?item=files&action=index";
        String body = requestGet(url);

        Matcher uidMatcher = UID_PATTERN.matcher(body);
        Matcher veiMatcher = VEI_PATTERN.matcher(body);
        String uid = uidMatcher.find() ? uidMatcher.group(1) : "";
        String vei = veiMatcher.find() ? veiMatcher.group(1) : "";

        if (uid.isBlank() || vei.isBlank()) {
            // vei 可能在 JS data 中
            if (vei.isBlank()) {
                Map<String, String> data = htmlJsonToMap(body);
                vei = data.getOrDefault("vei", "");
            }
            if (uid.isBlank() || vei.isBlank()) {
                throw new LanzouSessionException("extract uid/vei failed: " + truncate(body));
            }
        }
        this.uid = uid;
        this.vei = vei;
        return new LanzouUidVei(uid, vei);
    }

    // ==================== 管理接口 ====================

    /** 分页列出目录下的文件与文件夹 */
    public LanzouPageResult listFiles(String folderId, int page) {
        ensureUidVei();
        String body = douploadPost(Map.of(
                "task", TASK_LIST_FILES,
                "folder_id", defaultFolder(folderId),
                "pg", String.valueOf(page)));
        JsonObject root = gson.fromJson(body, JsonObject.class);
        return new LanzouPageResult(page, 20, parseFiles(root), parseFolders(root));
    }

    /** 列出文件夹列表 */
    public List<LanzouFolder> listFolders(String parentId) {
        ensureUidVei();
        String body = douploadPost(Map.of(
                "task", TASK_LIST_FOLDERS,
                "folder_id", defaultFolder(parentId)));
        JsonObject root = gson.fromJson(body, JsonObject.class);
        return parseFolders(root);
    }

    public LanzouFolder createFolder(String parentId, String name) {
        ensureUidVei();
        String body = douploadPost(Map.of(
                "task", TASK_CREATE_FOLDER,
                "parent_id", defaultFolder(parentId),
                "folder_name", name,
                "folder_description", ""));
        JsonObject root = gson.fromJson(body, JsonObject.class);
        if (root == null) {
            throw new LanzouSessionException("create folder empty response");
        }
        // 官方响应: zt=1, folder_id 可能位于 info.folder_id, text, 或直接返回 id 字符串
        String folderId = null;
        JsonElement info = root.get("info");
        if (info != null && info.isJsonObject()) {
            JsonElement fi = info.getAsJsonObject().get("folder_id");
            if (fi != null && fi.isJsonPrimitive()) folderId = fi.getAsString();
        }
        if ((folderId == null || folderId.isBlank())) {
            JsonElement text = root.get("text");
            if (text != null && text.isJsonPrimitive()) folderId = text.getAsString();
        }
        if (folderId == null || folderId.isBlank() || "0".equals(folderId)) {
            throw new LanzouSessionException("create folder missing folder id: " + body);
        }
        return new LanzouFolder(folderId, name);
    }

    public void renameFolder(String folderId, String newName) {
        ensureUidVei();
        douploadPost(Map.of("task", TASK_RENAME_FOLDER, "folder_id", folderId, "name", newName));
    }

    public void deleteFolder(String folderId) {
        ensureUidVei();
        douploadPost(Map.of("task", TASK_DELETE_FOLDER, "folder_id", folderId));
    }

    public void renameFile(String fileId, String newName) {
        ensureUidVei();
        douploadPost(Map.of("task", TASK_RENAME_FILE, "file_id", fileId, "file_name", newName));
    }

    public void deleteFile(String fileId) {
        ensureUidVei();
        douploadPost(Map.of("task", TASK_DELETE_FILE, "file_id", fileId));
    }

    public void moveFile(String fileId, String folderId) {
        ensureUidVei();
        douploadPost(Map.of("task", TASK_MOVE_FILE, "file_id", fileId, "folder_id", defaultFolder(folderId)));
    }

    // ==================== 上传 ====================

    public LanzouUploadResult upload(String folderId, String fileName, java.io.File file) {
        if (file == null || !file.exists()) {
            throw new LanzouSessionException("upload file missing or not exists");
        }
        ensureUidVei();

        // 完全复刻 Go: 一次 multipart POST 到 /html5up.php
        RequestBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("task", "1")
                .addFormDataPart("vie", "2")
                .addFormDataPart("ve", "2")
                .addFormDataPart("id", "WU_FILE_0")
                .addFormDataPart("name", fileName)
                .addFormDataPart("folder_id_bb_n", defaultFolder(folderId))
                .addFormDataPart("upload_file", fileName,
                        RequestBody.create(file, MediaType.parse(contentTypeOf(fileName))))
                .build();

        String base = properties.getBaseUrl().replaceAll("/+$", "");
        Request request = new Request.Builder().url(base + UPLOAD_URL_PATH).post(body).build();
        String response = executeWithRetry(request);
        JsonObject root = gson.fromJson(response, JsonObject.class);
        if (root == null || !"1".equals(str(root, "zt"))) {
            throw new LanzouSessionException("upload failed: " + response);
        }
        // 响应 text 是 [{id,name,f_id,...}] 数组
        String uploadedId = null;
        String uploadedShareId = null;
        JsonElement textEl = root.get("text");
        if (textEl != null && textEl.isJsonArray() && !textEl.getAsJsonArray().isEmpty()) {
            JsonObject first = textEl.getAsJsonArray().get(0).getAsJsonObject();
            JsonElement idEl = first.get("id");
            if (idEl != null && idEl.isJsonPrimitive()) uploadedId = idEl.getAsString();
            JsonElement fidEl = first.get("f_id");
            if (fidEl != null && fidEl.isJsonPrimitive()) uploadedShareId = fidEl.getAsString();
        }
        return new LanzouUploadResult(uploadedId, fileName, file.length(), uploadedShareId, null);
    }

    // ==================== 直链 ====================

    public LanzouDirectLink directLink(String fileId, String fileName) {
        ensureUidVei();
        RequestBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("task", TASK_DIRECT_LINK)
                .addFormDataPart("file_id", fileId)
                .addFormDataPart("name", fileName)
                .build();
        String response = douploadPost(body);
        JsonObject root = gson.fromJson(response, JsonObject.class);
        String downUrl = Optional.ofNullable(root.get("text")).map(JsonElement::getAsString)
                .filter(s -> !s.isBlank())
                .orElseThrow(() -> new LanzouSessionException("direct link missing url: " + response));
        return new LanzouDirectLink(downUrl, Instant.now().plus(Duration.ofHours(4)));
    }


    /** 创建直链分享（别名，向后兼容） */
    public LanzouDirectLink createDirectShare(String fileId, String fileName) {
        return directLink(fileId, fileName);
    }

    // ==================== 分享管理 =====================

    public LanzouShareInfo getFileShareInfo(String fileId) {
        ensureUidVei();
        String body = douploadPost(Map.of("task", TASK_FILE_SHARE_INFO, "file_id", fileId));
        return parseShareInfo(body);
    }

    public String createFileShare(String fileId, boolean enablePassword, String password) {
        ensureUidVei();
        return douploadPost(Map.of(
                "task", TASK_FILE_SHARE_PWD,
                "file_id", fileId,
                "shows", enablePassword ? "1" : "0",
                "passwd", emptyIfNull(password)));
    }

    public LanzouShareInfo getFolderShareInfo(String folderId) {
        ensureUidVei();
        String body = douploadPost(Map.of("task", TASK_FOLDER_SHARE_INFO, "file_id", folderId));
        return parseShareInfo(body);
    }

    public String createFolderShare(String folderId, boolean enablePassword, String password) {
        ensureUidVei();
        return douploadPost(Map.of(
                "task", TASK_FOLDER_SHARE_PWD,
                "folder_id", folderId,
                "shows", enablePassword ? "1" : "0",
                "passwd", emptyIfNull(password)));
    }

    // ==================== 分享页解析 + 下载直链（核心！）====================

    /**
     * 解析分享页并获取下载直链。完整复刻 Go getFilesByShareUrl()。
     * <ol>
     *   <li>GET 分享页 HTML</li>
     *   <li>判断是否有密码表单 → 提取参数 POST ajaxm.php</li>
     *   <li>无密码 → iframe 下载页 → 提取参数 POST ajaxm.php</li>
     *   <li>得到 downloadUrl = dom + "/file/" + url</li>
     *   <li>NoRedirect GET downloadUrl → 302 Location 即为直链</li>
     *   <li>若非 302 → 再 POST ajax.php 得到 url</li>
     * </ol>
     */
    public LanzouShareLink resolveShareLink(String shareId) {
        if (shareId == null || shareId.isBlank()) {
            throw new LanzouSessionException("share id must not be blank");
        }

        String base = properties.getShareUrl().replaceAll("/+$", "");

        // Step 1: GET 分享页
        String sharePageData = getSharePageHtml(base + "/" + shareId);
        sharePageData = removeNotes(sharePageData);

        boolean requirePassword = sharePageData.contains("pwdload") || sharePageData.contains("passwddiv");

        String downloadUrl;
        String refererBase;
        String fileName = "";

        if (requirePassword) {
            // 有密码表单 → 提取 down_p 函数
            String downPFunc = getJSFunctionByName(sharePageData, "down_p");
            Map<String, String> param = htmlJsonToMap(downPFunc);
            param.put("p", ""); // 密码由调用者提供
            Matcher fileIdMatcher = FIND_FILE_ID_PATTERN.matcher(downPFunc);
            if (!fileIdMatcher.find()) {
                throw new LanzouSessionException("not find file id in down_p");
            }
            String fileId = fileIdMatcher.group(1);

            // POST ajaxm.php
            String ajaxBody = requestPost(base + AJAXM_PATH + "?file=" + fileId, param);
            JsonObject ajaxRoot = gson.fromJson(ajaxBody, JsonObject.class);
            String dom = str(ajaxRoot, "dom");
            String url = str(ajaxRoot, "url");
            fileName = str(ajaxRoot, "inf");
            refererBase = dom + "/file";
            downloadUrl = refererBase + "/" + url;
        } else {
            // 无密码 → iframe 下载页
            Matcher iframeMatcher = FIND_DOWN_PAGE_PARAM.matcher(sharePageData);
            if (!iframeMatcher.find()) {
                throw new LanzouSessionException("not find file page param");
            }
            String iframePath = iframeMatcher.group(1);
            String nextPageData = requestGet(base + iframePath);
            nextPageData = removeNotes(nextPageData);

            Map<String, String> param = htmlJsonToMap(nextPageData);
            Matcher fileIdMatcher = FIND_FILE_ID_PATTERN.matcher(nextPageData);
            if (!fileIdMatcher.find()) {
                throw new LanzouSessionException("not find file id");
            }
            String ajaxUrl = base + AJAXM_PATH + "?file=" + fileIdMatcher.group(1);
            String ajaxBody = requestPost(ajaxUrl, param);
            JsonObject ajaxRoot = gson.fromJson(ajaxBody, JsonObject.class);
            String dom = str(ajaxRoot, "dom");
            String url = str(ajaxRoot, "url");
            refererBase = dom + "/file";
            downloadUrl = refererBase + "/" + url;

            // 从原分享页提取文件名
            Matcher nameMatcher = NAME_FIND_PATTERN.matcher(sharePageData);
            while (nameMatcher.find()) {
                for (int i = 1; i <= nameMatcher.groupCount(); i++) {
                    String g = nameMatcher.group(i);
                    if (g != null && !g.isEmpty()) {
                        fileName = g;
                        break;
                    }
                }
                if (!fileName.isEmpty()) break;
            }
        }

        // Step 2: NoRedirect GET downloadUrl → 302 = 直链
        String directUrl = resolveDownloadUrl(downloadUrl, refererBase);

        return new LanzouShareLink(shareId, base + "/" + shareId, directUrl, requirePassword);
    }

    /**
     * 带密码的分享页解析。
     */
    public LanzouShareLink resolveShareLinkWithPassword(String shareId, String password) {
        if (shareId == null || shareId.isBlank()) {
            throw new LanzouSessionException("share id must not be blank");
        }
        String base = properties.getShareUrl().replaceAll("/+$", "");
        String sharePageData = getSharePageHtml(base + "/" + shareId);
        sharePageData = removeNotes(sharePageData);
        sharePageData = removeJSComment(sharePageData);

        if (!sharePageData.contains("pwdload") && !sharePageData.contains("passwddiv")) {
            // 无需密码，走普通流程
            return resolveShareLink(shareId);
        }

        String downPFunc = getJSFunctionByName(sharePageData, "down_p");
        Map<String, String> param = htmlJsonToMap(downPFunc);
        param.put("p", password);
        Matcher fileIdMatcher = FIND_FILE_ID_PATTERN.matcher(downPFunc);
        if (!fileIdMatcher.find()) {
            throw new LanzouSessionException("not find file id in down_p");
        }
        String fileId = fileIdMatcher.group(1);

        String ajaxBody = requestPost(base + AJAXM_PATH + "?file=" + fileId, param);
        JsonObject ajaxRoot = gson.fromJson(ajaxBody, JsonObject.class);
        String dom = str(ajaxRoot, "dom");
        String url = str(ajaxRoot, "url");
        String refererBase = dom + "/file";
        String downloadUrl = refererBase + "/" + url;

        String directUrl = resolveDownloadUrl(downloadUrl, refererBase);
        return new LanzouShareLink(shareId, base + "/" + shareId, directUrl, true);
    }

    /**
     * 获取分享目录中的文件（分页）。复刻 Go filemoreajax.php。
     */
    public String getShareFolderFiles(String shareId, String folderId, int page) {
        String base = properties.getShareUrl().replaceAll("/+$", "");
        String sharePageData = getSharePageHtml(base + "/" + shareId);
        Map<String, String> param = htmlJsonToMap(sharePageData);
        param.put("folder", folderId);
        param.put("pg", String.valueOf(page));
        return requestPost(base + FILE_MORE_AJAX_PATH, param);
    }

    /**
     * 获取分享文件的详细信息（ajaxm.php）。复刻 Go GetFilesByShareUrl。
     */
    public String getShareFileInfo(String shareId, String fileId, String password) {
        String base = properties.getShareUrl().replaceAll("/+$", "");
        Map<String, String> param = new LinkedHashMap<>();
        param.put("p", emptyIfNull(password));
        String ajaxBody = requestPost(base + AJAXM_PATH + "?file=" + fileId, param);
        return ajaxBody;
    }

    /**
     * 根据文件 ID 获取下载直链（管理态）。复刻 Go GetDownloadLink。
     * <ol>
     *   <li>doupload.php task=22 获取分享信息（f_id / pwd）</li>
     *   <li>解析分享页 → ajaxm.php → 下载地址</li>
     *   <li>NoRedirect → 302 直链 或 ajax.php 兜底</li>
     * </ol>
     */
    public LanzouDirectLink getFileDownloadLink(String fileId) {
        ensureUidVei();
        // Step 1: 获取文件分享信息
        String shareBody = douploadPost(Map.of("task", TASK_FILE_SHARE_INFO, "file_id", fileId));
        JsonObject shareRoot = gson.fromJson(shareBody, JsonObject.class);
        JsonObject info = shareRoot.has("info") ? shareRoot.getAsJsonObject("info") : new JsonObject();
        String shareId = str(info, "f_id");
        String pwd = str(info, "pwd");
        if (shareId.isEmpty()) {
            throw new LanzouSessionException("file share info missing f_id: " + shareBody);
        }

        // Step 2: 解析分享页 - 使用 is_newd 字段的域名
        String base = str(info, "is_newd");
        if (base.isEmpty()) {
            base = properties.getShareUrl().replaceAll("/+$", "");
        } else {
            base = base.replaceAll("/+$", "");
        }
        String sharePageData = getSharePageHtml(base + "/" + shareId);
        sharePageData = removeNotes(sharePageData);
        sharePageData = removeJSComment(sharePageData);

        String downloadUrl;
        String refererBase;

        if (sharePageData.contains("pwdload") || sharePageData.contains("passwddiv")) {
            String downPFunc = getJSFunctionByName(sharePageData, "down_p");
            Map<String, String> param = htmlJsonToMap(downPFunc);
            param.put("p", pwd);
            Matcher fm = FIND_FILE_ID_PATTERN.matcher(downPFunc);
            if (!fm.find()) throw new LanzouSessionException("not find file id in down_p");
            String ajaxBody = requestPost(base + AJAXM_PATH + "?file=" + fm.group(1), param);
            JsonObject ajaxRoot = gson.fromJson(ajaxBody, JsonObject.class);
            refererBase = str(ajaxRoot, "dom") + "/file";
            downloadUrl = refererBase + "/" + str(ajaxRoot, "url");
        } else {
            Matcher iframeMatcher = FIND_DOWN_PAGE_PARAM.matcher(sharePageData);
            if (!iframeMatcher.find()) throw new LanzouSessionException("not find file page param");
            String nextPageData = requestGet(base + iframeMatcher.group(1));
            nextPageData = removeNotes(nextPageData);
            Map<String, String> param = htmlJsonToMap(nextPageData);
            Matcher fm = FIND_FILE_ID_PATTERN.matcher(nextPageData);
            if (!fm.find()) throw new LanzouSessionException("not find file id");
            String ajaxBody = requestPost(base + AJAXM_PATH + "?file=" + fm.group(1), param);
            JsonObject ajaxRoot = gson.fromJson(ajaxBody, JsonObject.class);
            refererBase = str(ajaxRoot, "dom") + "/file";
            downloadUrl = refererBase + "/" + str(ajaxRoot, "url");
        }

        // Step 3: 解析直链
        String directUrl = resolveDownloadUrl(downloadUrl, refererBase);
        return new LanzouDirectLink(directUrl, Instant.now().plus(Duration.ofHours(4)));
    }

    // ==================== 下载直链解析（核心）====================

    /**
     * 解析下载直链。完整复刻 Go 的 302 / ajax.php 两阶段逻辑。
     */
    private String resolveDownloadUrl(String downloadUrl, String refererBase) {
        String vs = sessionCookies.getOrDefault("acw_sc__v2", "");
        String bodyStr = "";

        for (int i = 0; i < MAX_ANTI_BOT_RETRIES; i++) {
            Request.Builder builder = new Request.Builder()
                    .url(downloadUrl)
                    .get()
                    .header("accept-language", "zh-CN,zh;q=0.9")
                    .header("Referer", refererBase)
                    .header("User-Agent", USER_AGENT)
                    .header("Cookie", "down_ip=1" + (vs.isEmpty() ? "" : "; acw_sc__v2=" + vs));

            try (Response response = noRedirectClient.newCall(builder.build()).execute()) {
                if (response.code() == 302) {
                    String location = response.header("Location");
                    if (location != null && !location.isEmpty()) {
                        return location;
                    }
                }

                // 非 302 → 读 body
                bodyStr = response.body() != null ? response.body().string() : "";
                if (bodyContainsAntiBot(bodyStr)) {
                    vs = calcAcwScV2FromBody(bodyStr);
                    continue;
                }
                break;
            } catch (IOException e) {
                // OkHttp 可能对 redirect 抛异常
                throw new LanzouSessionException("resolve download url failed", e);
            }
        }

        // 非 302 → ajax.php 兜底
        Map<String, String> param = htmlJsonToMap(bodyStr);
        param.put("el", "2");
        sleep(AJAX_SLEEP_MS);

        vs = sessionCookies.getOrDefault("acw_sc__v2", "");
        for (int i = 0; i < MAX_ANTI_BOT_RETRIES; i++) {
            String cookie = "down_ip=1" + (vs.isEmpty() ? "" : "; acw_sc__v2=" + vs);
            FormBody.Builder formBuilder = new FormBody.Builder();
            param.forEach(formBuilder::add);
            Request ajaxReq = new Request.Builder()
                    .url(refererBase + AJAX_PATH)
                    .header("Referer", REFERER)
                    .header("User-Agent", USER_AGENT)
                    .header("Cookie", cookie)
                    .post(formBuilder.build())
                    .build();
            String ajaxBody = executeWithRetry(ajaxReq);
            if (bodyContainsAntiBot(ajaxBody)) {
                vs = calcAcwScV2FromBody(ajaxBody);
                sleep(AJAX_SLEEP_MS);
                continue;
            }
            JsonObject root = gson.fromJson(ajaxBody, JsonObject.class);
            if (root != null && root.has("url")) {
                return root.get("url").getAsString();
            }
            throw new LanzouSessionException("ajax.php missing url: " + ajaxBody);
        }
        throw new LanzouSessionException("anti-bot retries exceeded for download");
    }

    // ==================== 分享页 HTML 获取（带反爬重试）====================

    private String getSharePageHtml(String url) {
        // 完整复刻 Go 的 request 反爬循环：每次基于本次响应重算 acw_sc__v2，最多 3 次
        String vs = "";
        Map<String, String> extraCookies = new LinkedHashMap<>();
        for (int i = 0; i < MAX_ANTI_BOT_RETRIES; i++) {
            Request.Builder builder = new Request.Builder().url(url).get()
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", REFERER)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "zh-CN,zh;q=0.9");
            StringBuilder cookieStr = new StringBuilder();
            for (Map.Entry<String, String> e : extraCookies.entrySet()) {
                if (cookieStr.length() > 0) cookieStr.append("; ");
                cookieStr.append(e.getKey()).append("=").append(e.getValue());
            }
            if (!vs.isEmpty()) {
                if (cookieStr.length() > 0) cookieStr.append("; ");
                cookieStr.append("acw_sc__v2=").append(vs);
            }
            if (cookieStr.length() > 0) {
                builder.header("Cookie", cookieStr.toString());
            }
            try (Response response = httpClient.newCall(builder.build()).execute()) {
                // 收集响应中 Set-Cookie 里的 acw_tc / cdn_sec_tc
                for (String h : response.headers("Set-Cookie")) {
                    int eq = h.indexOf('=');
                    int sc = h.indexOf(';');
                    if (eq > 0) {
                        String k = h.substring(0, eq).trim();
                        String v = h.substring(eq + 1, sc > 0 ? sc : h.length()).trim();
                        if (!k.isEmpty()) extraCookies.put(k, v);
                    }
                }
                String body = response.body() != null ? response.body().string() : "";
                if (body.contains("取消分享")) throw new LanzouSessionException("file share cancelled");
                if (body.contains("文件不存在")) throw new LanzouSessionException("file not exist");
                if (bodyContainsAntiBot(body)) {
                    vs = computeAcwScV2FromHtml(body);
                    sleep(ANTI_BOT_SLEEP_MS);
                    continue;
                }
                return body;
            } catch (IOException e) {
                throw new LanzouSessionException("share page request failed: " + url, e);
            }
        }
        throw new LanzouSessionException("acw_sc__v2 validation error on share page");
    }

    // ==================== 通用请求层 ====================

    /** GET 请求，自动反爬重试（最多 3 次） */
    private String requestGet(String url) {
        String vs = "";
        for (int i = 0; i < MAX_ANTI_BOT_RETRIES; i++) {
            Request.Builder builder = new Request.Builder().url(url).get()
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", REFERER);
            String cookie = buildCookieHeader(url);
            if (!vs.isEmpty()) {
                cookie = mergeCookieValue(cookie, "acw_sc__v2", vs);
            }
            if (!cookie.isEmpty()) {
                builder.header("Cookie", cookie);
            }
            Request request = builder.build();

            try (Response response = httpClient.newCall(request).execute()) {
                mergeCookiesFromResponse(response);
                if (response.body() == null) throw new LanzouSessionException("empty response");
                String body = response.body().string();
                if (!response.isSuccessful()) {
                    throw new LanzouSessionException("http " + response.code() + ": " + truncate(body));
                }
                if (bodyContainsAntiBot(body)) {
                    vs = calcAcwScV2FromBody(body);
                    continue;
                }
                return body;
            } catch (IOException e) {
                throw new LanzouSessionException("request failed: " + url, e);
            }
        }
        throw new LanzouSessionException("acw_sc__v2 validation error");
    }

    /** POST form 请求，自动反爬重试 */
    private String requestPost(String url, Map<String, String> form) {
        String vs = "";
        for (int i = 0; i < MAX_ANTI_BOT_RETRIES; i++) {
            FormBody.Builder formBuilder = new FormBody.Builder();
            form.forEach(formBuilder::add);
            Request.Builder builder = new Request.Builder().url(url).post(formBuilder.build())
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", REFERER);
            String cookie = buildCookieHeader(url);
            if (!vs.isEmpty()) {
                cookie = mergeCookieValue(cookie, "acw_sc__v2", vs);
            }
            if (!cookie.isEmpty()) {
                builder.header("Cookie", cookie);
            }
            Request request = builder.build();
            try (Response response = httpClient.newCall(request).execute()) {
                mergeCookiesFromResponse(response);
                if (response.body() == null) throw new LanzouSessionException("empty response");
                String body = response.body().string();
                if (!response.isSuccessful()) {
                    throw new LanzouSessionException("http " + response.code() + ": " + truncate(body));
                }
                if (bodyContainsAntiBot(body)) {
                    vs = calcAcwScV2FromBody(body);
                    continue;
                }
                return body;
            } catch (IOException e) {
                throw new LanzouSessionException("request failed: " + url, e);
            }
        }
        throw new LanzouSessionException("acw_sc__v2 validation error");
    }

    /** doupload.php POST（带 uid/vei query），自动反爬 */
    private String douploadPost(Map<String, String> form) {
        String base = properties.getBaseUrl().replaceAll("/+$", "");
        String url = base + MANAGEMENT_BASE + "?uid=" + uid + "&vei=" + vei;
        return requestPost(url, form);
    }

    private String douploadPost(RequestBody body) {
        String base = properties.getBaseUrl().replaceAll("/+$", "");
        String url = base + MANAGEMENT_BASE + "?uid=" + uid + "&vei=" + vei;
        Request request = new Request.Builder().url(url).post(body).build();
        return executeWithRetry(request);
    }

    /** 执行请求并反爬重试。使用手动 sessionCookies 而非 OkHttp cookie jar（我们已移除 jar）。 */
    private String executeWithRetry(Request request) {
        String vs = "";
        for (int i = 0; i < MAX_ANTI_BOT_RETRIES; i++) {
            Request.Builder builder = request.newBuilder()
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", REFERER);
            String cookie = buildCookieHeader(request.url().toString());
            if (!vs.isEmpty()) {
                cookie = mergeCookieValue(cookie, "acw_sc__v2", vs);
            }
            if (!cookie.isEmpty()) {
                builder.header("Cookie", cookie);
            }
            Request req = builder.build();
            String body = executeString(req);
            if (bodyContainsAntiBot(body)) {
                vs = calcAcwScV2FromBody(body);
                continue;
            }
            return body;
        }
        throw new LanzouSessionException("acw_sc__v2 validation error");
    }

    private String executeString(Request request) {
        try {
            // OkHttp 会在 interceptor 中自动添加 cookie jar 中的 cookie
            // 但我们需要在发送前检查实际的请求头
            Response response = httpClient.newCall(request).execute();
            if (response.body() == null) throw new LanzouSessionException("empty response");
            String body = response.body().string();
            if (body.contains("arg1=")) {
            }
            if (!response.isSuccessful()) {
                throw new LanzouSessionException("http " + response.code() + ": " + truncate(body));
            }
            return body;
        } catch (IOException e) {
            throw new LanzouSessionException("request failed: " + request.url(), e);
        }
    }

    // ==================== acw_sc__v2 计算 ====================

    /**
     * 从 HTML 提取 arg1 并计算 acw_sc__v2。复刻 Go CalcAcwScV2。
     */
    public static String computeAcwScV2FromHtml(String html) {
        Matcher matcher = ARG1_PATTERN.matcher(html);
        if (!matcher.find()) {
            throw new LanzouSessionException("cannot match arg1 from html");
        }
        String arg1 = matcher.group(1);
        String u = unbox(arg1);

        // 候选 1: pairHexXor
        String candidate = pairHexXor(u, ACW_SECRET);
        if (!candidate.isEmpty()) return candidate;

        // 候选 2: 其他长 hex
        Matcher hexMatcher = LONG_HEX_PATTERN.matcher(html);
        while (hexMatcher.find()) {
            String hex = hexMatcher.group();
            if (!hex.equals(arg1) && hex.length() == u.length()) {
                candidate = pairHexXor(u, hex);
                if (!candidate.isEmpty()) return candidate;
            }
        }

        // 候选 3: 整段 hexXor 兜底
        try {
            candidate = hexXor(u, ACW_SECRET);
            if (!candidate.isEmpty()) return candidate;
        } catch (Exception ignored) {}

        throw new LanzouSessionException("acw_sc__v2 calculation failed");
    }

    /**
     * 直接用 arg1 计算（兼容旧接口签名）。
     * arg1 是 32 位 hex → unbox（32位直通） + pairHexXor。
     */
    public static String computeAcwScV2(String arg1) {
        if (arg1 == null || arg1.length() < 32) {
            throw new LanzouSessionException("arg1 too short: " + (arg1 == null ? "null" : arg1.length()));
        }
        String u = unbox(arg1);
        String result = pairHexXor(u, ACW_SECRET);
        if (result.isEmpty()) {
            throw new LanzouSessionException("acw_sc__v2 calculation failed for arg1");
        }
        return result;
    }

    /** 兼容旧签名：computeAcwScV2(arg1, longHex) */
    public static String computeAcwScV2(String arg1, String longHex) {
        if (arg1 != null && !arg1.isEmpty() && arg1.length() >= 32) {
            return computeAcwScV2(arg1);
        }
        if (longHex != null && !longHex.isEmpty()) {
            String u = unbox(longHex);
            String result = pairHexXor(u, ACW_SECRET);
            if (!result.isEmpty()) return result;
        }
        throw new LanzouSessionException("acw_sc__v2 calculation failed");
    }

    /** 从响应体提取 arg1 并计算 acw_sc__v2，同时更新 sessionCookies */
    private String calcAcwScV2FromBody(String body) {
        String acw = computeAcwScV2FromHtml(body);
        sessionCookies.put("acw_sc__v2", acw);
        return acw;
    }

    // ==================== acw 底层算法 ====================

    private static String unbox(String hex) {
        if (hex.length() <= 32) return hex;
        // 40 字符用 UNBOX_TABLE 置换
        if (hex.length() == 40) {
            char[] result = new char[40];
            for (int i = 0; i < UNBOX_TABLE.length && i < hex.length(); i++) {
                result[UNBOX_TABLE[i]] = hex.charAt(i);
            }
            return new String(result);
        }
        return hex;
    }

    private static String pairHexXor(String u, String secret) {
        StringBuilder sb = new StringBuilder();
        int len = Math.min(u.length(), secret.length());
        for (int x = 0; x + 2 <= len; x += 2) {
            int a = Integer.parseInt(u.substring(x, x + 2), 16);
            int b = Integer.parseInt(secret.substring(x, x + 2), 16);
            String h = Integer.toHexString(a ^ b);
            if (h.length() == 1) h = "0" + h;
            sb.append(h);
        }
        return sb.toString();
    }

    private static String hexXor(String hex1, String hex2) {
        byte[] bytes1 = hexToBytes(hex1);
        byte[] bytes2 = hexToBytes(hex2);
        int minLen = Math.min(bytes1.length, bytes2.length);
        byte[] result = new byte[minLen];
        for (int i = 0; i < minLen; i++) {
            result[i] = (byte) (bytes1[i] ^ bytes2[i]);
        }
        return bytesToHex(result);
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    // ==================== HTML / JS 解析 ====================

    /** 去除 HTML 注释 */
    private static String removeNotes(String html) {
        return html.replaceAll("<!--.*?-->", "\n")
                .replaceAll("(?<!:)//.*", "");
    }

    /** 去除 JS 注释 */
    private static String removeJSComment(String data) {
        StringBuilder result = new StringBuilder();
        boolean inBlockComment = false;
        boolean inLineComment = false;
        for (int i = 0; i < data.length(); i++) {
            char c = data.charAt(i);
            if (inLineComment && (c == '\n' || c == '\r')) {
                inLineComment = false;
                result.append(c);
                continue;
            }
            if (inBlockComment && c == '*' && i + 1 < data.length() && data.charAt(i + 1) == '/') {
                inBlockComment = false;
                i++;
                continue;
            }
            if (inBlockComment || inLineComment) continue;
            if (c == '/' && i + 1 < data.length()) {
                char next = data.charAt(i + 1);
                if (next == '*') { inBlockComment = true; i++; continue; }
                if (next == '/') { inLineComment = true; i++; continue; }
            }
            result.append(c);
        }
        return result.toString();
    }

    /** 从 HTML 中提取 data: {...} 并解析成 key-value */
    private static Map<String, String> htmlJsonToMap(String html) {
        Matcher dataMatcher = JS_DATA_PATTERN.matcher(html);
        if (!dataMatcher.find()) {
            return new LinkedHashMap<>();
        }
        String dataStr = dataMatcher.group(1);
        return jsonToMap(dataStr, html);
    }

    private static Map<String, String> jsonToMap(String data, String html) {
        Map<String, String> param = new LinkedHashMap<>();
        Matcher kvMatcher = JS_KV_PATTERN.matcher(data);
        while (kvMatcher.find()) {
            String k = kvMatcher.group(1);
            String fullValue = kvMatcher.group(2);
            String v = kvMatcher.group(3);
            if (v == null) v = "";
            // 如果值不是引号包裹的字面量且不是纯数字，当成 JS 变量去查找
            if (!fullValue.contains("'") && !isNumber(fullValue)) {
                String resolved = findJSVarFunc(fullValue, html);
                if (!resolved.isEmpty()) v = resolved;
            }
            param.put(k, v);
        }
        return param;
    }

    /** 查找 JS 变量值 */
    private static String findJSVarFunc(String key, String html) {
        Matcher m = Pattern.compile("var\\s+" + Pattern.quote(key) + "\\s*=\\s*['\"]?([^;'\"}]+)['\"]?;").matcher(html);
        return m.find() ? m.group(1) : "";
    }

    /** 按名称查找 JS 函数体 */
    private static String getJSFunctionByName(String html, String name) {
        Pattern fnPattern = Pattern.compile("(?ims)function\\s+" + Pattern.quote(name) + "\\s*\\([^)]*\\)\\s*\\{");
        Matcher fnMatcher = fnPattern.matcher(html);
        if (!fnMatcher.find()) {
            throw new LanzouSessionException("not find " + name + " function");
        }
        int start = fnMatcher.start();
        int braceStart = fnMatcher.end() - 1;
        int count = 1;
        int i = braceStart + 1;
        while (i < html.length() && count > 0) {
            char c = html.charAt(i);
            if (c == '{') count++;
            else if (c == '}') count--;
            i++;
        }
        return html.substring(start, i);
    }

    private static boolean isNumber(String s) {
        if (s == null || s.isEmpty()) return false;
        for (char c : s.toCharArray()) {
            if (!Character.isDigit(c)) return false;
        }
        return true;
    }

    // ==================== 辅助方法 ====================

    /** 手动设置 uid/vei（用于测试或已知值时绕过 mydisk.php 请求） */
    public void setUidVei(String uid, String vei) {
        this.uid = Objects.requireNonNull(uid, "uid");
        this.vei = Objects.requireNonNull(vei, "vei");
    }

    private void ensureUidVei() {
        if (!uid.isEmpty() && !vei.isEmpty()) return;
        try {
            getUidVei();
            return;
        } catch (LanzouSessionException e) {
            // uid/vei 抓不到 → 会话失效 → 尝试 bootstrap（走缓存/自动登录）
            if (!properties.isAutoRelogin()) throw e;
            sessionCookies.clear();
            boolean ok = bootstrap();
            if (!ok) throw e;
            // bootstrap 内部已经调用了 getUidVei，成功即返回
        }
    }

    private String defaultFolder(String folderId) {
        return folderId != null && !folderId.isEmpty() ? folderId : properties.getDefaultRootFolderId();
    }

    private static boolean bodyContainsAntiBot(String html) {
        return html != null && (html.contains("acw_sc__v2") || html.contains("arg1="));
    }

    private static String mergeCookieValue(String existing, String key, String value) {
        Map<String, String> map = new LinkedHashMap<>();
        if (existing != null && !existing.isEmpty()) {
            for (String pair : existing.split(";")) {
                String[] kv = pair.trim().split("=", 2);
                if (kv.length == 2) map.put(kv[0].trim(), kv[1].trim());
            }
        }
        map.put(key, value);
        StringBuilder sb = new StringBuilder();
        map.forEach((k, v) -> {
            if (!sb.isEmpty()) sb.append("; ");
            sb.append(k).append('=').append(v);
        });
        return sb.toString();
    }

    private LanzouShareInfo parseShareInfo(String body) {
        JsonObject root = gson.fromJson(body, JsonObject.class);
        JsonObject info = root.has("info") ? root.getAsJsonObject("info") : new JsonObject();
        String fileId = str(info, "f_id");
        String name = str(info, "name");
        String pwd = str(info, "pwd");
        boolean needPwd = "1".equals(str(info, "onof"));
        String shareUrl = properties.getShareUrl().replaceAll("/+$", "") + "/" + fileId;
        return new LanzouShareInfo(fileId, name, needPwd, pwd, shareUrl);
    }

    private List<LanzouFile> parseFiles(JsonObject root) {
        JsonArray arr = arrayOrEmpty(root, "text");
        List<LanzouFile> result = new ArrayList<>();
        for (JsonElement el : arr) {
            JsonObject o = el.getAsJsonObject();
            result.add(new LanzouFile(str(o, "id"), str(o, "name_all"), 0L, str(o, "share_id")));
        }
        return result;
    }

    private List<LanzouFolder> parseFolders(JsonObject root) {
        JsonArray arr = arrayOrEmpty(root, "info");
        List<LanzouFolder> result = new ArrayList<>();
        for (JsonElement el : arr) {
            JsonObject o = el.getAsJsonObject();
            result.add(new LanzouFolder(str(o, "folder_id"), str(o, "folder_name")));
        }
        return result;
    }

    private static JsonArray arrayOrEmpty(JsonObject root, String key) {
        JsonElement el = root.get(key);
        return (el != null && el.isJsonArray()) ? el.getAsJsonArray() : new JsonArray();
    }

    private static String str(JsonObject obj, String key) {
        JsonElement el = obj.get(key);
        return (el == null || el.isJsonNull()) ? "" : el.getAsString();
    }

    private static String truncate(String s) {
        return s != null && s.length() > 400 ? s.substring(0, 400) + "..." : s;
    }

    private static String emptyIfNull(String v) {
        return v == null ? "" : v;
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LanzouSessionException("interrupted", e);
        }
    }

    private static String contentTypeOf(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".mp3")) return "audio/mpeg";
        if (lower.endsWith(".flac")) return "audio/flac";
        if (lower.endsWith(".wav")) return "audio/wav";
        if (lower.endsWith(".ogg")) return "audio/ogg";
        if (lower.endsWith(".m4a")) return "audio/mp4";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".zip")) return "application/zip";
        if (lower.endsWith(".rar")) return "application/x-rar-compressed";
        if (lower.endsWith(".7z")) return "application/x-7z-compressed";
        return "application/octet-stream";
    }

    /** 内存 CookieJar */
    private static class MemoryCookieJar implements CookieJar {
        private final Map<String, List<Cookie>> store = new ConcurrentHashMap<>();

        @Override
        public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
            // 合并而不是覆盖
            List<Cookie> existing = store.getOrDefault(url.host(), new ArrayList<>());
            Map<String, Cookie> merged = new LinkedHashMap<>();
            for (Cookie c : existing) {
                merged.put(c.name(), c);
            }
            for (Cookie c : cookies) {
                merged.put(c.name(), c);
            }
            store.put(url.host(), new ArrayList<>(merged.values()));
        }

        @Override
        public List<Cookie> loadForRequest(HttpUrl url) {
            List<Cookie> cookies = store.getOrDefault(url.host(), List.of());
            if (!cookies.isEmpty()) {
            }
            return cookies;
        }
    }
}
