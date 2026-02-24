#!/bin/bash
# ============================================================
# 下载语音识别(Vosk)和语音合成(sherpa-onnx TTS)模型
# 用于 xiaozhi-esp32-server-java Docker 部署
#
# 预先下载模型到本地，Docker 构建时直接 COPY，避免构建时下载慢
#
# 使用方法:
#   ./scripts/download_models.sh              # 下载所有模型
#   ./scripts/download_models.sh stt          # 仅下载STT模型(Vosk)
#   ./scripts/download_models.sh stt small    # 下载小型Vosk模型
#   ./scripts/download_models.sh stt standard # 下载标准Vosk模型
#   ./scripts/download_models.sh tts          # 仅下载TTS模型
#   ./scripts/download_models.sh jni          # 仅下载sherpa-onnx JNI库
#   ./scripts/download_models.sh clean        # 清理所有下载的模型
#   ./scripts/download_models.sh status       # 查看模型状态
# ============================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

# ---- Vosk STT 模型配置 ----
# small: vosk-model-small-cn-0.22 (~50MB，速度快，适合低配设备)
# standard: vosk-model-cn-0.22 (~1.3GB，精度高，推荐生产使用)
VOSK_MODEL_SIZE="${2:-small}"
VOSK_MODEL_SMALL="vosk-model-small-cn-0.22"
VOSK_MODEL_STANDARD="vosk-model-cn-0.22"
VOSK_BASE_URL="https://alphacephei.com/vosk/models"
STT_MODEL_DIR="${PROJECT_DIR}/models"

# ---- sherpa-onnx TTS 模型配置 ----
TTS_MODEL_NAME="vits-melo-tts-zh_en"
TTS_MODEL_URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/${TTS_MODEL_NAME}.tar.bz2"
TTS_MODEL_DIR="${PROJECT_DIR}/models/tts"

# ---- sherpa-onnx JNI 原生库（从 native jar 中提取） ----
SHERPA_VERSION="1.12.23"
ONNXRUNTIME_VERSION="1.23.2"
JNI_JAR_NAME="sherpa-onnx-native-lib-linux-x64-v${SHERPA_VERSION}.jar"
JNI_JAR_URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/v${SHERPA_VERSION}/${JNI_JAR_NAME}"
LIB_DIR="${PROJECT_DIR}/lib"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

info()  { echo -e "${GREEN}[INFO]${NC} $1"; }
warn()  { echo -e "${YELLOW}[WARN]${NC} $1"; }
error() { echo -e "${RED}[ERROR]${NC} $1"; }

# 下载 Vosk STT 模型
download_stt() {
    local size="${1:-$VOSK_MODEL_SIZE}"
    local model_name

    if [ "$size" = "small" ]; then
        model_name="$VOSK_MODEL_SMALL"
    else
        model_name="$VOSK_MODEL_STANDARD"
    fi

    info "========== 下载语音识别(STT)模型 =========="
    info "模型: ${model_name}"
    if [ "$size" = "small" ]; then
        info "说明: Vosk 中文小模型 (~50MB，速度快)"
    else
        info "说明: Vosk 中文标准模型 (~1.3GB，精度高)"
    fi

    mkdir -p "$STT_MODEL_DIR"

    # 检查是否已存在（统一目录名 vosk-model）
    if [ -d "${STT_MODEL_DIR}/vosk-model" ]; then
        info "Vosk模型已存在: ${STT_MODEL_DIR}/vosk-model/"
        info "如需重新下载，请先运行: $0 clean"
        return 0
    fi

    cd "$STT_MODEL_DIR"
    info "正在下载 ${model_name}.zip ..."
    wget -c "${VOSK_BASE_URL}/${model_name}.zip" -O "${model_name}.zip"

    info "正在解压..."
    unzip -q "${model_name}.zip"

    # 重命名为统一目录名 vosk-model（与 VoskSttService.java 中的路径一致）
    mv "${model_name}" vosk-model
    rm -f "${model_name}.zip"

    info "STT模型下载完成！"
    info "路径: ${STT_MODEL_DIR}/vosk-model/"
    echo ""
}

# 下载 TTS 模型
download_tts() {
    info "========== 下载语音合成(TTS)模型 =========="
    info "模型: ${TTS_MODEL_NAME}"
    info "说明: VITS MeloTTS 中英文语音合成 (~163MB)"

    mkdir -p "$TTS_MODEL_DIR"

    if [ -f "${TTS_MODEL_DIR}/${TTS_MODEL_NAME}/model.onnx" ]; then
        info "TTS模型已存在: ${TTS_MODEL_DIR}/${TTS_MODEL_NAME}/"
        info "如需重新下载，请先运行: $0 clean"
        return 0
    fi

    cd "$TTS_MODEL_DIR"
    info "正在下载..."
    wget -c "$TTS_MODEL_URL" -O "${TTS_MODEL_NAME}.tar.bz2"

    info "正在解压..."
    tar xf "${TTS_MODEL_NAME}.tar.bz2"
    rm -f "${TTS_MODEL_NAME}.tar.bz2"

    info "TTS模型下载完成！"
    info "路径: ${TTS_MODEL_DIR}/${TTS_MODEL_NAME}/"
    echo ""
}

# 下载 sherpa-onnx JNI 原生库（从 native jar 中提取）
download_jni_lib() {
    info "========== 下载 sherpa-onnx JNI 原生库 =========="
    info "版本: v${SHERPA_VERSION} (linux-x64)"

    mkdir -p "$LIB_DIR"

    # 检查已有库是否版本匹配（通过 strings 检查 VERS 标记）
    local need_download=false
    if [ -f "${LIB_DIR}/libsherpa-onnx-jni.so" ] && [ -f "${LIB_DIR}/libonnxruntime.so" ]; then
        # 验证 libonnxruntime.so 包含正确的版本符号
        if command -v strings &>/dev/null; then
            if strings "${LIB_DIR}/libonnxruntime.so" | grep -q "VERS_${ONNXRUNTIME_VERSION}"; then
                info "JNI原生库已存在且版本匹配 (onnxruntime ${ONNXRUNTIME_VERSION}): ${LIB_DIR}/"
                info "如需重新下载，请先运行: $0 clean"
                return 0
            else
                warn "libonnxruntime.so 版本不匹配，需要 VERS_${ONNXRUNTIME_VERSION}，将重新下载..."
                need_download=true
            fi
        else
            info "JNI原生库已存在: ${LIB_DIR}/"
            info "如需重新下载，请先运行: $0 clean"
            return 0
        fi
    else
        need_download=true
    fi

    local WORK_DIR=$(mktemp -d)
    cd "$WORK_DIR"

    info "正在下载 ${JNI_JAR_NAME} ..."
    wget -c "$JNI_JAR_URL" -O "${JNI_JAR_NAME}"

    info "正在从 jar 中提取 JNI 库..."
    unzip -o "${JNI_JAR_NAME}" "sherpa-onnx/native/linux-x64/*" -d "$WORK_DIR"

    cp "$WORK_DIR/sherpa-onnx/native/linux-x64/libsherpa-onnx-jni.so" "$LIB_DIR/"
    cp "$WORK_DIR/sherpa-onnx/native/linux-x64/libonnxruntime.so" "$LIB_DIR/"
    # 如果有 libsherpa-onnx-c-api.so 也一并复制
    if [ -f "$WORK_DIR/sherpa-onnx/native/linux-x64/libsherpa-onnx-c-api.so" ]; then
        cp "$WORK_DIR/sherpa-onnx/native/linux-x64/libsherpa-onnx-c-api.so" "$LIB_DIR/"
    fi

    # 验证关键文件
    if [ ! -f "${LIB_DIR}/libsherpa-onnx-jni.so" ]; then
        error "提取失败，未找到 libsherpa-onnx-jni.so"
        rm -rf "$WORK_DIR"
        return 1
    fi

    rm -rf "$WORK_DIR"

    info "JNI原生库下载完成！"
    ls -lh "$LIB_DIR"/lib*.so
    echo ""
}

# 清理所有模型
clean_models() {
    warn "========== 清理所有下载的模型 =========="

    if [ -d "${STT_MODEL_DIR}/vosk-model" ]; then
        rm -rf "${STT_MODEL_DIR}/vosk-model"
        info "已删除 Vosk STT 模型"
    fi

    if [ -d "${TTS_MODEL_DIR}/${TTS_MODEL_NAME}" ]; then
        rm -rf "${TTS_MODEL_DIR}/${TTS_MODEL_NAME}"
        info "已删除 TTS 模型: ${TTS_MODEL_NAME}"
    fi

    for f in libsherpa-onnx-jni.so libonnxruntime.so; do
        [ -f "${LIB_DIR}/$f" ] && rm -f "${LIB_DIR}/$f" && info "已删除: $f"
    done

    info "清理完成！"
}

# 显示模型状态
show_status() {
    echo ""
    info "========== 模型状态 =========="

    # STT (Vosk)
    if [ -d "${STT_MODEL_DIR}/vosk-model" ]; then
        echo -e "  STT (语音识别): ${GREEN}✓ 已下载${NC} - vosk-model"
    else
        echo -e "  STT (语音识别): ${RED}✗ 未下载${NC} - vosk-model"
    fi

    # TTS
    if [ -f "${TTS_MODEL_DIR}/${TTS_MODEL_NAME}/model.onnx" ]; then
        echo -e "  TTS (语音合成): ${GREEN}✓ 已下载${NC} - ${TTS_MODEL_NAME}"
    else
        echo -e "  TTS (语音合成): ${RED}✗ 未下载${NC} - ${TTS_MODEL_NAME}"
    fi

    # VAD
    if [ -f "${PROJECT_DIR}/models/silero_vad.onnx" ]; then
        echo -e "  VAD (语音检测): ${GREEN}✓ 已存在${NC} - silero_vad.onnx"
    else
        echo -e "  VAD (语音检测): ${RED}✗ 不存在${NC} - silero_vad.onnx"
    fi

    # JNI
    if [ -f "${LIB_DIR}/libsherpa-onnx-jni.so" ]; then
        local version_ok=""
        if command -v strings &>/dev/null; then
            if strings "${LIB_DIR}/libonnxruntime.so" 2>/dev/null | grep -q "VERS_${ONNXRUNTIME_VERSION}"; then
                version_ok=" (onnxruntime ${ONNXRUNTIME_VERSION} ✓)"
            else
                version_ok=" ${RED}(onnxruntime 版本不匹配，需要 ${ONNXRUNTIME_VERSION})${NC}"
            fi
        fi
        echo -e "  JNI (原生库):   ${GREEN}✓ 已下载${NC} - sherpa-onnx v${SHERPA_VERSION}${version_ok}"
    else
        echo -e "  JNI (原生库):   ${RED}✗ 未下载${NC} - sherpa-onnx v${SHERPA_VERSION}"
    fi
    echo ""
}

# 主逻辑
case "${1:-all}" in
    stt)
        download_stt "$2"
        show_status
        ;;
    tts)
        download_tts
        show_status
        ;;
    jni)
        download_jni_lib
        show_status
        ;;
    all)
        download_stt "$VOSK_MODEL_SIZE"
        download_tts
        download_jni_lib
        show_status
        info "所有模型下载完成！现在可以运行 docker compose build 构建镜像。"
        ;;
    clean)
        clean_models
        show_status
        ;;
    status)
        show_status
        ;;
    *)
        echo "用法: $0 [stt|tts|jni|all|clean|status] [small|standard]"
        echo ""
        echo "  stt [small|standard] - 下载Vosk语音识别模型 (默认small)"
        echo "  tts                  - 下载TTS语音合成模型 (vits-melo-tts-zh_en)"
        echo "  jni                  - 下载sherpa-onnx JNI原生库"
        echo "  all                  - 下载所有模型和库 (默认)"
        echo "  clean                - 清理所有下载的模型"
        echo "  status               - 查看模型下载状态"
        exit 1
        ;;
esac
