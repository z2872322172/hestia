#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
COMPOSE_FILE="$PROJECT_DIR/docker/compose.yaml"
ENV_FILE="$PROJECT_DIR/.env"

if ! command -v docker >/dev/null 2>&1; then
  echo "未找到 docker 命令。" >&2
  exit 1
fi

if ! docker compose version >/dev/null 2>&1; then
  echo "当前 Docker 不支持 docker compose，请升级 Docker Compose。" >&2
  exit 1
fi

compose_args=(-f "$COMPOSE_FILE")
if [[ -f "$ENV_FILE" ]]; then
  compose_args=(--env-file "$ENV_FILE" "${compose_args[@]}")
fi

echo "启动 Hestia 基础设施：$COMPOSE_FILE"
docker compose "${compose_args[@]}" up -d

echo
docker compose "${compose_args[@]}" ps

echo
echo "等待 MySQL/Redis/MinIO 健康检查通过..."
for container in hestia-mysql hestia-redis hestia-minio; do
  for _ in $(seq 1 60); do
    status="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container" 2>/dev/null || true)"
    if [[ "$status" == "healthy" ]]; then
      echo "$container: healthy"
      break
    fi
    sleep 3
  done

  status="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container" 2>/dev/null || true)"
  if [[ "$status" != "healthy" ]]; then
    echo "容器 $container 未通过健康检查，当前状态：$status" >&2
    echo "请执行：docker compose -f $COMPOSE_FILE logs $container" >&2
    exit 1
  fi
done

echo
echo "基础设施已启动："
echo "  MySQL:          localhost:${MYSQL_HOST_PORT:-3306}"
echo "  Redis:          localhost:${REDIS_HOST_PORT:-6379}"
echo "  MinIO API:      http://localhost:${MINIO_API_HOST_PORT:-9000}"
echo "  MinIO Console:  http://localhost:${MINIO_CONSOLE_HOST_PORT:-9001}"
echo "  RocketMQ Proxy: localhost:${ROCKETMQ_PROXY_HOST_PORT:-8081}"
