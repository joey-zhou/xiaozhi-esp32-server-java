# Linux 源码部署

以 CentOS / Rocky / Alma 为例，Ubuntu 把 `yum` 换成 `apt`。
只想跑起来用 [Docker 部署](./DOCKER.md) 更省事，本文适合要改代码或要接管进程的场景。

要求：内存 ≥ 4GB（用本地语音模型建议 8GB），磁盘 ≥ 20GB。

## 1. 依赖

```bash
sudo yum install -y epel-release wget curl git unzip
sudo yum install -y java-21-openjdk java-21-openjdk-devel maven
curl -sL https://rpm.nodesource.com/setup_22.x | sudo bash - && sudo yum install -y nodejs

sudo firewall-cmd --permanent --add-port={8084,8091,8092,1883}/tcp
sudo firewall-cmd --permanent --add-port=1884/udp
sudo firewall-cmd --reload
```

## 2. MySQL 与 Redis

用容器起最省事：

```bash
docker compose -f docker-compose-db.yml up -d
```

已有 MySQL 8.0 就建库建号（**不用导入 SQL，Flyway 首次启动自动建表**）：

```sql
CREATE DATABASE xiaozhi CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'xiaozhi'@'localhost' IDENTIFIED BY '123456';
GRANT ALL PRIVILEGES ON xiaozhi.* TO 'xiaozhi'@'localhost';
FLUSH PRIVILEGES;
```

口令或地址不同时用环境变量覆盖：`SPRING_DATASOURCE_PASSWORD`、`SPRING_DATA_REDIS_HOST`。

## 3. 模型与原生库

```bash
git clone https://github.com/joey-zhou/xiaozhi-esp32-server-java
cd xiaozhi-esp32-server-java

./scripts/download_models.sh          # 全部
./scripts/download_base.sh            # 只要基础依赖（VAD + 原生库），语音全用云端时够用
./scripts/download_models.sh status   # 查看状态
```

## 4. 启动

双进程：`xiaozhi-server`(8091) 管后台与 OTA，`xiaozhi-dialogue`(8092) 管设备对话。

```bash
bin/all.sh start           # 自检 → 编译 → 启动，随后跟随日志（Ctrl+C 只退出跟随）
bin/all.sh start prod      # 用生产配置
bin/all.sh status / stop / restart / logs
```

启动前会自检 JDK 版本、模型与原生库、MySQL/Redis 连通性，缺什么直接给出补法，
`SKIP_PREFLIGHT=1` 可跳过。也可单独管理：`bin/server.sh`、`bin/dialogue.sh`。

前端：`cd web && npm install && npm run build`，产物在 `web/dist`。

## 5. Nginx

把前端、API、WebSocket 收敛到一个端口：

```nginx
server {
    listen 80;
    server_name your_domain_or_ip;

    location / {
        root /path/to/xiaozhi-esp32-server-java/web/dist;
        try_files $uri $uri/ /index.html;
    }
    location /api/ {
        proxy_pass http://127.0.0.1:8091;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        client_max_body_size 100M;
        proxy_read_timeout 300s;
    }
    location ~ ^/(audio|uploads)/ {
        proxy_pass http://127.0.0.1:8091;
        proxy_set_header Host $host;
    }
    location /ws/ {
        proxy_pass http://127.0.0.1:8092;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_read_timeout 3600s;
    }
}
```

挂了 Nginx 要配 `XIAOZHI_SECURITY_TRUSTED_PROXIES`，否则限流与封禁会把所有请求算成同一个 IP。

配 HTTPS 就把上面这段挪到 `listen 443 ssl` 里：前端不写死协议，页面是 https 时
WebSocket 自动用 wss，不需要重新构建。设备侧的下发地址要走 https/wss 则需配
`XIAOZHI_SERVER_DOMAIN`，并把 `ws.<域名>` 也解析过来。

## 6. systemd（可选）

```ini
# /etc/systemd/system/xiaozhi-server.service
[Unit]
Description=xiaozhi-server
After=network.target

[Service]
WorkingDirectory=/opt/xiaozhi-esp32-server-java
Environment=SPRING_PROFILES_ACTIVE=prod
Environment=XIAOZHI_DEVICE_AUTH_SECRET=换成自己的随机值
ExecStart=/usr/bin/java -Djava.library.path=/opt/xiaozhi-esp32-server-java/lib \
  -jar /opt/xiaozhi-esp32-server-java/xiaozhi-server/target/xiaozhi-server-6.0.0.jar
Restart=always

[Install]
WantedBy=multi-user.target
```

`xiaozhi-dialogue` 照抄一份换成 `xiaozhi-dialogue/target/xiaozhi-dialogue-*-exec.jar`，
**两个服务的 `XIAOZHI_DEVICE_AUTH_SECRET` 必须一致**。

## 7. 访问

前端 `http://<IP>:8084`，账号 **admin / 123456**，登录后先改密码。

还要配大模型密钥才能对话，见[配置说明](./CONFIGURATION.md#第一次使用要配什么)；
对外部署前要换掉的默认密钥见[安全相关的默认值](./CONFIGURATION.md#安全相关的默认值)。

日常维护：

```bash
bin/all.sh status
git pull origin main && bin/all.sh restart
mysqldump -u root -p xiaozhi > backup.sql
```

遇到问题看[常见问题](./FAQ.md)。
