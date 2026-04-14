#!/usr/bin/env bash
# =============================================================================
# 下载 STT 语音识别模型 (Vosk)
# 可独立运行，也可被 download_models.sh 总控脚本调用
#
# 用法:
#   ./scripts/download_stt.sh              # 下载小模型 (默认)
#   ./scripts/download_stt.sh small        # 下载小模型 (~50MB)
#   ./scripts/download_stt.sh standard     # 下载标准模型 (~1.3GB)
#   ./scripts/download_stt.sh clean        # 清理
#   ./scripts/download_stt.sh status       # 查看状态
# =============================================================================

set -e
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/_common.sh"

# ---- Vosk STT 模型配置 ----
# small: vosk-model-small-cn-0.22 (~50MB，速度快，适合低配设备)
# standard: vosk-model-cn-0.22 (~1.3GB，精度高，推荐生产使用)
VOSK_MODEL_SMALL="vosk-model-small-cn-0.22"
VOSK_MODEL_STANDARD="vosk-model-cn-0.22"
VOSK_BASE_URL="https://alphacephei.com/vosk/models"
STT_MODEL_DIR="${MODELS_DIR}"

# ============================================================
# 下载 Vosk STT 模型
# ============================================================
download_stt() {
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

# ============================================================
# 清理
# ============================================================
clean_stt() {
    warn "========== 清理 STT 模型 =========="
    if [ -d "${STT_MODEL_DIR}/vosk-model" ]; then
        rm -rf "${STT_MODEL_DIR}/vosk-model"
        info "已删除 Vosk STT 模型"
    else
        info "无需清理"
    fi
    info "清理完成！"
}

# ============================================================
# 状态
# ============================================================
show_stt_status() {
    if [ -d "${STT_MODEL_DIR}/vosk-model" ]; then
        echo -e "  STT (语音识别):    ${GREEN}✓ 已下载${NC} - vosk-model"
    else
        echo -e "  STT (语音识别):    ${RED}✗ 未下载${NC} - vosk-model"
    fi
}

# ============================================================
# 独立运行时的入口
# ============================================================
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    case "${1:-small}" in
        small|standard)
            download_stt "$1"
            ;;
        clean)
            clean_stt
            ;;
        status)
            echo ""; info "========== STT 模型状态 =========="; show_stt_status; echo ""
            ;;
        *)
            echo "用法: $0 [small|standard|clean|status]"
            echo ""
            echo "  small    - 下载 Vosk 中文小模型 (~50MB，默认)"
            echo "  standard - 下载 Vosk 中文标准模型 (~1.3GB)"
            echo "  clean    - 清理 STT 模型"
            echo "  status   - 查看状态"
            exit 1
            ;;
    esac
fi
