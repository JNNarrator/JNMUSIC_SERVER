# JNMusic 构建部署指南

## 前置条件

- Node.js 环境（已安装 npm）
- Java 17+ / Maven 环境
- SSH 访问 `gm` 服务器（`~/.ssh/config` 已配置）
- gm 服务器上已有启动脚本：`/home/jiangnan/music/deepseek_bash_20260708_19322e.sh`

## 快速部署（一条命令）

```bash
cd /Applications/work/workspace/music/admin \
  && npm run build \
  && cp -r dist/* ../src/main/resources/static/ \
  && cd .. \
  && ./mvnw package -DskipTests -q \
  && rsync -avz --progress target/music-0.0.1-SNAPSHOT.jar gm:/home/jiangnan/music/ \
  && ssh gm "cd /home/jiangnan/music && sh deepseek_bash_20260708_19322e.sh restart"
```

## 分步说明

### 1. 构建前端

```bash
cd /Applications/work/workspace/music/admin
npm run build
```

产物在 `admin/dist/` 目录。

### 2. 复制静态资源到 Spring Boot

```bash
cp -r /Applications/work/workspace/music/admin/dist/* /Applications/work/workspace/music/src/main/resources/static/
```

注意：应用部署在 `/music/` 上下文路径，静态资源直接放在 `static/` 根目录（不是 `static/admin/`）。

### 3. 构建 Spring Boot JAR

```bash
cd /Applications/work/workspace/music
./mvnw package -DskipTests -q
```

产物：`target/music-0.0.1-SNAPSHOT.jar`（约 50MB）。

### 4. 上传到 gm 服务器

```bash
rsync -avz --progress /Applications/work/workspace/music/target/music-0.0.1-SNAPSHOT.jar gm:/home/jiangnan/music/
```

如果 rsync 不可用，使用 scp：
```bash
scp /Applications/work/workspace/music/target/music-0.0.1-SNAPSHOT.jar gm:/home/jiangnan/music/
```

### 5. 重启服务

```bash
ssh gm "cd /home/jiangnan/music && sh deepseek_bash_20260708_19322e.sh restart"
```

启动成功后日志路径：`/home/jiangnan/music/logs/music-app.log`

## 验证部署

```bash
# 检查 JAR 文件完整性
ssh gm "ls -lh /home/jiangnan/music/music-0.0.1-SNAPSHOT.jar && unzip -t /home/jiangnan/music/music-0.0.1-SNAPSHOT.jar 2>&1 | tail -2"

# 查看服务进程
ssh gm "ps aux | grep music"

# 查看启动日志
ssh gm "tail -30 /home/jiangnan/music/logs/music-app.log"
```

## 仅提交代码（不部署）

```bash
cd /Applications/work/workspace/music
git add <files>
git commit -m "message"
git push
```

## 项目路径速查

| 项目 | 路径 |
|------|------|
| 前端源码 | `/Applications/work/workspace/music/admin/src/` |
| 前端构建产物 | `/Applications/work/workspace/music/admin/dist/` |
| Spring Boot 静态资源 | `/Applications/work/workspace/music/src/main/resources/static/` |
| Spring Boot JAR | `/Applications/work/workspace/music/target/music-0.0.1-SNAPSHOT.jar` |
| gm 服务器部署目录 | `/home/jiangnan/music/` |
| gm 服务器启动脚本 | `/home/jiangnan/music/deepseek_bash_20260708_19322e.sh` |
| gm 服务器日志 | `/home/jiangnan/music/logs/music-app.log` |

## 文件结构速查（核心文件）

| 文件 | 用途 |
|------|------|
| `admin/src/stores/player.ts` | 播放器核心逻辑（播放、切歌、状态管理） |
| `admin/src/components/PlayerBar.vue` | 底部播放栏组件 |
| `admin/src/components/PlayerPage.vue` | 全屏播放器/歌词页面 |
| `admin/src/components/TrackList.vue` | 歌单列表页面 |
| `admin/src/App.vue` | 根组件 + 布局容器 |
| `admin/src/main.ts` | Vue 入口 + JS 初始化 |
| `admin/src/styles.css` | 全局样式 + 移动端适配 |
| `admin/index.html` | HTML 入口 + PWA meta 标签 |
| `admin/vite.config.ts` | Vite 构建配置 |
| `admin/public/manifest.json` | PWA 清单文件 |
