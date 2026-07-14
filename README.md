# JNMusic · 夜猫电台

一个简洁优雅的在线音乐播放器。后端使用 Spring Boot 提供 API，前端为 Vue 3 单页应用。

音乐文件存储在蓝奏云网盘，歌词文件（`.txt`）跟随在歌曲文件夹中。

## 技术栈

| 层 | 技术 |
| --- | --- |
| 后端 | Spring Boot 4.1, Java 21, MySQL / H2, MyBatis-Plus |
| 前端 | Vue 3.4, Pinia, Vant 4, Element Plus, Vite 5.4 |
| 部署 | systemd, Nginx 反向代理 |
| 存储 | 蓝奏云网盘 (Lanzou API) |

## 项目结构

```
music/
├── admin/                          # 前端 Vue 应用
│   ├── src/
│   │   ├── components/             # Vue 组件
│   │   │   ├── App.vue             # 根布局（顶栏、歌单、播放栏）
│   │   │   ├── TrackList.vue       # 歌单列表
│   │   │   ├── PlayerBar.vue       # 底部播放控制栏
│   │   │   ├── PlayerPage.vue      # 全屏播放器（歌词）
│   │   │   ├── LanzouAuthPanel.vue # 蓝奏云认证面板
│   │   │   ├── LyricsPanel.vue     # 歌词面板
│   │   │   └── BrandLogo.vue       # 品牌 Logo
│   │   ├── stores/
│   │   │   ├── player.ts           # 播放器状态（队列、播放模式）
│   │   │   ├── theme.ts            # 主题/夜间模式
│   │   │   └── ui.ts               # UI 状态
│   │   ├── utils/lrc.ts            # LRC 歌词解析/缓存
│   │   └── styles.css              # 全局样式与主题变量
│   └── public/
│       ├── manifest.json           # PWA 清单
│       ├── sw.js                   # Service Worker
│       └── icon-*.png / screenshot-*.*  # PWA 资源
│
├── src/main/java/com/jn/music/
│   ├── admin/controller/           # 蓝奏云认证管理接口
│   ├── common/                     # 通用（配置、异常、日志、枚举）
│   ├── lanzou/                     # 蓝奏云 API 客户端
│   │   ├── LanzouApiClient.java    # HTTP 客户端（文件列表/上传/直链）
│   │   └── config/                 # 客户端配置
│   ├── storage/                    # 存储抽象层
│   │   └── lanzou/                 # 蓝奏云存储实现
│   ├── track/                      # 歌曲管理
│   │   ├── controller/             # REST API
│   │   ├── service/                # 业务逻辑 + 缓存
│   │   └── mapper/                 # MyBatis Mapper
│   └── user/                       # 用户数据（收藏、历史、队列、搜索）
│
├── src/main/resources/
│   ├── static/                     # 编译后的前端静态文件
│   ├── schema-h2.sql               # 开发环境 H2 表结构
│   ├── schema.sql                  # 生产环境 MySQL 表结构
│   └── application*.properties     # 配置
│
└── deploy.sh                       # 部署脚本
```

## 开发

### 前置要求

- Java 21+
- Node.js 20+
- Maven 3.8+

### 后端

```bash
# 开发环境使用 H2 内存数据库，无需安装 MySQL
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

默认端口 19001，上下文路径 `/music`。

### 前端

```bash
cd admin
npm install
npm run dev        # 开发服务器，默认 5173
npm run build      # 生产构建，输出到 dist/
```

开发时 Vite 代理 API 请求到后端 19001。

## 部署

```bash
bash deploy.sh
```

自动执行：
1. 构建前端 → 复制到 `src/main/resources/static/`
2. Maven 打包 JAR
3. 上传到服务器 (`gm`)
4. 重启 service

## 配置

### 蓝奏云认证

通过前端面板输入 Cookie 或账号密码。认证信息保存在浏览器本地，不经过服务端存储。

### 文件服务

配置项在 `application.properties`：

```properties
jnmusic.file-server.public-base-url=   # 客户端直链访问地址
jnmusic.file-server.internal-base-url= # 服务端内部地址
```

## PWA

支持移动端和桌面端安装。可在 Chrome DevTools → Application → Manifest 查看清单状态。

- 图标：192×192 / 512×512 PNG
- 截图：竖屏 390×844 / 横屏 1280×800
- 车机模式：URL 参数 `?car=1` 或宽屏自动检测（宽高比 >1.5）
- iOS PWA：安全区域适配、锁屏播放控制

## API

| 路径 | 说明 |
| --- | --- |
| `GET /api/v1/tracks` | 分页获取歌曲列表 |
| `GET /api/v1/tracks/search?q=` | 搜索歌曲 |
| `GET /api/v1/tracks/{id}/media-url` | 获取播放直链 |
| `GET /api/v1/tracks/media-urls?ids=` | 批量获取播放直链 |
| `GET /api/v1/tracks/{id}/lyrics` | 获取歌词 |
| `GET /api/v1/admin/lanzou/status` | 蓝奏云认证状态 |
| `POST /api/v1/admin/lanzou/cookie` | Cookie 认证 |
| `POST /api/v1/admin/lanzou/login` | 账号密码登录 |
| `POST /api/v1/admin/lanzou/refresh-cache` | 刷新直链缓存 |

## 播放模式

- **列表循环**：播完最后一首回到开头
- **单曲循环**：自然结束时重播当前曲，手动切歌正常前进
- **随机播放**：每次随机选曲
