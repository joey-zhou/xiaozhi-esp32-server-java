#!/usr/bin/env bash
# =============================================================================
# 公共函数库，被 download_*.sh 引用，不直接执行
# =============================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

# ---- 版本配置 ----
SHERPA_VERSION="1.12.23"
ONNXRUNTIME_VERSION="1.23.2"
VOSK_VERSION="0.3.45"

# ---- 公共目录 ----
LIB_DIR="${PROJECT_DIR}/lib"
MODELS_DIR="${PROJECT_DIR}/models"

# ---- 颜色 ----
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

info()  { echo -e "${GREEN}[INFO]${NC} $1"; }
warn()  { echo -e "${YELLOW}[WARN]${NC} $1"; }
error() { echo -e "${RED}[ERROR]${NC} $1"; }

# ============================================================
# 平台检测
# ============================================================
detect_platform() {
    if [ -n "$PLATFORM" ]; then
        return 0
    fi

    if [ -n "$TARGET_PLATFORM" ]; then
        PLATFORM="$TARGET_PLATFORM"
        info "使用指定平台: ${BLUE}${PLATFORM}${NC}"
    else
        local os arch
        os=$(uname -s | tr '[:upper:]' '[:lower:]')
        arch=$(uname -m)
        case "$os" in
            linux)
                case "$arch" in
                    x86_64|amd64)   PLATFORM="linux-x64" ;;
                    aarch64|arm64)  PLATFORM="linux-aarch64" ;;
                    *) error "不支持的 Linux 架构: $arch"; exit 1 ;;
                esac
                ;;
            darwin)
                case "$arch" in
                    x86_64)         PLATFORM="osx-x86_64" ;;
                    arm64|aarch64)  PLATFORM="osx-arm64" ;;
                    *) error "不支持的 macOS 架构: $arch"; exit 1 ;;
                esac
                ;;
            mingw*|msys*|cygwin*)
                PLATFORM="win-x64"
                ;;
            *)
                error "不支持的操作系统: $os (支持: linux, darwin, windows)"
                exit 1
                ;;
        esac
        info "检测到平台: ${BLUE}${PLATFORM}${NC}"
    fi

    # 设置平台相关的库文件扩展名和名称
    case "$PLATFORM" in
        linux-*)
            LIB_EXT="so"
            SHERPA_JNI_LIB="libsherpa-onnx-jni.${LIB_EXT}"
            SHERPA_CAPI_LIB="libsherpa-onnx-c-api.${LIB_EXT}"
            ONNXRT_LIB="libonnxruntime.${LIB_EXT}"
            VOSK_LIB="libvosk.${LIB_EXT}"
            ;;
        osx-*)
            LIB_EXT="dylib"
            SHERPA_JNI_LIB="libsherpa-onnx-jni.${LIB_EXT}"
            SHERPA_CAPI_LIB="libsherpa-onnx-c-api.${LIB_EXT}"
            ONNXRT_LIB="libonnxruntime.${LIB_EXT}"
            VOSK_LIB="libvosk.${LIB_EXT}"
            ;;
        win-*)
            LIB_EXT="dll"
            SHERPA_JNI_LIB="sherpa-onnx-jni.${LIB_EXT}"
            SHERPA_CAPI_LIB="sherpa-onnx-c-api.${LIB_EXT}"
            ONNXRT_LIB="onnxruntime.${LIB_EXT}"
            VOSK_LIB="vosk.${LIB_EXT}"
            ;;
    esac
}

# ============================================================
# 下载辅助函数（优先 curl，fallback wget）
# ============================================================
download_file() {
    local url="$1"
    local output="$2"
    if command -v curl &>/dev/null; then
        curl -L -C - --progress-bar -o "$output" "$url"
    elif command -v wget &>/dev/null; then
        wget -c "$url" -O "$output"
    else
        error "需要 wget 或 curl，请先安装其中之一"
        exit 1
    fi
}
