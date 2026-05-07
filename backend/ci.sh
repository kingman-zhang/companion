#!/bin/bash

set -e

# =========================
# 基础配置
# =========================

APP_NAME="companion"
SERVER_USER="ubuntu"
SERVER_IP="119.29.14.238"
SERVER_APP_DIR="/data/app"

# 本地项目目录：默认当前目录
#PROJECT_DIR=$(pwd)
PROJECT_ROOT_DIR="./src"
PROJECT_API_MODULE_DIR="./companion-api"

# 时间版本号
VERSION=$(date +"%Y%m%d%H%M%S")

# 打包后的远程文件名
PACKAGE_NAME="${APP_NAME}-${VERSION}.jar"

echo "=============================="
echo "开始部署：$APP_NAME"
echo "版本号：$VERSION"
echo "=============================="

# =========================
# 1. 本地打包
# =========================

echo "1. 开始 Maven 打包..."

cd "$PROJECT_ROOT_DIR"

mvn -B clean package -Dmaven.test.skip=true -Dautoconfig.skip

echo "Maven 打包完成"

# =========================
# 2. 找到 jar 包
# =========================

echo "进入API模块目录：$PROJECT_API_MODULE_DIR"
cd "$PROJECT_API_MODULE_DIR"


JAR_FILE=$(find target -maxdepth 1 -name "*.jar" ! -name "*sources.jar" ! -name "*javadoc.jar" | head -n 1)

if [ -z "$JAR_FILE" ]; then
  echo "未找到 jar 包，部署失败"
  exit 1
fi

echo "找到 jar 包：$JAR_FILE"

# =========================
# 3. 复制成带版本号的包
# =========================

cp "$JAR_FILE" "target/$PACKAGE_NAME"

echo "生成版本包：target/$PACKAGE_NAME"

# =========================
# 4. 上传到服务器
# =========================

echo "2. 上传 jar 到服务器..."

# =========================
# 如果你每次都要输入密码，可以配置 SSH 免密：
# ssh-keygen
# ssh-copy-id -i ~/.ssh/id_xxx.pub ${SERVER_USER}@${SERVER_IP}
# =========================


scp "target/$PACKAGE_NAME" "${SERVER_USER}@${SERVER_IP}:${SERVER_APP_DIR}/releases/${PACKAGE_NAME}"

echo "上传完成"

# =========================
# 5. 服务器部署并重启
# =========================

echo "3. 远程部署并启动应用..."

ssh "${SERVER_USER}@${SERVER_IP}" << EOF
set -e

cd ${SERVER_APP_DIR}


echo "切换 app.jar 到新版本（类似Windows的快捷方式）..."
ln -sfn releases/${PACKAGE_NAME} ${SERVER_APP_DIR}/app.jar

echo "重新构建并启动 Java 应用..."
docker compose up -d --build app

echo "清理旧版本，只保留最近 5 个..."
ls -t releases/*.jar | tail -n +6 | xargs -r rm -f

echo "查看容器状态..."
docker ps

echo "查看最近日志..."
docker logs --tail=50 companion-app
EOF

echo "=============================="
echo "部署完成"˚
echo "版本：$PACKAGE_NAME"
echo "=============================="
