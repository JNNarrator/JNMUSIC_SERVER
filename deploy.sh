#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
ADMIN_DIR="$PROJECT_DIR/admin"
STATIC_DIR="$PROJECT_DIR/src/main/resources/static"
DEPLOY_HOST="gm"
DEPLOY_PATH="/home/jiangnan/music"
RESTART_SCRIPT="$DEPLOY_PATH/deepseek_bash_20260708_19322e.sh"
JAR_FILE="$PROJECT_DIR/target/music-0.0.1-SNAPSHOT.jar"
BACKUP_JAR="$DEPLOY_PATH/music-0.0.1-SNAPSHOT.jar.bak"

echo "==> 1/6  构建前端..."
cd "$ADMIN_DIR"
npm run build --silent

echo "==> 2/6  清理旧静态资源..."
rm -rf "$STATIC_DIR/assets"
cp -r "$ADMIN_DIR/dist/"* "$STATIC_DIR/"
cp -r "$ADMIN_DIR/dist/assets/"* "$STATIC_DIR/assets/"

echo "==> 3/6  构建 Spring Boot JAR..."
cd "$PROJECT_DIR"
./mvnw package -DskipTests -q

echo "==> 4/6  上传到 $DEPLOY_HOST..."
# 备份线上 JAR（保留一份回滚）
rsync -avz --progress "$JAR_FILE" "$DEPLOY_HOST:$BACKUP_JAR"
rsync -avz --progress "$JAR_FILE" "$DEPLOY_HOST:$DEPLOY_PATH/"

echo "==> 5/6  重启服务..."
# stop 可能因进程已不存在而失败，|| true 确保 start 总能执行
ssh "$DEPLOY_HOST" "cd $DEPLOY_PATH && sh $RESTART_SCRIPT stop 2>/dev/null || true && sleep 2 && sh $RESTART_SCRIPT start"

echo "==> 6/6  验证..."
sleep 3
ssh "$DEPLOY_HOST" "tail -5 $DEPLOY_PATH/logs/music-app.log"

echo ""
echo "=== 部署完成 ==="
echo "如需回滚: ssh $DEPLOY_HOST 'cp $BACKUP_JAR $DEPLOY_PATH/music-0.0.1-SNAPSHOT.jar && cd $DEPLOY_PATH && sh $RESTART_SCRIPT start'"
