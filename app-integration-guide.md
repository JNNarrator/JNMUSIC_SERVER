# JNMusic App 接口对接指南

编写日期：2026-07-03  
适用对象：负责把 JNMusic Flutter App 从外部音源迁移到自建后端的开发者/AI 助手。

本指南与仓库当前 Spring Boot 4 + MyBatis-Plus 实现完全对齐，覆盖 P0 音乐库 + P1 用户数据同步的全部端点。所有接口默认允许匿名访问，登录鉴权作为后续扩展保留。

---

## 1. 基础约定

### 1.1 环境地址

```
本地开发（Spring Boot）: http://127.0.0.1:19001/music/api/v1
生产环境: https://<你的部署域名>/music/api/v1
文件服务（dufs 直连）: http://jn_file.88933.vip
```

说明：
- Spring Boot `server.servlet.context-path=/music`，业务接口前缀是 `/music/api/v1`。
- 音频 / 封面 / 歌词由文件服务器直接对外提供，App 拿到后端返回的完整 URL 后可直接消费，支持 HTTP Range。

### 1.2 通用请求头

| 请求头 | 必填 | 说明 |
| --- | --- | --- |
| `Content-Type` | 写请求必填 | 固定 `application/json; charset=utf-8` |
| `Accept` | 建议 | `application/json` |
| `X-Device-Id` | 建议 | 匿名设备唯一 ID；未传时后端归档为 `anonymous` |
| `X-Client-Platform` | 建议 | `ios` / `android` / `macos` / `windows` / `linux` |
| `X-Client-Version` | 建议 | 客户端版本号，例如 `1.0.0` |
| `X-Trace-Id` | 可选 | 客户端可自带；未传时后端生成 `req_...`，并回写到响应头 `X-Trace-Id` |
| `Authorization` | 否 | 预留 `Bearer <token>`，当前阶段可忽略 |

P1 收藏 / 历史 / 搜索历史 / 播放队列全部按 `X-Device-Id` 维度隔离；切换账号前 App 应保持 `X-Device-Id` 稳定。

### 1.3 统一响应结构

成功：

```json
{
  "success": true,
  "data": { },
  "error": null,
  "traceId": "req_1783048069923_2bb5..."
}
```

分页成功：

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
  "error": null,
  "traceId": "req_1783048069923_2bb5..."
}
```

失败：

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "TRACK_NOT_FOUND",
    "message": "歌曲不存在或已下架"
  },
  "traceId": "req_1783048069923_2bb5..."
}
```

客户端建议约定：
- 用 `success` 判断结果，失败时读取 `error.code` 分流。
- 把 `traceId` 写进日志，遇到线上问题直接给后端定位。
- 分页字段：`page` 从 1 开始，`pageSize` 上限 50，超出后端会自动截断。

### 1.4 错误码

| 错误码 | HTTP | 触发场景 | App 建议处理 |
| --- | --- | --- | --- |
| `TRACK_NOT_FOUND` | 404 | 详情 / 播放 / 收藏 / 队列引用了不存在的 `trackId` | 提示歌曲已下架，可从缓存里剔除 |
| `SEARCH_NO_RESULTS` | 200 | 搜索无结果（当前按空 `data.items` 返回，不会以错误码抛出） | 展示空状态 UI |
| `MEDIA_UNAVAILABLE` | 502 | 播放地址暂不可用 | 重试或降级音质 |
| `RATE_LIMITED` | 429 | 请求频率过高 | 指数退避 |
| `INVALID_PARAMETER` | 400 | 参数缺失 / 超限（`q` 为空、batch ids > 50、`quality` 非法等） | 表单错误提示 |
| `INTERNAL_ERROR` | 500 | 服务内部错误 | 通用错误 toast |

### 1.5 核心数据模型

`TrackDTO`（详情 / 批量 / 收藏 / 历史 / 队列共用）：

```json
{
  "trackId": "T0000421",
  "name": "晴天",
  "artist": "周杰伦",
  "album": "叶惠美",
  "coverUrl": "http://jn_file.88933.vip:27472/covers/T0000421.jpg",
  "duration": 269,
  "format": "flac",
  "fileSize": 52600000,
  "trackNumber": 1,
  "hasLyric": false,
  "lyricUrl": null
}
```

`TrackSummaryDTO`（搜索列表专用）：

```json
{
  "trackId": "T0000421",
  "name": "晴天",
  "artist": "周杰伦",
  "album": "叶惠美",
  "coverUrl": "http://jn_file.88933.vip:27472/covers/T0000421.jpg",
  "duration": 269
}
```

`MediaUrlDTO`（播放地址）：

```json
{
  "trackId": "T0000421",
  "mediaUrl": "http://jn_file.88933.vip:27472/audio/T0000421.flac",
  "format": "flac",
  "expiresAt": "2026-07-04T03:11:19Z"
}
```

`HistoryTrackDTO`（历史记录专用）：

```json
{
  "track": {},
  "playedAt": "2026-07-04T03:11:19Z"
}
```

`SearchKeywordDTO`（搜索历史专用）：

```json
{
  "keyword": "周杰伦",
  "searchedAt": "2026-07-04T03:11:19Z"
}
```

`QueueItemDTO`（播放队列专用）：

```json
{
  "trackId": "T0000421",
  "position": 0,
  "track": {}
}
```

`ExistsDTO`（存在性检查）：

```json
{
  "exists": true
}
```

---

## 2. P0 音乐库接口

### 2.1 搜索歌曲

```http
GET /api/v1/tracks/search?q=晴天&page=1&pageSize=20
```

| 参数 | 类型 | 必填 | 默认 | 说明 |
| --- | --- | --- | --- | --- |
| `q` | string | 是 | - | 搜索关键词，会去掉首尾空格；为空时直接返回参数错误 |
| `page` | int | 否 | 1 | 页码，从 1 开始 |
| `pageSize` | int | 否 | 20 | 每页数量，最大 50 |

说明：
- 当前实现按歌曲名 / 歌手 / 专辑做中文 LIKE 模糊匹配；拼音索引为后续搜索升级保留。
- 无结果时返回 `success: true`，`data.items` 为空数组。

响应 `data` 为分页格式：

```json
{
  "items": [
    {
      "trackId": "T0000421",
      "name": "晴天",
      "artist": "周杰伦",
      "album": "叶惠美",
      "coverUrl": "http://jn_file.88933.vip:27472/covers/T0000421.jpg",
      "duration": 269
    }
  ],
  "page": 1,
  "pageSize": 20,
  "total": 1,
  "hasMore": false
}
```

### 2.2 浏览歌曲列表

```http
GET /api/v1/tracks?page=1&pageSize=20
```

| 参数 | 类型 | 必填 | 默认 | 说明 |
| --- | --- | --- | --- | --- |
| `page` | int | 否 | 1 | 页码，从 1 开始 |
| `pageSize` | int | 否 | 20 | 每页数量，最大 50 |

说明：
- 用于浏览全量歌曲列表；按 `trackId` 倒序分页。
- 响应结构与 `/tracks/search` 相同，返回 `PageResponse<TrackSummaryDTO>`。

### 2.3 获取歌曲详情

```http
GET /api/v1/tracks/{trackId}
```

说明：
- `trackId` 首尾空格会被自动去除；为空或不存在时返回 `404 TRACK_NOT_FOUND`。
- 返回完整 `TrackDTO`，适合详情页使用。

响应 `data`：

```json
{
  "trackId": "T0000421",
  "name": "晴天",
  "artist": "周杰伦",
  "album": "叶惠美",
  "coverUrl": "http://jn_file.88933.vip:27472/covers/T0000421.jpg",
  "duration": 269,
  "format": "flac",
  "fileSize": 52600000,
  "trackNumber": 1,
  "hasLyric": false,
  "lyricUrl": null
}
```

### 2.4 批量获取歌曲元数据

```http
GET /api/v1/tracks/batch?ids=T0000421,T0000422,T0000423
```

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `ids` | string[] | 是 | 逗号分隔或重复传入的 `trackId`，最多 50 个；后端会自动去重 |

说明：
- 适合播放队列初始化、批量刷新元数据。
- 返回 `PageResponse<TrackDTO>`，`page=1`，`pageSize` 为实际去重后的请求数，`hasMore` 固定为 `false`。
- 不存在于库中的 `trackId` 会直接不返回，不会报错。

响应 `data`：

```json
{
  "items": [],
  "page": 1,
  "pageSize": 2,
  "total": 2,
  "hasMore": false
}
```

### 2.5 获取播放地址

```http
GET /api/v1/tracks/{trackId}/media-url
```

说明：
- 文件地址由后端根据文件服务器配置拼接，客户端直接消费。
- 若目标音质为 `mp3_320` / `mp3_128`，返回的 `format` 和文件扩展名会变成所选音质代码；`flac` 保持原格式。
- `expiresAt` 固定为 24 小时后，适合做本地地址缓存过期策略。

响应 `data`：

```json
{
  "trackId": "T0000421",
  "mediaUrl": "http://jn_file.88933.vip:27472/audio/T0000421.flac",
  "format": "flac",
  "expiresAt": "2026-07-04T03:11:19Z"
}
```

---

## 3. P1 用户数据接口

P1 接口全部按 `X-Device-Id` 隔离；未传时后端按 `anonymous` 处理。首次写入数据前会校验 `trackId` / `keyword` 是否存在，避免污染无效数据。

### 3.1 收藏

```http
GET    /api/v1/favorites?page=1&pageSize=50
POST   /api/v1/favorites
DELETE /api/v1/favorites/{trackId}
GET    /api/v1/favorites/{trackId}/exists
```

`POST /api/v1/favorites`：

```json
{
  "trackId": "T0000421"
}
```

说明：
- 已收藏时再次添加会幂等，不会重复写入。
- `GET /api/v1/favorites/{trackId}/exists` 返回 `{"exists": true}`。

响应 `data`：

```json
{
  "items": [],
  "page": 1,
  "pageSize": 20,
  "total": 3,
  "hasMore": false
}
```

### 3.2 播放历史

```http
GET    /api/v1/history?page=1&pageSize=50
POST   /api/v1/history
DELETE /api/v1/history
```

`POST /api/v1/history`：

```json
{
  "trackId": "T0000421"
}
```

说明：
- 同一 `trackId` 重复上报会更新时间，不会产生重复记录。
- 按最近播放时间倒序分页，返回 `playedAt`。

响应 `data`：

```json
{
  "items": [
    {
      "track": {},
      "playedAt": "2026-07-04T03:11:19Z"
    }
  ],
  "page": 1,
  "pageSize": 20,
  "total": 1,
  "hasMore": false
}
```

### 3.3 搜索历史

```http
GET    /api/v1/search-history?limit=20
POST   /api/v1/search-history
DELETE /api/v1/search-history
```

`POST /api/v1/search-history`：

```json
{
  "keyword": "周杰伦"
}
```

说明：
- 按最近搜索时间倒序返回，`limit` 默认 20，最大 50。
- 关键词不区分大小写敏感策略由前端控制；后端只做去重和更新时间。
- 返回对象数组，不是分页结构。

响应 `data`：

```json
[
  {
    "keyword": "周杰伦",
    "searchedAt": "2026-07-04T03:11:19Z"
  }
]
```

### 3.4 播放队列

```http
GET    /api/v1/queue
PUT    /api/v1/queue
POST   /api/v1/queue/items
DELETE /api/v1/queue/items/{trackId}
```

`PUT /api/v1/queue`：

```json
{
  "items": [
    { "trackId": "T0000421", "position": 0 },
    { "trackId": "T0000422", "position": 1 }
  ]
}
```

说明：
- `PUT /api/v1/queue` 会清空旧队列再重建；客户端提交的 `position` 仅供排序，后端会重新按传入顺序编号为 `0, 1, 2`。
- `POST /api/v1/queue/items` 会追加到队尾；已存在时不会重复添加。
- 返回有序队列列表，并按顺序返回歌曲元数据。

响应 `data`：

```json
[
  {
    "trackId": "T0000421",
    "position": 0,
    "track": {}
  }
]
```

---

## 4. Flutter 客户端对接要点

### 4.1 地址拼接规则

- `/tracks/{trackId}/media-url` 返回的 `mediaUrl` 可直接交给 `just_audio` 播放。
- `coverUrl` / `lyricUrl` 可能为 `null`；遇到 `null` 时展示占位图或隐藏歌词。
- `expiresAt` 可根据本地播放器缓存策略清理失效地址；24 小时内无需频繁重算。

### 4.2 本地缓存建议

- 搜索建议缓存 `TrackSummaryDTO`；详情页缓存 `TrackDTO`。
- 收藏 / 历史 / 搜索历史 / 播放队列建议先写本地，再静默同步到后端。
- 删除后端数据时，本地缓存也要同步清理，避免假收藏 / 假历史。

### 4.3 队列兼容处理

- `PUT /queue` 会重建队列；客户端保存前请校验 `trackId` 是否仍然存在。
- 已下架歌曲不会进入队列返回结果；本地仍保留但播放前需要重新校验。
- 客户端保存队列时无需提交完整歌曲元数据，只传 `trackId + position` 即可。

### 4.4 异常与兼容

- 所有写接口失败时按幂等重试，不要在 UI 上阻塞。
- `traceId` 建议写入本地崩溃或埋点日志，便于后端排查。
- 若未来接入登录态，当前 `X-Device-Id` 仍可作为兜底匿名维度继续使用。
