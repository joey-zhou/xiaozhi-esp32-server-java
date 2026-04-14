# Docker 部署指南

## 前提条件

- [Docker](https://docs.docker.com/get-docker/) + [Docker Compose](https://docs.docker.com/compose/install/)

| 端口 | 服务 |
|------|------|
| 3306 | MySQL |
| 8084 | 前端 |
| 8091 | 管理后台 API（xiaozhi-server） |
| 8092 | 对话服务（xiaozhi-dialogue） |

## 快速开始

```bash
git clone https://github.com/joey-zhou/xiaozhi-esp32-server-java/
cd xiaozhi-esp32-server-java
docker-compose up -d
```

启动 5 个服务：MySQL、Redis、Node 前端、Server 后台、Dialogue 对话。

| 服务 | 地址 |
|------|------|
| 前端界面 | http://localhost:8084 |
| 后台 API | http://localhost:8091 |
| WebSocket | ws://宿主机IP:8092/ws/xiaozhi/v1/ |

默认管理员：admin / 123456

> ESP32 设备连接时需使用宿主机实际 IP，不要用 localhost。

## 模型与原生库

Docker 构建会自动下载：
- **原生库** — sherpa-onnx JNI + onnxruntime + Vosk（linux-x64）
- **VAD 模型** — silero_vad.onnx
- **STT 模型** — Vosk 中文模型
- **TTS 模型** — vits-melo 或 matcha

### 预下载（可选，加速构建）

网络慢可提前下载，Docker 构建时会自动跳过已存在的文件：

```bash
./scripts/download_models.sh all       # 下载所有模型和原生库
./scripts/download_models.sh status    # 查看状态
```

各模块也可独立下载：

```bash
./scripts/download_base.sh             # VAD 模型 + 原生库
./scripts/download_stt.sh              # STT 模型
./scripts/download_tts.sh              # TTS 模型
```

## 环境变量

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `VOSK_MODEL_SIZE` | `small` | Vosk 模型，可选 `standard`（~1.3GB，精度高） |
| `TTS_MODEL` | `vits-melo-tts-zh_en` | TTS 模型，设为 `none` 跳过下载 |

```bash
VOSK_MODEL_SIZE=standard docker-compose up -d
```

## 持久化数据

| 卷名 | 说明 |
|------|------|
| `mysql_data` | MySQL 数据 |
| `redis_data` | Redis 数据 |

## 系统要求

| 配置 | CPU | 内存 | 存储 | 说明 |
|------|-----|------|------|------|
| 最低 | 2核 | 2GB | 10GB | 需第三方 STT/TTS API |
| 推荐 | 2核 | 4GB | 20GB | 本地小模型 |
| 完整 | 4核 | 8GB | 30GB | 本地大模型 |

## 常用命令

```bash
docker-compose logs -f server      # 查看后台日志
docker-compose logs -f dialogue    # 查看对话服务日志
docker-compose ps                  # 查看容器状态
docker-compose down                # 停止
docker-compose down -v             # 停止并删除数据
docker-compose build --no-cache    # 重新构建
```

## 更新

```bash
git pull
docker-compose build
docker-compose up -d
```

## 故障排除

- **容器启动失败**：`docker-compose logs <service_name>` 查看日志
- **数据库连接问题**：`docker-compose ps mysql` 确认状态为 healthy
- **WebSocket 连接失败**：确认使用宿主机 IP 而非 localhost，防火墙开放 8092 端口