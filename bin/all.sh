#!/usr/bin/env bash
# =============================================================================
# 所有服务管理脚本（server + dialogue）
# 用法: bin/all.sh <start|stop|restart|status|logs> [dev|prod]，运行环境默认 dev
# =============================================================================
source "$(cd "$(dirname "$0")" && pwd)/_common.sh"

case "${1:-}" in
  start)
    PROFILE="$(resolve_profile "${2:-}")" || exit 1
    build all
    start_service "xiaozhi-server"   "xiaozhi-server"   8091 "$PROFILE"
    start_service "xiaozhi-dialogue" "xiaozhi-dialogue" 8092 "$PROFILE"
    echo ""
    _ok "全部启动完成"
    follow_logs_if_tty "xiaozhi-server" "xiaozhi-dialogue"
    ;;
  stop)
    stop_service "xiaozhi-server"
    stop_service "xiaozhi-dialogue"
    _ok "全部已停止"
    ;;
  restart)
    PROFILE="$(resolve_profile "${2:-}")" || exit 1
    stop_service "xiaozhi-server"
    stop_service "xiaozhi-dialogue"
    sleep 1
    build all
    start_service "xiaozhi-server"   "xiaozhi-server"   8091 "$PROFILE"
    start_service "xiaozhi-dialogue" "xiaozhi-dialogue" 8092 "$PROFILE"
    echo ""
    _ok "全部重启完成"
    follow_logs_if_tty "xiaozhi-server" "xiaozhi-dialogue"
    ;;
  status)
    echo ""
    status_service "xiaozhi-server"   8091
    status_service "xiaozhi-dialogue" 8092
    echo ""
    ;;
  logs)
    follow_logs "xiaozhi-server" "xiaozhi-dialogue"
    ;;
  *)
    usage "bin/all.sh"
    exit 1
    ;;
esac
