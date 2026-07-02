# JNMusic 后端开发计划

## 1. 项目背景与目标

基于 `backend-api-spec.md`，本项目目标是先完成 P0 音乐库能力：

- 提供歌曲搜索、详情、批量查询、播放地址接口
- 固定使用 dufs 文件服务：`http://jn_file.88933.vip:27472/`
- 新增 Vue 3 管理后台，用于上传和管理音乐
- 管理后台打包后内嵌进 Spring Boot，一起启动
- ORM 层使用 MyBatis-Plus
- 其他接口继续沿用现有 Controller 定义风格
- 数据库初始化 SQL 放在项目目录内
- 管理后台先使用写死登录：`jiangnan` / `jiangnan123`

> 用户已确认决策：
> - 新增 `spring-boot-starter-validation`
> - 管理后台必须支持上传文件到 dufs
> - 上传文件统一走后端 HTTP POST 接口
## 2. 当前仓库现状

- 已有 Spring Boot 4.1 + Java 21 骨架
- 已有部分 DTO、Api、Service 接口定义
- 已有统一响应结构：`ApiResponse`、`PageResponse`、`ErrorCode`
- 尚未接入数据库实体、Mapper、Controller 实现
- 尚未有管理后台页面
## 3. 技术方案

| 层级 | 技术选型 | 说明 |
| --- | --- | --- |
| 后端框架 | Spring Boot 4.1 + Java 21 | 沿用现有骨架 |
| ORM | MyBatis-Plus 3.5.16 | 利用 BaseMapper、LambdaQueryWrapper、分页能力 |
| 数据库 | MySQL | 存储歌曲元数据 |
| 文件服务 | dufs | 固定地址 `http://jn_file.88933.vip:27472/` |
| 管理后台 | Vue 3 + Vite | 构建产物放入 Spring Boot 静态资源目录 |
| 启动方式 | Spring Boot | 同时提供 API、管理后台页面和 dufs 上传代理 |
| 参数校验 | spring-boot-starter-validation | 新增，用于接口与管理后台参数校验 |
## 4. 目录与产物约定

- SQL 文件：`/Applications/work/workspace/music/schema.sql`
- 管理后台构建产物：`src/main/resources/static/admin`
- 管理后台访问路径：`/music/admin/`
- API 前缀：`/music/api/v1`
- 文件服务目录约定：
  - `/audio/{trackId}.{format}`
  - `/covers/{trackId}.jpg`
  - `/lyrics/{trackId}.lrc`
## 5. 数据库设计

新增 `schema.sql`，核心表结构如下：

```sql
CREATE TABLE IF NOT EXISTS track (
    track_id     VARCHAR(32)   PRIMARY KEY COMMENT '全局唯一歌曲ID，如 T0000421',
    name         VARCHAR(256)  NOT NULL COMMENT '歌曲名',
    artist       VARCHAR(256)  NOT NULL COMMENT '歌手名',
    album        VARCHAR(256)  DEFAULT NULL COMMENT '专辑名',
    cover_url    VARCHAR(512)  DEFAULT NULL COMMENT '封面图片相对路径',
    duration     INT           NOT NULL COMMENT '时长（秒）',
    format       VARCHAR(16)   DEFAULT NULL COMMENT '文件格式：flac/mp3/wav/aac',
    file_size    BIGINT        DEFAULT NULL COMMENT '文件大小（字节）',
    track_number INT           DEFAULT NULL COMMENT '专辑内曲目序号',
    has_lyric    TINYINT(1)    DEFAULT 0 COMMENT '是否有歌词文件',
    lyric_url    VARCHAR(512)  DEFAULT NULL COMMENT '歌词文件相对路径',
    created_at   DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间',
    updated_at   DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_name (name),
    INDEX idx_artist (artist),
    INDEX idx_album (album)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='歌曲元数据表';
```
## 6. MyBatis-Plus 层设计

- 新增 `Track` 实体类
- 使用 `@TableName`、`@TableId` 等 MyBatis-Plus 注解
- 新增 `TrackMapper extends BaseMapper<Track>`
- 搜索查询使用 `LambdaQueryWrapper`
- 分页查询使用 `IPage`
- 不手写 XML SQL，优先使用框架内置能力
## 7. 后端接口实现

### 7.1 P0 接口

保留现有 DTO 与 `TrackService` 接口定义，新增 `TrackController` 实现以下接口：

- `GET /api/v1/tracks/search`
- `GET /api/v1/tracks/{trackId}`
- `GET /api/v1/tracks/batch`
- `GET /api/v1/tracks/{trackId}/media-url`

其中 `/batch` 建议后端按逗号拆分 `trackId`，避免查询参数解析不稳定。

### 7.2 统一能力

- 统一响应继续使用现有 `ApiResponse`
- 新增统一异常处理，映射到现有 `ErrorCode`
- 每个请求自动生成 `traceId`
- 异常响应保持统一结构
## 8. dufs 文件服务对接

- 后端只负责拼接文件服务地址，不直接读写 dufs 文件
- `mediaUrl` 返回格式：`http://jn_file.88933.vip:27472/audio/{trackId}.{format}`
- 管理后台新增 HTTP POST 上传接口，由后端转发文件到 dufs，避免前端直连 dufs
- 上传接口优先做参数校验、文件类型/大小限制和错误映射
## 9. Vue 3 管理后台

- 技术栈：Vue 3 + Vite
- 构建产物放入 `src/main/resources/static/admin`
- 功能范围：
  - 登录页（写死账号校验）
  - 音乐列表
  - 上传音乐
  - 编辑音乐
  - 删除音乐
- 登录写死账号：`jiangnan`
- 登录写死密码：`jiangnan123`
- 访问地址：`/music/admin/`
## 10. 依赖与风险说明

### 10.1 已确认依赖

- Spring Boot 4.1 相关依赖
- MyBatis-Plus 3.5.16
- MySQL Connector
- Lombok
- `spring-boot-starter-validation`
- Spring Boot 内置 HTTP 客户端能力，用于转发上传到 dufs

### 10.2 待确认依赖

- 无
## 11. 实施步骤

1. 在 `pom.xml` 中新增 `spring-boot-starter-validation`
2. 编写 `/Applications/work/workspace/music/schema.sql`
3. 新增 `Track` 实体与 `TrackMapper`
4. 新增 `TrackServiceImpl`
5. 新增 `TrackController`，实现 P0 接口
6. 新增统一异常处理与 `traceId` 支持
7. 新增管理后台登录写死校验
8. 新增管理后台文件上传 HTTP POST 接口，转发到 dufs
9. 开发 Vue 3 管理后台最小版，支持列表、上传、编辑、删除
10. 配置静态资源路径，使管理后台可访问
11. 本地启动验证 P0 接口和管理后台上传流程
## 12. 下一步

等你确认本计划后，我再按上面的实施步骤开始落地代码。
