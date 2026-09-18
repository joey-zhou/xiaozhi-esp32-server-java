# 配置说明

部署步骤见 [Docker 部署](./DOCKER.md) / [CentOS 部署](./CENTOS_DEVELOPMENT.md) / [Windows 部署](./WINDOWS_DEVELOPMENT.md)。

## 第一次使用要配什么

登录（**admin / 123456**）后还不能直接对话——**AI 服务商密钥要自己填**，系统不预置任何密钥。

**1. 配一个大模型**（必须）

配置管理 → 模型配置 → 新增。下拉里有 50 多家服务商，多数只要一个 API Key：

| 服务商 | 要填什么 |
|--------|----------|
| DeepSeek / ZHIPU-AI | API Key |
| Ollama（本地模型） | 接口地址，如 `http://<主机>:11434`；Docker 部署要填宿主机 IP |
| OpenAI-API-Compatible | 兼容接口地址 + Key |

**2. 语音识别**（可跳过）

默认用本地 SenseVoice，零成本不联网，Docker 镜像已内置模型；源码部署要先跑
`./scripts/download_stt.sh`。要用云端就在 配置管理 → 语音识别配置 里新增。

**3. 语音合成**（可跳过）

默认是免费的 Edge TTS，不需要密钥。换音色或换服务商去 配置管理 → 语音合成配置。

**4. 绑定**

角色配置 → 编辑角色，选好模型、语音识别、音色并保存。
再 设备管理 → 添加设备 → 输入设备上电播报的**验证码**，绑定后为它选角色。

没有硬件也可以先用页面上的**网页聊天**试，走的是同一条对话链路。

## 运行环境（profile）

默认都是 `dev`，走生产要显式指定：

| 启动方式 | 切到 prod |
|---|---|
| `bin/all.sh` / `bin\all.ps1` | `bin/all.sh start prod` |
| `java -jar` | `SPRING_PROFILES_ACTIVE=prod java -jar ...` |
| `docker compose` | `.env` 里写 `SPRING_PROFILES_ACTIVE=prod` |

优先级是**环境变量 > 命令行参数 > yml**，所以容器里即使跑 dev，
数据库地址也是 compose 传进去的那套。

## 环境变量

改配置优先用环境变量，不要改仓库里的 yml，升级时不会冲突。

**数据库与中间件**

| 变量 | 默认值 |
|------|--------|
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | `localhost:3306/xiaozhi`、`xiaozhi`、`123456` |
| `SPRING_DATA_REDIS_HOST` / `_PORT` / `_PASSWORD` | `localhost`、`6379`、空 |

Redis 没开 requirepass 时 `_PASSWORD` 必须留空，空串会被当口令发出去。

**服务地址**

| 变量 | 说明 |
|------|------|
| `HOST_IP` | 下发给设备的服务器 IP，留空自动探测 |
| `XIAOZHI_SERVER_DOMAIN` | 对外域名。留空按 IP 拼地址；填了则下发 `https://<域名>/api/device/ota` 与 `wss://ws.<域名>/ws/xiaozhi/v1/`，要自己把子域名反代过去 |
| `XIAOZHI_SECURITY_TRUSTED_PROXIES` | 前面挂 Nginx/LB 时填代理 IP 或 CIDR，否则限流会把所有请求算成同一个 IP |

改了服务端口要同步改 `xiaozhi.server.port` 与 `xiaozhi.dialogue.port`，否则下发给设备的地址还是旧端口。

### 安全相关的默认值

**都带着可用的默认值，不改也能跑，但对外部署前必须换掉。** 随机值用 `openssl rand -hex 16`。

| 变量 | 默认值 | 不改的后果 |
|------|--------|-----------|
| `XIAOZHI_DEVICE_AUTH_SECRET` | `xz-2026` | 任何人报一个 device-id 就能接进对话链路。server 与 dialogue 必须同值 |
| `JWT_SECRET_KEY` | 内置字符串 | 后台登录 token 可被伪造 |

**存储与其它**

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `XIAOZHI_DATA_DIR` | 空 | 录音与上传文件的根目录。server 与 dialogue 在不同目录启动时，**必须设成同一个绝对路径** |
| `EMAIL_SMTP_USERNAME` / `_PASSWORD` | 空 | 注册与找回密码的验证码邮件，不配则发信失败 |

## 可选组件

| 能力 | 前置条件 | 不满足时 |
|------|----------|----------|
| 本地语音识别（SenseVoice） | `models/sense-voice` + `lib/` | 退到 Vosk，都没有就只能用云端 |
| 本地语音识别兜底（Vosk） | `models/vosk-model` | 无影响，SenseVoice 在时它不会被加载 |
| 本地语音合成 | `models/tts` | 用云端或 Edge TTS |

Docker 镜像已内置全部本地模型与原生库。源码部署按需下载：

```bash
./scripts/download_base.sh        # 原生库 + VAD 模型，必须
./scripts/download_stt.sh         # 本地识别模型
./scripts/download_tts.sh         # 本地合成模型
./scripts/download_models.sh      # 以上全部
```

## 端口

| 端口 | 进程 | 用途 |
|------|------|------|
| 8084 | web | 管理界面，容器部署时同时反代 API 与 WebSocket |
| 8091 | server | 后台 API、OTA、静态资源 |
| 8092 | dialogue | 设备与网页聊天的 WebSocket |
| 3306 / 13306 | MySQL | 源码部署 3306，Docker 一键部署 13306 |
| 6379 | Redis | |
