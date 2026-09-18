# Windows 部署

只想跑起来：装 [Docker Desktop](https://www.docker.com/products/docker-desktop/) 后按
[Docker 部署](./DOCKER.md) 走，注意要在 `.env` 里填 `HOST_IP` 设备才能连上。
本文是在 Windows 上跑源码的做法。

## 1. 依赖

| 依赖 | 下载 | 环境变量 | 验证 |
|------|------|----------|------|
| JDK 21 | [Temurin 21](https://adoptium.net/temurin/releases/?version=21) | `JAVA_HOME`，Path 加 `%JAVA_HOME%\bin` | `java -version` |
| Maven | [maven.apache.org](https://maven.apache.org/download.cgi) | `MAVEN_HOME`，Path 加 `%MAVEN_HOME%\bin` | `mvn -v` |
| Node.js 22 | [nodejs.org](https://nodejs.org/) | 安装程序自动配置 | `node -v` |
| MySQL 8.0 | [MySQL Installer](https://dev.mysql.com/downloads/installer/) | Path 加 MySQL 的 `bin` | `mysql --version` |
| Redis | 用 Docker 或 [Memurai](https://www.memurai.com/) | — | `redis-cli ping` |

中间件用容器起最省事：

```powershell
docker compose -f docker-compose-db.yml up -d   # MySQL + Redis
```

## 2. 数据库

用了上面的 compose 就已经建好，可跳过。自己装的 MySQL 执行：

```sql
CREATE DATABASE xiaozhi CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'xiaozhi'@'localhost' IDENTIFIED BY '123456';
GRANT ALL PRIVILEGES ON xiaozhi.* TO 'xiaozhi'@'localhost';
FLUSH PRIVILEGES;
```

无需导入 SQL，Flyway 首次启动自动建表。口令不同时设 `$env:SPRING_DATASOURCE_PASSWORD`。

## 3. 模型与原生库

下载脚本是 bash 写的，在 **Git Bash** 里执行：

```bash
./scripts/download_models.sh          # 全部
./scripts/download_base.sh            # 只要基础依赖（VAD + 原生库）
```

也可手动下载：[SenseVoice](https://github.com/k2-fsa/sherpa-onnx/releases/tag/asr-models) 的
`sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17.tar.bz2`，解压重命名为 `models\sense-voice`
（保留 `model.int8.onnx` 与 `tokens.txt`）。

## 4. 启动

```powershell
bin\all.ps1 start        # 自检 → 编译 → 启动
bin\all.ps1 start prod   # 用生产配置
bin\all.ps1 status / stop / restart
```

启动前会自检 JDK 版本、模型与原生库、MySQL/Redis 连通性，`$env:SKIP_PREFLIGHT = '1'` 可跳过。
提示脚本被禁止执行时：`Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass`。

Git Bash 里也可以用 `bin/all.sh start`。前端：`cd web && npm install && npm run dev`。

## 5. 访问

前端 <http://localhost:8084>，账号 **admin / 123456**。

还要配大模型密钥才能对话，见[配置说明](./CONFIGURATION.md#第一次使用要配什么)。
遇到问题看[常见问题](./FAQ.md)。
