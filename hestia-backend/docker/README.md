# Hestia 本地中间件

## 包含服务

- MySQL 8.0
- Redis 7.2
- MinIO
- RocketMQ NameServer、Broker、Proxy

## 在 Linux 宿主机上清理旧天机容器

先查看脚本计划删除的容器：

```bash
cd hestia-backend
chmod +x scripts/dev/cleanup-legacy-tianji.sh
./scripts/dev/cleanup-legacy-tianji.sh --dry-run
```

确认后删除 `tj-*` 容器：

```bash
./scripts/dev/cleanup-legacy-tianji.sh
```

如果 `nacos`、`seata`、`sentinel`、`xxljob`、`mq` 也明确属于旧项目：

```bash
./scripts/dev/cleanup-legacy-tianji.sh --include-shared
```

脚本默认不删除 `mysql`、`redis`、镜像和数据卷。

## 启动中间件

```bash
cp .env.example .env
./scripts/dev/start-infra.sh
```

也可以一次完成“清理旧 `tj-*` 容器 + 启动 Hestia 中间件”：

```bash
chmod +x scripts/dev/*.sh
./scripts/dev/bootstrap-infra.sh
```

如果旧 `mysql` 和 `redis` 仍占用 `3306/6379`，修改 `.env`：

```text
MYSQL_HOST_PORT=13306
DB_URL=jdbc:mysql://localhost:13306/hestia?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
REDIS_HOST_PORT=16379
REDIS_PORT=16379
```

同时保持 `DB_URL` 和 `REDIS_PORT` 与新宿主机端口一致。

## 停止

```bash
docker compose --env-file .env -f docker/compose.yaml stop
```

## 删除容器但保留数据卷

```bash
docker compose --env-file .env -f docker/compose.yaml down
```

## 删除数据卷

该操作会清空数据库、Redis、MinIO 和 RocketMQ 数据，只在明确需要重建环境时执行：

```bash
docker compose --env-file .env -f docker/compose.yaml down -v
```
