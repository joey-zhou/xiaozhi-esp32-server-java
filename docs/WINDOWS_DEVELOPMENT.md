# Windows 部署指南

## 系统要求

- Windows 10+，管理员权限

## 1. 安装依赖

| 依赖 | 下载 | 环境变量 | 验证 |
|------|------|----------|------|
| JDK 21 | [Oracle JDK 21](https://www.oracle.com/java/technologies/downloads/#java21) | `JAVA_HOME` → 安装路径，Path 添加 `%JAVA_HOME%\bin` | `java -version` |
| MySQL 8.0 | [MySQL Installer](https://dev.mysql.com/downloads/installer/) | Path 添加 `C:\Program Files\MySQL\MySQL Server 8\bin` | `mysql --version` |
| Maven | [Maven 下载](https://maven.apache.org/download.cgi) | `MAVEN_HOME` → 解压路径，Path 添加 `%MAVEN_HOME%\bin` | `mvn -v` |
| Node.js | [Node.js LTS](https://nodejs.org/) | 安装程序自动配置 | `node -v` |

## 2. 数据库配置

```sql
mysql -u root -p
CREATE DATABASE xiaozhi CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'xiaozhi'@'localhost' IDENTIFIED BY '123456';
GRANT ALL PRIVILEGES ON xiaozhi.* TO 'xiaozhi'@'localhost';
FLUSH PRIVILEGES;
```

> 无需手动导入 SQL，项目集成 Flyway，首次启动自动建表。

## 3. 下载模型和原生库

使用第三方 STT/TTS 服务可只下载基础依赖。在 Git Bash 中执行：

```bash
./scripts/download_models.sh            # 下载全部（模型 + 原生库）
./scripts/download_models.sh status     # 查看状态
```

也可按需单独下载：

```bash
./scripts/download_base.sh              # 基础依赖（VAD 模型 + 原生库）— 必须
./scripts/download_stt.sh               # Vosk STT 模型（使用第三方 STT 可跳过）
./scripts/download_tts.sh               # TTS 模型（使用第三方 TTS 可跳过）
```

> 手动下载：从 [Vosk 模型](https://alphacephei.com/vosk/models) 下载 `vosk-model-cn-0.22`，解压重命名为 `models\vosk-model`。

## 4. 部署

项目采用**双进程架构**：

| 服务 | 端口 | 说明 |
|------|------|------|
| xiaozhi-server | 8091 | 管理后台 API、用户/设备管理 |
| xiaozhi-dialogue | 8092 | 设备对话、AI、WebSocket |

```bash
git clone https://github.com/joey-zhou/xiaozhi-esp32-server-java
cd xiaozhi-esp32-server-java
```

### 方式一：bin 脚本（Git Bash / WSL）

```bash
bin/all.sh start       # 编译并启动
bin/all.sh status      # 查看状态
bin/all.sh restart     # 重启
```

### 方式二：手动启动（CMD / PowerShell）

```bash
mvn clean install -DskipTests

# 终端1：启动管理后台
java -Djava.library.path=lib -jar xiaozhi-server\target\xiaozhi-server-*.jar

# 终端2：启动对话服务
java -Djava.library.path=lib -jar xiaozhi-dialogue\target\xiaozhi-dialogue-*-exec.jar
```

### 前端

```bash
cd web && npm install && npm run dev
```

## 5. 访问

| 服务 | 地址 |
|------|------|
| 前端 | http://localhost:8084 |
| 后台 API | http://localhost:8091 |
| WebSocket | ws://localhost:8092/ws/xiaozhi/v1/ |

默认管理员：admin / 123456

## 常见问题

| 问题 | 解决 |
|------|------|
| 端口冲突 | 修改 `xiaozhi-server\src\main\resources\application.yml` 中的 `server.port` |
| MySQL 连接失败 | 确认 MySQL 服务已启动（服务管理器检查） |
| 构建失败 | `mvn clean install`，确认网络可访问 Maven 中央仓库 |
