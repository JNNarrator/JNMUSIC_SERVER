# 蓝奏云客户端使用文档

## 概述

`LanzouApiClient` 是一个基于 OkHttp 4.x 的蓝奏云 Java 客户端，封装了蓝奏云管理后台和分享页的主要接口。内置 `acw_sc__v2` 反爬 Cookie 自动注入机制。

## 快速开始

### 1. 配置

**application.properties**

```properties
# 蓝奏云管理后台地址（默认）
lanzou.client.base-url=https://pc.woozooo.com
# 蓝奏云分享页地址（默认）
lanzou.client.share-url=https://pan.lanzoui.com
# 默认根目录ID（-1 表示根目录）
lanzou.client.default-root-folder-id=-1
# 连接超时（毫秒）
lanzou.client.connect-timeout-ms=10000
# 读取超时（毫秒）
lanzou.client.read-timeout-ms=30000
```

### 2. 注入 Cookie 登录态（推荐）

从浏览器蓝奏云控制台登录后，通过开发者工具复制 Cookie，注入客户端：

```java
@Autowired
private LanzouApiClient lanzouClient;

// 注入浏览器 Cookie（必须包含 phpdisk_info）
lanzouClient.setSessionCookie("phpdisk_info=abc123; lang=zh; ...");
```

### 3. 获取 uid/vei

```java
LanzouUidVei uidVei = lanzouClient.getUidVei();
// uidVei.uid() -> "123456"
// uidVei.vei() -> "789"
```

**注意：** `uid` 和 `vei` 从 `mydisk.php` 页面 HTML 中提取，部分蓝奏云管理接口需要这两个参数。

## API 参考

### 文件列表

| 方法 | 说明 | 对应蓝奏云接口 |
|------|------|----------------|
| `listFiles(folderId, page)` | 分页列出目录下的文件与文件夹 | `doupload.php?task=5` |
| `listFolders(parentId)` | 列出文件夹列表（不含文件） | `doupload.php?task=47` |

**示例：**

```java
// 列根目录，第1页
LanzouPageResult result = lanzouClient.listFiles("-1", 1);
for (LanzouFile file : result.files()) {
    System.out.println(file.id() + " - " + file.name());
}
for (LanzouFolder folder : result.folders()) {
    System.out.println(folder.id() + " - " + folder.name());
}
```

### 文件夹管理

| 方法 | 说明 | 对应蓝奏云接口 |
|------|------|----------------|
| `createFolder(parentId, name)` | 创建文件夹，返回 `LanzouFolder` | `doupload.php?task=2` |
| `renameFolder(folderId, newName)` | 重命名文件夹 | `doupload.php?task=4` |
| `deleteFolder(folderId)` | 删除文件夹 | `doupload.php?task=3` |

**示例：**

```java
// 创建文件夹
LanzouFolder folder = lanzouClient.createFolder("-1", "新专辑");

// 重命名
lanzouClient.renameFolder(folder.id(), "修改后");

// 删除
lanzouClient.deleteFolder(folder.id());
```

### 文件管理

| 方法 | 说明 | 对应蓝奏云接口 |
|------|------|----------------|
| `renameFile(fileId, newName)` | 重命名文件 | `doupload.php?task=46` |
| `moveFile(fileId, folderId)` | 移动文件到指定文件夹 | `doupload.php?task=20` |
| `deleteFile(fileId)` | 删除文件 | `doupload.php?task=6` |

### 上传

| 方法 | 说明 | 对应蓝奏云接口 |
|------|------|----------------|
| `upload(folderId, fileName, file)` | 两阶段上传：prepare + multipart upload | `html5up.php` |

**示例：**

```java
File file = new File("/tmp/demo.mp3");
LanzouUploadResult result = lanzouClient.upload("-1", "demo.mp3", file);
// result.fileId() -> 上传任务ID
// result.name() -> demo.mp3
// result.size() -> 文件字节数
```

### 直链

| 方法 | 说明 | 对应蓝奏云接口 |
|------|------|----------------|
| `directLink(fileId, fileName)` | 获取文件直链（4小时过期） | `doupload.php?task=12` |
| `createDirectShare(fileId, fileName)` | 创建直链分享（同 `directLink`） | 同上 |

**示例：**

```java
LanzouDirectLink link = lanzouClient.directLink("12345", "demo.mp3");
// link.url() -> "https://cdn.lanzou.com/..."
// link.expiresAt() -> 过期时间
```

### 分享管理

| 方法 | 说明 | 对应蓝奏云接口 |
|------|------|----------------|
| `getFileShareInfo(fileId)` | 获取文件分享信息 | `doupload.php?task=22` |
| `createFileShare(fileId, enablePassword, password)` | 创建/更新文件本站分享 | `doupload.php?task=23` |
| `getFolderShareInfo(folderId)` | 获取文件夹分享信息 | `doupload.php?task=18` |
| `createFolderShare(folderId, enablePassword, password)` | 创建/更新文件夹分享 | `doupload.php?task=16` |

**返回值说明：**

- `getFileShareInfo` / `getFolderShareInfo` 返回 `LanzouShareInfo`：
  - `fileId()`、`fileName()`、`passwordRequired()`、`password()`、`shareUrl()`
- `createFileShare` / `createFolderShare` 返回 `String`：格式为 `url|code`（有密码多为 `url|code`，无密码仅为 `url`）

**示例：**

```java
// 查看分享信息
LanzouShareInfo info = lanzouClient.getFileShareInfo("12345");
if (info.passwordRequired()) {
    System.out.println("提取码: " + info.password());
}

// 创建带密码分享
String share = lanzouClient.createFileShare("12345", true, "1234");
// share -> "https://pan.lanzoui.com/xxxx|1234"
```

### 分享页解析

| 方法 | 说明 |
|------|------|
| `resolveShareLink(shareId)` | 解析分享页，返回包含直链的 `LanzouShareLink` |
| `getShareFolderFiles(shareId, folderId, page)` | 获取分享目录中的文件（分页） |
| `getShareFileInfo(shareId, fileId, password)` | 获取分享文件的详细信息 |

**示例：**

```java
LanzouShareLink link = lanzouClient.resolveShareLink("share123");
// link.shareId() -> "share123"
// link.shareUrl() -> "https://pan.lanzoui.com/share123"
// link.directUrl() -> "https://cdn.lanzou.com/..."
// link.requirePassword() -> true/false
```

### 反爬处理

客户端内置反爬自动处理机制：

1. 在 `executeString` 中检测返回内容是否包含 `acw_sc__v2` 或 `arg1=`
2. 若检测到，自动调用 `computeAcwScV2` 计算并重试
3. 登录流程最多重试 5 次，其他接口 3 次

手动计算：

```java
String acw = LanzouApiClient.computeAcwScV2("abcdef...");
lanzouClient.setAcwScV2(acw);
```

## DTO 一览

| DTO | 字段 | 说明 |
|-----|------|------|
| `LanzouFile` | id, name, size, shareId | 文件信息 |
| `LanzouFolder` | id, name | 文件夹信息 |
| `LanzouPageResult` | page, pageSize, files, folders | 分页结果 |
| `LanzouUploadResult` | fileId, name, size, shareId, shareUrl | 上传结果 |
| `LanzouDirectLink` | url, expiresAt | 直链（4小时过期） |
| `LanzouShareLink` | shareId, shareUrl, directUrl, requirePassword | 分享解析结果 |
| `LanzouShareInfo` | fileId, fileName, passwordRequired, password, shareUrl | 分享信息详情 |
| `LanzouUidVei` | uid, vei | 用户状态参数 |

## 异常处理

所有接口失败时抛出 `LanzouSessionException`（继承 `RuntimeException`），可根据具体情况进行捕获：

```java
try {
    lanzouClient.listFiles("-1", 1);
} catch (LanzouSessionException e) {
    // 检查是否触发反爬或登录态失效
    if (e.getMessage().contains("anti-bot")) {
        // 需要重新注入 Cookie
    }
}
```

## 测试

```bash
mvn test -Dtest='LanzouApiClientTest'
```

当前测试覆盖：

| 覆盖点 | 测试方法 |
|--------|----------|
| 列目录（文件和文件夹解析） | `listFiles_shouldParseFoldersAndFiles` |
| 列目录无参数默认根目录 | `listFiles_shouldDefaultToConfiguredRootFolder` |
| 列文件夹 | `listFolders_shouldParseFolders` |
| 创建文件夹 | `createFolder_shouldReturnId` |
| 文件重命名、移动、删除 | `renameMoveDelete_shouldSendExpectedTasks` |
| 文件夹重命名 | `renameFolder_shouldSendRenameTask` |
| 文件夹删除 | `deleteFolder_shouldSendDeleteTask` |
| 上传（两阶段） | `upload_shouldPrepareAndUploadChunks` |
| 上传文件缺失 | `upload_shouldThrowOnMissingFile` |
| 直链解析 | `directLink_shouldReturnUrlAndExpiry` |
| 直链解析失败 | `directLink_shouldThrowOnMissingText` |
| 创建直链分享 | `createDirectShare_shouldProxyToDirectLink` |
| 文件分享信息/创建 | `fileShareFlow_shouldReturnInfoAndShareToken` |
| 文件夹分享信息/创建 | `folderShareFlow_shouldReturnInfoAndShareToken` |
| 分享页解析（密码检测） | `resolveShareLink_shouldDetectPasswordPrompt` |
| 分享页解析（iframe下载页） | `resolveShareLink_shouldParseIframeDownPage` |
| 分享页反爬检测 | `resolveShareLink_shouldThrowOnAntiBot` |
| 空/空值 shareId | `resolveShareLink_shouldThrowOnBlankId` |
| 分享目录文件 | `getShareFolderFiles_shouldReturnParsedText` |
| 分享目录文件回退 | `getShareFolderFiles_shouldFallbackToRawBodyWhenTextMissing` |
| 分享文件信息 | `getShareFileInfo_shouldReturnJsonPayload` |
| uid/vei 提取 | `getUidVei_shouldExtractValuesFromMydisk` |
| uid/vei 反爬重试 | `getUidVei_shouldRetryAntiBotOnce` |
| Cookie 登录 | `loginByCookie_shouldSubmitLoginForm` |
| acw_sc__v2 计算 | `acwSolver_shouldComputeExpectedHash` |
| acw_sc__v2 长 hex 回退 | `acwSolver_shouldFallbackToLongHex` |
| acw_sc__v2 短输入异常 | `acwSolver_shouldThrowOnShortInput` |
| Cookie 注入校验 | `setSessionCookie_shouldThrowWhenMissingPhpdisk`、`setSessionCookie_shouldAcceptValidCookie` |
