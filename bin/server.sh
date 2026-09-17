#!/usr/bin/env bash
# =============================================================================
# xiaozhi-server 管理脚本
# 用法: bin/server.sh <start|stop|restart|status|logs> [dev|prod]，运行环境默认 dev
# =============================================================================
source "$(cd "$(dirname "$0")" && pwd)/_common.sh"

NAME="xiaozhi-server"
MODULE="xiaozhi-server"
PORT=8091

case "${1:-}" in
  start)
    PROFILE="$(resolve_profile "${2:-}")" || exit 1
    build "$MODULE"
    start_service "$NAME" "$MODULE" "$PORT" "$PROFILE"
    follow_logs_if_tty "$NAME"
    ;;
  stop)
    stop_service "$NAME"
    ;;
  restart)
    PROFILE="$(resolve_profile "${2:-}")" || exit 1
    stop_service "$NAME"
    sleep 1
    build "$MODULE"
    start_service "$NAME" "$MODULE" "$PORT" "$PROFILE"
    follow_logs_if_tty "$NAME"
    ;;
  status)
    status_service "$NAME" "$PORT"
    ;;
  logs)
    follow_logs "$NAME"
    ;;
  *)
    usage "bin/server.sh"
    exit 1
    ;;
esac
