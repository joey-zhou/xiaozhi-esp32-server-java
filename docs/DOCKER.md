# Docker 部署

前置条件：装好 Docker（`docker compose version` 能输出版本）。不需要 clone 仓库。

## 1. 启动

```bash
mkdir xiaozhi && cd xiaozhi
curl -O https://raw.githubusercontent.com/joey-zhou/xiaozhi-esp32-server-java/main/docker-compose.yml
docker compose up -d
```

起 5 个容器：MySQL、Redis、server、dialogue、web。表由 Flyway 自动建，不用导入 SQL。

成功的标志是所有容器都 healthy（首次约 1～2 分钟）：

```bash
docker compose ps
```

## 2. 登录

打开 <http://localhost:8084>，账号 **admin / 123456**，登录后尽快改掉密码。

| 服务 | 地址 |
|------|------|
| 管理界面 | http://localhost:8084 |
| 后台 API / OTA | http://localhost:8091 |
| 设备 WebSocket | ws://localhost:8092/ws/xiaozhi/v1/ |

## 3. 配一个大模型

**系统起来了不等于能对话**，大模型密钥要自己填：配置管理 → 模型配置 → 新增。
语音识别默认用镜像内置的本地模型，语音合成默认用免费的 Edge TTS，都可以先不管。

完整步骤见[配置说明](./CONFIGURATION.md#第一次使用要配什么)。

## 4. 连接设备

设备要连宿主机的**局域网 IP**，不能用 localhost：

- OTA：`http://<宿主机IP>:8091/api/device/ota`
- WebSocket：`ws://<宿主机IP>:8092/ws/xiaozhi/v1/`

Linux 下容器能自动探测到这个 IP；**macOS / Windows 必须手填**：

```bash
curl -O https://raw.githubusercontent.com/joey-zhou/xiaozhi-esp32-server-java/main/.env.example
mv .env.example .env        # 编辑 HOST_IP=192.168.1.100
docker compose up -d server dialogue
```

烧录固件见[固件编译文档](./FIRMWARE-BUILD.md)。

## 常用命令

```bash
docker compose ps                  # 状态
docker compose logs -f server      # 日志
docker compose restart server      # 重启单个服务
docker compose pull && docker compose up -d   # 升级
docker compose down                # 停止，数据保留
docker compose down -v             # 停止并删除全部数据
```

> 从 6.0.0 之前的版本升级：compose 现在固定项目名 `xiaozhi`，数据卷名也跟着变。
> 想继续用原来的卷，先在 `.env` 里写 `COMPOSE_PROJECT_NAME=<原来的目录名>`，
> 否则会挂到新的空卷上（旧数据还在，只是没被用到）。

数据都在 Docker 命名卷里（`xiaozhi_mysql_data`、`xiaozhi_audio_data` 等），备份数据库：

```bash
docker compose exec mysql mysqldump -u xiaozhi -p123456 xiaozhi > backup.sql
```

## 自定义配置

不改任何文件也能跑。要改就下载 `.env.example` 存成 `.env`，只写需要改的行，
再执行 `docker compose up -d`。常用项：

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `XIAOZHI_TAG` | `latest` | 镜像版本，线上建议钉具体版本号 |
| `WEB_PORT` / `SERVER_PORT` / `DIALOGUE_PORT` | 8084 / 8091 / 8092 | 端口冲突时改 |
| `MYSQL_PASSWORD` | `123456` | 首次启动后再改要重建数据卷 |
| `SPRING_PROFILES_ACTIVE` | `dev` | 生产改 `prod` |
| `HOST_IP` | 空 | 下发给设备的服务器 IP |
| `XIAOZHI_DEVICE_AUTH_SECRET` | 空 | 设备接入鉴权密钥 |

全部配置项见[配置说明](./CONFIGURATION.md#环境变量)。

## 对外部署前

1. **开设备鉴权**，否则任何人报一个 device-id 就能接进来消耗你的模型额度：

   ```bash
   echo "XIAOZHI_DEVICE_AUTH_SECRET=$(openssl rand -hex 16)" >> .env
   docker compose up -d server dialogue
   ```

2. 改掉数据库默认口令，确认 MySQL 没对公网开放（默认只绑 `127.0.0.1`）。
3. `.env` 里设 `SPRING_PROFILES_ACTIVE=prod`，并改掉 admin 默认密码。

其余默认密钥见[安全相关的默认值](./CONFIGURATION.md#安全相关的默认值)。

## 从源码构建

改了代码，或者拉不到 `ghcr.io` 的镜像时（需要 clone 仓库）：

```bash
docker compose -f docker-compose.yml -f docker-compose.build.yml up -d --build
```

首次构建要编译全部 Java 模块并下载约 1.5GB 模型，**耗时 20～40 分钟**。
国内网络可以在 `.env` 里配 `BUILD_PROXY`，或先用 `./scripts/download_models.sh` 把模型下好
（构建会自动跳过已存在的文件）。

镜像内容：server 与 dialogue 含本地模型 + 原生库（体积几个 GB），web 是前端产物 + nginx。

本地模型（构建时已内置）：

- **原生库** — sherpa-onnx JNI + onnxruntime + Vosk
- **VAD** — silero_vad.onnx
- **STT** — sherpa-onnx SenseVoice-Small（首选，~230MB）与 Vosk 中文模型（兜底）。
  启动时 SenseVoice 加载成功就直接用它，Vosk 不会被初始化
- **TTS** — vits-melo（默认）或 matcha

## 只跑数据库容器

想让 Java 服务跑在宿主机上、只把 MySQL 与 Redis 放进容器（本地开发常用）：

```bash
docker compose -f docker-compose-db.yml up -d
bin/all.sh start
```

这套 compose 把 MySQL 映射在 **3306**（不是 13306），与 `application-dev.yml` 的默认值对齐。

## 系统要求

| 配置 | CPU | 内存 | 磁盘 |
|------|-----|------|------|
| 最低 | 2核 | 4GB | 20GB |
| 推荐 | 4核 | 8GB | 30GB |

macOS / Windows 还要看 Docker Desktop 自己的配额（Settings → Resources），
默认可能只有 2GB，而 server 与 dialogue 各自的上限就是 2GB，不调大会被反复 OOM kill。

遇到问题看[常见问题](./FAQ.md)。
