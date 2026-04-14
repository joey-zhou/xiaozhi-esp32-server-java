# CentOS 部署指南

## 系统要求

| 项目 | 要求 |
|------|------|
| 系统 | CentOS 7/8 |
| 内存 | ≥ 2GB（推荐 4GB） |
| 磁盘 | ≥ 10GB |
| 端口 | 8084、8091、8092、3306 |

## 1. 安装依赖

```bash
sudo yum install -y epel-release wget curl git vim unzip
sudo yum install -y java-21-openjdk java-21-openjdk-devel maven
curl -sL https://rpm.nodesource.com/setup_22.x | sudo bash -
sudo yum install -y nodejs
```

## 2. 配置防火墙

```bash
sudo firewall-cmd --permanent --add-port={8084,8091,8092,3306}/tcp
sudo firewall-cmd --reload
```

## 3. 安装 MySQL 8.0

```bash
sudo yum localinstall -y https://dev.mysql.com/get/mysql80-community-release-el7-7.noarch.rpm
sudo yum install -y mysql-community-server
sudo systemctl start mysqld && sudo systemctl enable mysqld
sudo grep 'temporary password' /var/log/mysqld.log   # 获取临时密码
sudo mysql_secure_installation
```

创建数据库：

```sql
mysql -u root -p
CREATE DATABASE xiaozhi CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'xiaozhi'@'localhost' IDENTIFIED BY '123456';
GRANT ALL PRIVILEGES ON xiaozhi.* TO 'xiaozhi'@'localhost';
FLUSH PRIVILEGES;
```

> 无需手动导入 SQL，项目集成 Flyway，首次启动自动建表。

## 4. 下载模型和原生库

使用第三方 STT/TTS 服务可只下载基础依赖。

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

## 5. 部署

项目采用**双进程架构**：

| 服务 | 端口 | 说明 |
|------|------|------|
| xiaozhi-server | 8091 | 管理后台 API、用户/设备管理 |
| xiaozhi-dialogue | 8092 | 设备对话、AI、WebSocket |

```bash
git clone https://github.com/joey-zhou/xiaozhi-esp32-server-java
cd xiaozhi-esp32-server-java

# 一键编译并启动
bin/all.sh start

# 查看状态
bin/all.sh status

# 停止 / 重启
bin/all.sh stop
bin/all.sh restart
```

也可单独管理：`bin/server.sh start`、`bin/dialogue.sh start`

前端：

```bash
cd web && npm install && npm run build
```

## 6. Nginx 反向代理（可选）

```nginx
server {
    listen 80;
    server_name your_domain_or_ip;

    location / {
        root /path/to/xiaozhi-esp32-server-java/web/dist;
        try_files $uri $uri/ /index.html;
    }
    location /api {
        proxy_pass http://localhost:8091;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
    location /ws {
        proxy_pass http://localhost:8092;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }
}
```

## 7. 访问

| 服务 | 地址 |
|------|------|
| 前端 | http://your_server_ip:8084 |
| 后台 API | http://your_server_ip:8091 |
| WebSocket | ws://your_server_ip:8092/ws/xiaozhi/v1/ |

默认管理员：admin / 123456

## 维护

```bash
bin/all.sh status                          # 查看状态
tail -f logs/xiaozhi-server.log            # 查看日志
tail -f logs/xiaozhi-dialogue.log
git pull origin main && bin/all.sh restart # 更新并重启
mysqldump -u root -p xiaozhi > backup.sql  # 数据库备份
```

## 常见问题

| 问题 | 解决 |
|------|------|
| MySQL 初始化失败 | `sudo systemctl restart mysqld` |
| 端口冲突 | `netstat -tulnp \| grep <端口>` 找到并 kill 占用进程 |
| 内存不足 | 添加 swap：`sudo dd if=/dev/zero of=/swapfile bs=1M count=2048 && sudo mkswap /swapfile && sudo swapon /swapfile` |
| 模型加载失败 | `chmod -R 755 models` |
