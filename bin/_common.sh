#!/usr/bin/env bash
# =============================================================================
# 公共函数库，被 server.sh / dialogue.sh / all.sh 引用，不直接执行
# =============================================================================

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOGS_DIR="$ROOT_DIR/logs"

# ---- 颜色 ----
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
CYAN='\033[0;36m'; BLUE='\033[0;34m'; BOLD='\033[1m'; NC='\033[0m'

_log()  { echo -e "${GREEN}[xiaozhi]${NC} $*"; }
_info() { echo -e "${CYAN}[xiaozhi]${NC} $*"; }
_warn() { echo -e "${YELLOW}[xiaozhi]${NC} $*"; }
_err()  { echo -e "${RED}[xiaozhi]${NC} $*" >&2; }
_ok()   { echo -e "${GREEN}[xiaozhi]${NC} ${BOLD}$*${NC}"; }

# ---- 编译 ----
# build <module>  — 只编译该模块及其依赖
# build all       — 编译全部
build() {
  local target="${1:-all}"
  if [[ "$target" == "all" ]]; then
    _info "编译所有模块..."
    mvn clean install -DskipTests -q -f "$ROOT_DIR/pom.xml"
  else
    _info "编译 $target 及其依赖..."
    mvn clean install -DskipTests -q -f "$ROOT_DIR/pom.xml" \
        -pl "$target" --also-make
  fi
  _log "编译完成"
}

# ---- 查找 jar ----
# xiaozhi-dialogue 使用 classifier=exec，产出 *-exec.jar；其余模块用普通 jar
find_jar() {
  local module="$1"
  if [[ "$module" == "xiaozhi-dialogue" ]]; then
    ls "$ROOT_DIR/$module/target/$module"-*-exec.jar 2>/dev/null | head -1
  else
    ls "$ROOT_DIR/$module/target/$module"-*.jar 2>/dev/null \
      | grep -v 'original' | grep -v '\-exec\.jar' | head -1
  fi
}

# ---- PID 文件路径 ----
pid_file() {
  echo "$LOGS_DIR/$1.pid"
}

# ---- 判断进程是否存活 ----
is_running() {
  local pid_path
  pid_path="$(pid_file "$1")"
  [[ -f "$pid_path" ]] && kill -0 "$(cat "$pid_path")" 2>/dev/null
}

# ---- 启动单个服务 ----
# start_service <name> <module> <port> [label_color]
start_service() {
  local name="$1" module="$2" port="$3" color="${4:-$CYAN}"

  if is_running "$name"; then
    _warn "$name 已在运行 (pid=$(cat "$(pid_file "$name")"))"
    return 0
  fi

  local jar
  jar="$(find_jar "$module")"
  if [[ -z "$jar" ]]; then
    _err "$module jar 不存在，请先编译"; return 1
  fi

  _info "启动 $name (port $port)..."

  nohup java \
    -Djava.library.path="$ROOT_DIR/lib" \
    -jar "$jar" \
    > /dev/null 2>&1 &

  echo $! > "$(pid_file "$name")"
  _ok "$name 已启动  pid=$!  日志: logs/$name.log"
}

# ---- 停止单个服务 ----
stop_service() {
  local name="$1"
  local pid_path
  pid_path="$(pid_file "$name")"

  if ! is_running "$name"; then
    _warn "$name 未在运行"
    return 0
  fi

  local pid
  pid="$(cat "$pid_path")"
  _info "停止 $name (pid=$pid)..."
  kill "$pid"

  # 等待最多 15 秒
  local i=0
  while kill -0 "$pid" 2>/dev/null && (( i < 15 )); do
    sleep 1; (( i++ ))
  done

  if kill -0 "$pid" 2>/dev/null; then
    _warn "未能正常关闭，强制结束..."
    kill -9 "$pid" 2>/dev/null || true
  fi

  rm -f "$pid_path"
  _ok "$name 已停止"
}

# ---- 查看状态 ----
status_service() {
  local name="$1" port="$2"
  if is_running "$name"; then
    local pid
    pid="$(cat "$(pid_file "$name")")"
    echo -e "  ${GREEN}●${NC} ${BOLD}$name${NC}  pid=$pid  port=$port  日志: logs/$name.log"
  else
    echo -e "  ${RED}○${NC} ${BOLD}$name${NC}  未运行"
  fi
}

# ---- 重启 ----
restart_service() {
  local name="$1" module="$2" port="$3"
  stop_service  "$name"
  sleep 1
  start_service "$name" "$module" "$port"
}

# ---- 用法提示 ----
usage() {
  local script="$1"
  echo -e "用法: ${BOLD}$script${NC} <start|stop|restart|status>"
  echo "  start    编译并启动"
  echo "  stop     停止"
  echo "  restart  停止后重新编译并启动"
  echo "  status   查看运行状态"
}
