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

# ---- 部署模式检测 ----
# 部署模式：$ROOT_DIR 下没有 pom.xml（纯 jar 部署）或没有 mvn 命令
# 此时跳过编译，直接使用现成的 jar
is_deploy_mode() {
  [[ ! -f "$ROOT_DIR/pom.xml" ]] && return 0
  ! command -v mvn >/dev/null 2>&1 && return 0
  return 1
}

# ---- Java 可执行文件解析 ----
# 优先级: $JAVA_BIN > $JAVA_HOME/bin/java > PATH 中的 java
# 适配宝塔/独立安装 JDK 不在 PATH 的场景（例如 /www/server/java/jdk-21.0.2/bin/java）
resolve_java() {
  if [[ -n "$JAVA_BIN" && -x "$JAVA_BIN" ]]; then
    echo "$JAVA_BIN"; return 0
  fi
  if [[ -n "$JAVA_HOME" && -x "$JAVA_HOME/bin/java" ]]; then
    echo "$JAVA_HOME/bin/java"; return 0
  fi
  if command -v java >/dev/null 2>&1; then
    command -v java; return 0
  fi
  return 1
}

# ---- 编译 ----
# build <module>  — 只编译该模块及其依赖
# build all       — 编译全部
build() {
  if is_deploy_mode; then
    _info "部署模式：跳过编译（未检测到 pom.xml 或 mvn 命令）"
    return 0
  fi

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
# 优先在 $ROOT_DIR 根目录查找（部署模式），找不到再回退到 $module/target/（开发模式）
find_jar() {
  local module="$1" jar=""
  if [[ "$module" == "xiaozhi-dialogue" ]]; then
    jar=$(ls "$ROOT_DIR/$module"-*-exec.jar 2>/dev/null | head -1)
    [[ -z "$jar" ]] && jar=$(ls "$ROOT_DIR/$module/target/$module"-*-exec.jar 2>/dev/null | head -1)
  else
    jar=$(ls "$ROOT_DIR/$module"-*.jar 2>/dev/null \
      | grep -v 'original' | grep -v '\-exec\.jar' | head -1)
    [[ -z "$jar" ]] && jar=$(ls "$ROOT_DIR/$module/target/$module"-*.jar 2>/dev/null \
      | grep -v 'original' | grep -v '\-exec\.jar' | head -1)
  fi
  echo "$jar"
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

  local java_bin
  if ! java_bin="$(resolve_java)"; then
    _err "未找到 java 可执行文件。请安装 JDK 21+ 或设置 JAVA_HOME / JAVA_BIN 环境变量"
    _err "  例如: export JAVA_HOME=/www/server/java/jdk-21.0.2"
    return 1
  fi

  _info "启动 $name (port $port)..."
  _info "  java: $java_bin"
  mkdir -p "$LOGS_DIR"

  # cd 到 ROOT_DIR 启动，确保:
  #   1. Logback 配置中的 ./logs 写到 $ROOT_DIR/logs/
  #   2. application.yml 中 lib/, models/silero_vad.onnx 等相对路径解析正确
  ( cd "$ROOT_DIR" && exec nohup "$java_bin" \
      -Djava.library.path="$ROOT_DIR/lib" \
      -jar "$jar" \
      >> "$LOGS_DIR/$name.out" 2>&1 ) &

  local pid=$!
  echo "$pid" > "$(pid_file "$name")"
  _ok "$name 已启动  pid=$pid  日志: logs/$name.log  控制台: logs/$name.out"
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
