#!/usr/bin/env bash
# =============================================================================
# 下载 STT 语音识别模型
# 可独立运行，也可被 download_models.sh 总控脚本调用
#
# 用法:
#   ./scripts/download_stt.sh              # 下载 SenseVoice (默认，首选本地识别)
#   ./scripts/download_stt.sh sensevoice   # sherpa-onnx SenseVoice-Small int8 (~230MB，中英日韩粤，带情绪标签)
#   ./scripts/download_stt.sh small        # Vosk 中文小模型 (~50MB，兜底)
#   ./scripts/download_stt.sh standard     # Vosk 中文标准模型 (~1.3GB)
#   ./scripts/download_stt.sh clean        # 清理全部 STT 模型
#   ./scripts/download_stt.sh status       # 查看状态
# =============================================================================

set -e
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/_common.sh"

# ---- SenseVoice（sherpa-onnx）----
# 服务端优先加载 models/sense-voice，目录名与 RuntimePathConfig.senseVoiceModelDir 一致
SENSE_VOICE_MODEL="sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17"
SENSE_VOICE_BASE_URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models"
SENSE_VOICE_DIR_NAME="sense-voice"

# ---- Vosk ----
# small: vosk-model-small-cn-0.22 (~50MB，速度快，适合低配设备)
# standard: vosk-model-cn-0.22 (~1.3GB，精度高)
VOSK_MODEL_SMALL="vosk-model-small-cn-0.22"
VOSK_MODEL_STANDARD="vosk-model-cn-0.22"
VOSK_BASE_URL="https://alphacephei.com/vosk/models"

STT_MODEL_DIR="${MODELS_DIR}"

# ============================================================
# 下载 SenseVoice 模型
# ============================================================
download_sense_voice() {
    info "========== 下载语音识别(STT)模型 =========="
    info "模型: ${SENSE_VOICE_MODEL}"
    info "说明: sherpa-onnx SenseVoice-Small (~230MB int8，中英日韩粤，带情绪标签)"

    mkdir -p "$STT_MODEL_DIR"
    if [ -d "${STT_MODEL_DIR}/${SENSE_VOICE_DIR_NAME}" ]; then
        info "SenseVoice 模型已存在: ${STT_MODEL_DIR}/${SENSE_VOICE_DIR_NAME}/"
        info "如需重新下载，请先运行: $0 clean"
        return 0
    fi

    cd "$STT_MODEL_DIR"
    info "正在下载 ${SENSE_VOICE_MODEL}.tar.bz2 ..."
    download_file "${SENSE_VOICE_BASE_URL}/${SENSE_VOICE_MODEL}.tar.bz2" "${SENSE_VOICE_MODEL}.tar.bz2"

    info "正在解压..."
    tar xf "${SENSE_VOICE_MODEL}.tar.bz2"
    rm -f "${SENSE_VOICE_MODEL}.tar.bz2"
    mv "${SENSE_VOICE_MODEL}" "${SENSE_VOICE_DIR_NAME}"

    # 服务端只加载 int8 量化模型，fp32 的 model.onnx 近 900MB 留着只占磁盘
    if [ -f "${SENSE_VOICE_DIR_NAME}/model.int8.onnx" ] && [ -f "${SENSE_VOICE_DIR_NAME}/model.onnx" ]; then
        rm -f "${SENSE_VOICE_DIR_NAME}/model.onnx"
    fi

    info "STT模型下载完成！"
    info "路径: ${STT_MODEL_DIR}/${SENSE_VOICE_DIR_NAME}/"
    echo ""
}

# ============================================================
# 下载 Vosk 模型
# ============================================================
download_vosk() {
    local size="${1:-small}"
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

    if [ -d "${STT_MODEL_DIR}/vosk-model" ]; then
        info "Vosk模型已存在: ${STT_MODEL_DIR}/vosk-model/"
        info "如需重新下载，请先运行: $0 clean"
        return 0
    fi

    cd "$STT_MODEL_DIR"
    info "正在下载 ${model_name}.zip ..."
    download_file "${VOSK_BASE_URL}/${model_name}.zip" "${model_name}.zip"

    info "正在解压..."
    unzip -q "${model_name}.zip"

    # 重命名为统一目录名 vosk-model（与 VoskSttService.java 中的路径一致）
    mv "${model_name}" vosk-model
    rm -f "${model_name}.zip"

    info "STT模型下载完成！"
    info "路径: ${STT_MODEL_DIR}/vosk-model/"
    echo ""
}

# 总控脚本与 Dockerfile 的统一入口：sensevoice | small | standard
download_stt() {
    case "${1:-sensevoice}" in
        sensevoice) download_sense_voice ;;
        small|standard) download_vosk "$1" ;;
        *) error "未知的 STT 模型: $1"; return 1 ;;
    esac
}

# ============================================================
# 清理
# ============================================================
clean_stt() {
    warn "========== 清理 STT 模型 =========="
    local cleaned=0
    if [ -d "${STT_MODEL_DIR}/${SENSE_VOICE_DIR_NAME}" ]; then
        rm -rf "${STT_MODEL_DIR}/${SENSE_VOICE_DIR_NAME}"
        info "已删除 SenseVoice STT 模型"
        cleaned=1
    fi
    if [ -d "${STT_MODEL_DIR}/vosk-model" ]; then
        rm -rf "${STT_MODEL_DIR}/vosk-model"
        info "已删除 Vosk STT 模型"
        cleaned=1
    fi
    if [ "$cleaned" = "0" ]; then
        info "无需清理"
    fi
    info "清理完成！"
}

# ============================================================
# 状态
# ============================================================
show_stt_status() {
    if [ -d "${STT_MODEL_DIR}/${SENSE_VOICE_DIR_NAME}" ]; then
        echo -e "  STT (语音识别):    ${GREEN}✓ 已下载${NC} - ${SENSE_VOICE_DIR_NAME} (sherpa-onnx SenseVoice)"
    else
        echo -e "  STT (语音识别):    ${RED}✗ 未下载${NC} - ${SENSE_VOICE_DIR_NAME} (sherpa-onnx SenseVoice)"
    fi
    if [ -d "${STT_MODEL_DIR}/vosk-model" ]; then
        echo -e "  STT (Vosk 兜底):   ${GREEN}✓ 已下载${NC} - vosk-model"
    else
        echo -e "  STT (Vosk 兜底):   ${RED}✗ 未下载${NC} - vosk-model"
    fi
}

# ============================================================
# 独立运行时的入口
# ============================================================
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    case "${1:-sensevoice}" in
        sensevoice|small|standard)
            download_stt "$1"
            ;;
        clean)
            clean_stt
            ;;
        status)
            echo ""; info "========== STT 模型状态 =========="; show_stt_status; echo ""
            ;;
        *)
            echo "用法: $0 [sensevoice|small|standard|clean|status]"
            echo ""
            echo "  sensevoice - 下载 sherpa-onnx SenseVoice-Small (~230MB，默认，首选本地识别)"
            echo "  small      - 下载 Vosk 中文小模型 (~50MB，兜底)"
            echo "  standard   - 下载 Vosk 中文标准模型 (~1.3GB)"
            echo "  clean      - 清理 STT 模型"
            echo "  status     - 查看状态"
            exit 1
            ;;
    esac
fi
