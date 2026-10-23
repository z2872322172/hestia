#!/usr/bin/env bash

set -euo pipefail

# 只删除明确属于旧天机项目的容器，不删除镜像和数据卷。
# 通用名称的 mysql/redis/nacos 等容器默认不动，避免误删其他项目基础设施。

DRY_RUN=false
INCLUDE_SHARED=false

for arg in "$@"; do
  case "$arg" in
    --dry-run)
      DRY_RUN=true
      ;;
    --include-shared)
      INCLUDE_SHARED=true
      ;;
    -h|--help)
      cat <<'EOF'
用法：
  ./cleanup-legacy-tianji.sh [--dry-run] [--include-shared]

默认删除：
  tj-gateway tj-user tj-auth tj-course tj-trade
  tj-learning tj-data tj-exam

--include-shared 额外删除旧项目常用共享容器：
  nacos seata sentinel xxljob mq

脚本不会删除 mysql、redis、镜像和数据卷。
如果 mysql/redis 属于旧项目，请先备份数据，再手动确认删除。
EOF
      exit 0
      ;;
    *)
      echo "未知参数：$arg" >&2
      exit 2
      ;;
  esac
done

if ! command -v docker >/dev/null 2>&1; then
  echo "未找到 docker 命令。" >&2
  exit 1
fi

targets=(
  tj-gateway
  tj-user
  tj-auth
  tj-course
  tj-trade
  tj-learning
  tj-data
  tj-exam
)

if [[ "$INCLUDE_SHARED" == "true" ]]; then
  targets+=(
    nacos
    seata
    sentinel
    xxljob
    mq
  )
fi

found=0
for name in "${targets[@]}"; do
  if docker container inspect "$name" >/dev/null 2>&1; then
    found=1
    if [[ "$DRY_RUN" == "true" ]]; then
      echo "[dry-run] docker rm -f $name"
    else
      echo "删除容器：$name"
      docker rm -f "$name" >/dev/null
    fi
  fi
done

if [[ "$found" -eq 0 ]]; then
  echo "没有发现需要删除的旧天机容器。"
else
  echo "旧天机容器清理完成。镜像和数据卷未删除。"
fi

echo
echo "当前仍占用常用端口的容器："
docker ps --format 'table {{.Names}}\t{{.Ports}}' \
  | grep -E ':(3306|6379|9000|9001|9876|10909|10911|8081)(->|/|$)' \
  || true

echo
echo "提示：如果 mysql 或 redis 仍占用 3306/6379，请先备份数据并确认归属，"
echo "或在本项目 .env 中改用 MYSQL_HOST_PORT=13306、REDIS_HOST_PORT=16379。"
