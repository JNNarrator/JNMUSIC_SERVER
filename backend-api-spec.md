# JNMusic 后端接口文档

编写日期：2026-07-02  
适用阶段：前端基本功能完成后，开始建设自有后端服务

## 1. 目标与边界

JNMusic 当前 Flutter 前端已经完成「搜索 -> 详情 -> 播放 -> 收藏 / 历史 / 队列 / 设置」基础闭环。下一阶段后端定位为 **自有音乐库 API + 文件服务器资源管理服务**。

后端不再向 App 暴露外部音源概念，也不需要在 App 层区分 `a / b / c` 等音源类型。`24bit-music-api-notes.md` 中记录的外部接口只作为早期数据导入、字段参考或临时兼容资料；真正面向 App 的接口统一围绕自建音乐文件服务器设计。

核心目标：

1. App 只依赖自有后端接口，不直接请求外部音乐站点。
2. 后端统一管理歌曲元数据、封面、音频文件 URL、搜索索引和播放地址。
   （歌词、专辑图片等扩展信息为可选，数据模型保留扩展字段，可为 null）
3. 前端逐步从 `SongModel.id + sourceType` 迁移到统一的 `trackId`。
4. 客户端 SQLite 继续作为离线缓存和弱网兜底，不和后端职责冲突。
5. 第一阶段优先支持匿名使用；账号、跨设备同步、管理后台可后续扩展。

## 2. 实现优先级

| 优先级 | 能力 | 说明 |
| --- | --- | --- |
| P0 | 音乐库 API | 搜索、详情、元数据读取 |
| P0 | 文件服务 | 音频、封面的静态托管或签名访问 |
| P0 | 播放地址服务 | 返回可播放的媒体 URL，隐藏真实存储路径 |
| P1 | 用户数据服务 | 收藏、播放历史、搜索历史、播放队列同步 |
| P1 | 数据导入服务 | 从本地文件、外部记录、人工表格导入元数据 |
| P2 | 管理后台 API | 上传、编辑、下架、重扫音乐文件 |

第一阶段只需要完成 P0。P1 / P2 可以先按本文档预留数据结构，等前端对接稳定后再实现。

## 3. 通用约定

### 3.1 基础地址

```text
本地开发: http://127.0.0.1:8080/api/v1
生产环境: https://api.jnmusic.example.com/api/v1
文件服务: https://media.jnmusic.example.com
```

业务接口默认使用 JSON：

```http
Content-Type: application/json; charset=utf-8
Accept: application/json
```

音频文件接口必须支持 HTTP Range 请求，否则 `just_audio` 在拖动进度、后台播放和大文件播放时会不稳定。

### 3.2 鉴权

P0 音乐搜索、详情、播放地址接口可以匿名访问。P1 用户同步接口建议支持匿名设备态，也预留登录态。

```http
Authorization: Bearer <access_token>
X-Device-Id: <device_uuid>
X-Client-Platform: ios | android | macos
X-Client-Version: 1.0.0
```

| 请求头 | 必填 | 说明 |
| --- | --- | --- |
| `Authorization` | 否 | 用户登录后传。匿名阶段可不传 |
| `X-Device-Id` | 建议 | 设备唯一 ID，用于匿名同步、限流和排查问题 |
| `X-Client-Platform` | 建议 | 客户端平台 |
| `X-Client-Version` | 建议 | 客户端版本 |

### 3.3 统一响应

成功响应：

```json
{
  "success": true,
  "data": {},
  "traceId": "req_20260702_xxx"
}
```

分页成功响应：

```json
{
  "success": true,
  "data": {
    "items": [],
    "page": 1,
    "pageSize": 20,
    "total": 128,
    "hasMore": true
  },
  "traceId": "req_20260702_xxx"
}
```

失败响应：

```json
{
  "success": false,
  "error": {
    "code": "TRACK_NOT_FOUND",
    "message": "歌曲不存在或已下架"
  },
  "traceId": "req_20260702_xxx"
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `success` | bool | 本次请求是否成功 |
| `data` | object/null | 业务数据（成功时返回） |
| `error` | object/null | 错误信息（失败时返回） |
| `traceId` | string | 请求追踪 ID，方便排查 |

### 3.4 核心数据模型

#### Track — 歌曲元数据

向后端 **所有接口统一使用 `trackId` 标识歌曲**。前端当前使用的 `SongModel.id + sourceType` 只在迁移阶段作为兼容字段。

```json
{
  "trackId": "T0000421",
  "name": "晴天",
  "artist": "周杰伦",
  "album": "叶惠美",
  "coverUrl": "https://media.jnmusic.example.com/covers/T0000421.jpg",
  "duration": 269,
  "format": "flac",
  "fileSize": 52600000,
  "trackNumber": 1,
  "hasLyric": false,
  "lyricUrl": null
}
```

| 字段 | 类型 | 必含 | 说明 |
| --- | --- | --- | --- |
| `trackId` | string | 是 | 全局唯一歌曲 ID，后端生成 |
| `name` | string | 是 | 歌曲名 |
| `artist` | string | 是 | 歌手名 |
| `album` | string | 否 | 专辑名 |
| `coverUrl` | string | 否 | 封面图片 URL |
| `duration` | number | 是 | 时长（秒） |
| `format` | string | 否 | 文件格式：flac / mp3 / wav / aac |
| `fileSize` | number | 否 | 文件大小（字节） |
| `trackNumber` | number | 否 | 专辑内曲目序号 |
| `hasLyric` | bool | 否 | 是否有歌词文件，默认 false |
| `lyricUrl` | string/null | 否 | 歌词文件 URL，无歌词时为 null |

> 说明：歌词、图片等扩展信息为可选。数据模型保留上述扩展字段，无数据时设为 null 即可。

### 3.5 错误码

| 错误码 | HTTP 状态码 | 说明 |
| --- | --- | --- |
| `TRACK_NOT_FOUND` | 404 | 歌曲不存在或已下架 |
| `SEARCH_NO_RESULTS` | 200 | 搜索无结果（success: true, data.items 为空数组） |
| `MEDIA_UNAVAILABLE` | 502 | 播放地址暂时不可用 |
| `RATE_LIMITED` | 429 | 请求频率过高 |
| `INVALID_PARAMETER` | 400 | 请求参数校验失败 |
| `INTERNAL_ERROR` | 500 | 服务内部错误 |

## 4. P0 接口清单

### 4.1 搜索歌曲

```http
GET /tracks/search?q=晴天&page=1&pageSize=20
```

| 参数 | 类型 | 必填 | 默认 | 说明 |
| --- | --- | --- | --- | --- |
| `q` | string | 是 | - | 搜索关键词，建议支持拼音和模糊匹配 |
| `page` | int | 否 | 1 | 页码，从 1 开始 |
| `pageSize` | int | 否 | 20 | 每页数量，最大 50 |

响应 `data` 为分页格式：

```json
{
  "items": [
    {
      "trackId": "T0000421",
      "name": "晴天",
      "artist": "周杰伦",
      "album": "叶惠美",
      "coverUrl": "https://media.jnmusic.example.com/covers/T0000421.jpg",
      "duration": 269
    }
  ],
  "page": 1,
  "pageSize": 20,
  "total": 1,
  "hasMore": false
}
```

### 4.2 获取歌曲详情

```http
GET /tracks/{trackId}
```

返回完整 Track 对象。前端详情页（`DetailPage`）使用此接口获取完整元数据。

### 4.3 批量获取歌曲元数据

```http
GET /tracks/batch?ids=T0000421,T0000422,T0000423
```

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `ids` | string | 是 | 逗号分隔的 trackId，最多 50 个 |

返回分页格式的 `items` 数组。播放队列初始化时前端调用此接口一次性获取多首歌曲元数据。

### 4.4 获取播放地址

```http
GET /tracks/{trackId}/media-url
```

| 查询参数 | 类型 | 必填 | 默认 | 说明 |
| --- | --- | --- | --- | --- |
| `quality` | string | 否 | flac | 可选 `flac` / `mp3_320` / `mp3_128` |

响应 `data` 示例：

```json
{
  "trackId": "T0000421",
  "mediaUrl": "https://media.jnmusic.example.com/audio/T0000421.flac",
  "format": "flac",
  "expiresAt": "2026-07-03T00:00:00Z"
}
```

说明：

- `mediaUrl` 是直接可播放的音频 URL，必须支持 HTTP Range 请求（`Accept-Ranges: bytes`，响应 `206 Partial Content`）。
- 文件服务器建议用 Nginx / CDN 直接托管，URL 可附带短期签名（如 24h 过期）。
- 前端 `just_audio` 的 `AudioPlayer.setAudioSource` 直接消费此 URL。

## 5. P1 接口清单

以下接口建议在 P0 稳定后再实现。前端当前用 SQLite 管理这些数据，P1 阶段做双写 + 云端同步：客户端先写本地确认反馈，后台静默同步到后端，不阻塞用户操作。

### 5.1 收藏

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/favorites?page=1&pageSize=50` | 获取收藏列表，分页返回 Track 对象 |
| `POST` | `/favorites` | 添加收藏。Body: `{"trackId": "T0000421"}` |
| `DELETE` | `/favorites/{trackId}` | 取消收藏 |
| `GET` | `/favorites/{trackId}/exists` | 检查是否已收藏。返回 `{"exists": true}` |

### 5.2 播放历史

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/history?page=1&pageSize=50` | 获取播放历史，分页返回带 `playedAt` 的 Track |
| `POST` | `/history` | 记录一次播放。Body: `{"trackId": "T0000421"}` |
| `DELETE` | `/history` | 清空播放历史 |

### 5.3 搜索历史

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/search-history?limit=20` | 获取搜索关键词列表 |
| `POST` | `/search-history` | 记录搜索关键词。Body: `{"keyword": "周杰伦"}` |
| `DELETE` | `/search-history` | 清空所有搜索关键词 |

### 5.4 播放队列

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/queue` | 获取当前设备播放队列，返回有序 Track 列表 |
| `PUT` | `/queue` | 覆盖保存队列。Body: `{"items": [{"trackId": "T0000421", "position": 0}]}` |
| `POST` | `/queue/items` | 追加单曲到队尾。Body: `{"trackId": "T0000421"}` |
| `DELETE` | `/queue/items/{trackId}` | 从队列移除 |

## 6. 文件服务器规范

### 6.1 目录结构

```text
/audio/T0000421.flac      # 音频文件
/covers/T0000421.jpg      # 歌曲封面
/covers/ALB001.jpg        # 专辑封面
/lyrics/T0000421.lrc      # 歌词（可选，目录保留但不要求必须存在）
```

### 6.2 要求

1. 音频文件必须支持 HTTP Range 请求，Nginx 默认支持（`add_header Accept-Ranges bytes`）。
2. 封面图片建议 JPG 格式，最小宽度 512px。
3. 文件服务器域名与 API 域名分离（`media.*` vs `api.*`），方便 CDN 加速。

## 7. 前端迁移步骤

### 7.1 字段映射

| 当前前端字段 | 后端新字段 | 迁移说明 |
| --- | --- | --- |
| `SongModel.id` | `trackId` | 类型不变，语义统一 |
| `SongModel.sourceType` | 废弃 | 不再用于文件寻址，仅历史兼容 |
| `SongModel.coverUrl` | `coverUrl` | 域名改为文件服务器 |
| `SearchResult.player` | `artist` | 字段名对齐 |
| `SearchResult.cover` | `coverUrl` | 字段名对齐 |

### 7.2 第一阶段对接路径

1. 后端完成 P0 接口 + 文件服务器部署后，修改前端的 `DioClient.baseUrl` 指向自有地址。
2. 修改 `SearchRepository`：改为调用 `GET /tracks/search`，不再请求外部搜索源。
3. 移除 `DetailParser` 或降级为调用 `GET /tracks/{trackId}/media-url`，不再解析外部详情页 HTML。
4. `SongModel` 新增 `trackId` 字段，逐步替换 `id + sourceType` 组合。
5. SQLite 中的 `media_caches` 表和 `DetailParser` 缓存逻辑可不再维护。

## 8. 推荐技术栈

## 9. 第一阶段最小实现任务清单

- [ ] 搭建文件服务器（Nginx 托管音频 + 封面目录），写入首批测试歌曲
- [ ] 实现 `GET /tracks/search`
- [ ] 实现 `GET /tracks/{trackId}`
- [ ] 实现 `GET /tracks/{trackId}/media-url`
- [ ] 实现 `GET /tracks/batch`
- [ ] 前端修改 `SearchRepository` 指向自有搜索接口
- [ ] 前端修改 `DetailParser` / `DetailProvider` 指向自有播放地址接口
- [ ] 前端 `SongModel` 新增 `trackId` 字段，逐步废弃 `sourceType`
- [ ] 双端联调：搜索 -> 详情 -> 播放链路跑通

验证标准：搜索一首歌、点击进入详情、拿到播放地址并开始播放。完成这个闭环后，P0 就算达标。
