#!/bin/bash

echo "=== 蓝奏云Java客户端测试脚本 ==="
echo ""

# 检查是否提供了Cookie
if [ -z "$1" ]; then
    echo "用法: ./test_lanzou.sh '你的Cookie'"
    echo ""
    echo "获取Cookie步骤:"
    echo "1. 浏览器访问 https://pc.woozooo.com"
    echo "2. 使用账号 13949121576 登录"
    echo "3. F12 -> Network -> 复制Cookie"
    echo "4. 运行: ./test_lanzou.sh 'phpdisk_info=你的Cookie值'"
    exit 1
fi

COOKIE=$1

echo "使用Cookie: $COOKIE"
echo ""

# 编译项目
echo "编译项目..."
mvn compile -q

# 运行测试
echo "运行测试..."
mvn test -Dtest='LanzouCommandLineTest' -Dcookie="$COOKIE" -q

echo ""
echo "测试完成"
