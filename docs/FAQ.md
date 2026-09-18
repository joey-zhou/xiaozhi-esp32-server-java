# 常见问题

## 部署

**容器一直不 healthy**
`docker compose logs <服务名>` 看日志。server 首次启动要跑完 Flyway 建表，到 healthy 可能要 1 分钟。

**dialogue 迟迟不启动**
它等 server 变 healthy 才起（建表由 server 负责），先排 server。

**容器反复重启，或构建报 `cannot allocate memory`**
Docker Desktop 的内存配额太小（默认可能只有 2GB，而 server 与 dialogue 各自上限就是 2GB）。
Settings → Resources 调到 8GB。当前配额：`docker info | grep -i "total memory"`。

**拉镜像失败（manifest unknown / 超时）**
改用[源码构建](./DOCKER.md#从源码构建)，或在 `.env` 里用 `XIAOZHI_IMAGE_SERVER` 等指向自己的镜像仓库。

**端口被占用**
在 `.env` 里改 `WEB_PORT` / `SERVER_PORT` / `DIALOGUE_PORT`。

**页面能打开，接口 502**
server 还没 healthy，`docker compose ps server`。

## 源码启动

**自检报 JDK 版本过低**
`java -version` 要是 21+。多版本共存时设 `JAVA_HOME`，或直接指定 `JAVA_BIN`。

**自检报 MySQL / Redis 连不上**
中间件没起（`docker compose -f docker-compose-db.yml up -d`），
或地址不是默认值——用 `SPRING_DATASOURCE_URL` / `SPRING_DATA_REDIS_HOST` 指过去。

**启动报找不到原生库**
`./scripts/download_base.sh`，然后确认 `lib/` 非空。Linux 下可能还要 `chmod -R 755 models lib`。

**构建失败**
`mvn clean install`，确认网络可访问 Maven 中央仓库。

## 使用

**能登录，但设备说话没反应**
没配大模型密钥。见[配置说明](./CONFIGURATION.md#第一次使用要配什么)。

**设备连不上**
用服务器的局域网 IP，不要用 localhost；`.env` 里设 `HOST_IP`；防火墙放行 8091、8092。
服务端认为自己的地址对不对，看 `docker compose logs dialogue | head -40` 的启动横幅。

**网页聊天连不上 WebSocket**
前端走同源 `/ws/`，由 web 容器反代到 dialogue，确认 dialogue 已 healthy。

**为什么 SenseVoice 和 Vosk 两个识别模型都下载了**
启动时 SenseVoice 加载成功就用它，Vosk 不会被初始化，它只是模型缺失或损坏时的兜底。
不想要可以设 `VOSK_MODEL_SIZE=none`。

**忘了管理员密码**
口令是 BCrypt，不能直接写明文。在数据库里重置回出厂的 `123456`：

```sql
UPDATE sys_user SET password = '$2a$10$Kszen1V6r4y3z3CODLVu.ORMy7xGt0W7Br1tnt8FsVepDO/t13i4W'
WHERE username = 'admin';
```
