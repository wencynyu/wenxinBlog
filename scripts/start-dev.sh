#!/bin/bash
# WenxinBlog 一键启动脚本（本地开发）
# 用法: ./scripts/start-dev.sh [start|stop|restart|status]
#
# 启动所有应用服务（限堆防 OOM）+ docker 基建 + web 前端

set -e

JWT_SECRET="wenxinblog-mvp-jwt-secret-dev-only"
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

# --- 服务定义：name:port:dir:java_heap ---
SERVICES=(
  "auth:8001:services/auth-service::-"
  "user:8002:services/user-service::-"
  "blog:8003:services/blog-service:512"
  "search:8005:services/search-service:384"
  "recommend:8006:services/recommendation-service:384"
  "content:8004:services/content-service:384"
  "ad:8007:services/ad-service:384"
  "gateway:8080:services/gateway:256"
)

start_service() {
  local name=$1 port=$2 dir=$3 heap=$4
  if lsof -ti:$port >/dev/null 2>&1; then
    echo "  $name($port) already running"
    return
  fi
  cd "$ROOT_DIR/$dir"
  local jvm_args=""
  if [ "$heap" != "-" ] && [ -n "$heap" ]; then
    jvm_args="-Dspring-boot.run.jvmArguments=-Xmx${heap}m"
  fi

  if [ -f "cmd/server/main.go" ]; then
    # Go service
    JWT_SECRET="$JWT_SECRET" DATABASE_URL="postgres://postgres:postgres@localhost:$(echo $name | grep -q auth && echo 5432 || echo 5433)/${name}_db?sslmode=disable" \
      nohup go run ./cmd/server > "/tmp/svc-$name.log" 2>&1 &
  else
    # Java service
    JWT_SECRET="$JWT_SECRET" nohup mvn -q -ntp -Dmaven.test.skip=true $jvm_args spring-boot:run > "/tmp/svc-$name.log" 2>&1 &
  fi
  cd "$ROOT_DIR"
  echo "  $name($port) starting..."
}

stop_service() {
  local name=$1 port=$2
  local pid=$(lsof -ti:$port 2>/dev/null)
  if [ -n "$pid" ]; then
    kill -9 $pid 2>/dev/null
    # Also kill parent Maven process
    pkill -9 -f "spring-boot:run.*$name" 2>/dev/null || true
    echo "  $name($port) stopped"
  else
    echo "  $name($port) not running"
  fi
}

wait_for_port() {
  local port=$1 timeout=${2:-30}
  for i in $(seq 1 $timeout); do
    curl -sf --max-time 2 "http://localhost:$port/health" >/dev/null 2>&1 && return 0
    sleep 1
  done
  return 1
}

case "${1:-start}" in
  start)
    echo "=== Starting WenxinBlog dev environment ==="

    # 1. Docker 基建
    echo "[1/3] Docker infrastructure..."
    docker compose up -d --pull never 2>/dev/null || docker compose up -d 2>/dev/null
    echo "  Docker containers up"

    # 2. 应用服务
    echo "[2/3] Application services..."
    for svc in "${SERVICES[@]}"; do
      IFS=':' read -r name port dir heap <<< "$svc"
      start_service "$name" "$port" "$dir" "$heap"
    done

    echo "  Waiting for services..."
    for svc in "${SERVICES[@]}"; do
      IFS=':' read -r name port dir heap <<< "$svc"
      if wait_for_port "$port" 15 2>/dev/null; then
        echo "  ✅ $name($port) ready"
      else
        echo "  ⚠️  $name($port) not responding (check /tmp/svc-$name.log)"
      fi
    done

    # 3. Web 前端
    echo "[3/3] Web frontend..."
    if ! lsof -ti:3000 >/dev/null 2>&1; then
      cd "$ROOT_DIR/web"
      rm -rf .next
      nohup npm run dev > /tmp/wenxin-web-dev.log 2>&1 &
      cd "$ROOT_DIR"
    fi
    if wait_for_port 3000 30 2>/dev/null; then
      echo "  ✅ web(3000) ready"
    else
      echo "  ⚠️  web(3000) not responding"
    fi

    echo ""
    echo "=== All services ==="
    ./scripts/start-dev.sh status
    ;;

  stop)
    echo "=== Stopping all services ==="
    # Stop web
    lsof -ti:3000 2>/dev/null | xargs kill -9 2>/dev/null && echo "  web(3000) stopped"

    # Stop app services
    for svc in "${SERVICES[@]}"; do
      IFS=':' read -r name port dir heap <<< "$svc"
      stop_service "$name" "$port"
    done

    # Stop Docker
    echo "=== Stopping Docker ==="
    docker compose down 2>/dev/null
    echo "Done."
    ;;

  restart)
    ./scripts/start-dev.sh stop
    sleep 3
    ./scripts/start-dev.sh start
    ;;

  status)
    echo "=== Service Status ==="
    for svc in "${SERVICES[@]}"; do
      IFS=':' read -r name port dir heap <<< "$svc"
      if lsof -ti:$port >/dev/null 2>&1; then
        echo "  ✅ $name($port) UP"
      else
        echo "  ❌ $name($port) DOWN"
      fi
    done
    lsof -ti:3000 >/dev/null 2>&1 && echo "  ✅ web(3000) UP" || echo "  ❌ web(3000) DOWN"
    echo ""
    echo "Docker: $(docker compose ps --format '{{.Status}}' 2>/dev/null | grep -c Up) containers running"
    echo "Java processes: $(ps aux | grep -c '[j]ava')"
    ;;

  *)
    echo "Usage: $0 [start|stop|restart|status]"
    exit 1
    ;;
esac
