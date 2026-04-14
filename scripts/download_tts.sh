#!/usr/bin/env bash
# =============================================================================
# 下载 TTS 语音合成模型 (sherpa-onnx)
# 可独立运行，也可被 download_models.sh 总控脚本调用
#
# 用法:
#   ./scripts/download_tts.sh              # 下载默认模型 (vits-melo)
#   ./scripts/download_tts.sh vits-melo    # 下载 VITS MeloTTS 中英文 (~163MB)
#   ./scripts/download_tts.sh matcha       # 下载 Matcha-Icefall 中英文
#   ./scripts/download_tts.sh clean        # 清理
#   ./scripts/download_tts.sh status       # 查看状态
# =============================================================================

set -e
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/_common.sh"

# ---- TTS 模型配置 ----
TTS_MODEL_DIR="${MODELS_DIR}/tts"
SHERPA_TTS_BASE_URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models"
# 支持的 TTS 模型: vits-melo (默认), matcha
TTS_VITS_MELO="vits-melo-tts-zh_en"
TTS_MATCHA="matcha-icefall-zh-en"

# ============================================================
# 下载 TTS 模型
# ============================================================
download_tts() {
    local tts_type="${1:-vits-melo}"
    local model_name

    case "$tts_type" in
        vits-melo|vits|melo)
            model_name="$TTS_VITS_MELO"
            info "========== 下载语音合成(TTS)模型 =========="
            info "模型: ${model_name} (VITS MeloTTS 中英文，~163MB)"
            ;;
        matcha|matcha-icefall)
            model_name="$TTS_MATCHA"
            info "========== 下载语音合成(TTS)模型 =========="
            info "模型: ${model_name} (Matcha-Icefall 中英文)"
            ;;
        *)
            error "未知的 TTS 模型类型: $tts_type (支持: vits-melo, matcha)"
            return 1
            ;;
    esac

    mkdir -p "$TTS_MODEL_DIR"

    if [ -f "${TTS_MODEL_DIR}/${model_name}/model.onnx" ]; then
        info "TTS模型已存在: ${TTS_MODEL_DIR}/${model_name}/"
        info "如需重新下载，请先运行: $0 clean"
        return 0
    fi

    local tar_url="${SHERPA_TTS_BASE_URL}/${model_name}.tar.bz2"

    cd "$TTS_MODEL_DIR"
    info "正在下载..."
    download_file "$tar_url" "${model_name}.tar.bz2"

    info "正在解压..."
    tar xf "${model_name}.tar.bz2"
    rm -f "${model_name}.tar.bz2"

    info "TTS模型下载完成！"
    info "路径: ${TTS_MODEL_DIR}/${model_name}/"
    echo ""
}

# ============================================================
# 清理
# ============================================================
clean_tts() {
    warn "========== 清理 TTS 模型 =========="
    for tts_model in "$TTS_VITS_MELO" "$TTS_MATCHA"; do
        if [ -d "${TTS_MODEL_DIR}/${tts_model}" ]; then
            rm -rf "${TTS_MODEL_DIR}/${tts_model}"
            info "已删除 TTS 模型: ${tts_model}"
        fi
        rm -f "${TTS_MODEL_DIR}/${tts_model}.tar.bz2" 2>/dev/null
    done
    info "清理完成！"
}

# ============================================================
# 状态
# ============================================================
show_tts_status() {
    if [ -f "${TTS_MODEL_DIR}/${TTS_VITS_MELO}/model.onnx" ]; then
        echo -e "  TTS (vits-melo):   ${GREEN}✓ 已下载${NC} - ${TTS_VITS_MELO}"
    else
        echo -e "  TTS (vits-melo):   ${RED}✗ 未下载${NC} - ${TTS_VITS_MELO}"
    fi

    if [ -f "${TTS_MODEL_DIR}/${TTS_MATCHA}/model.onnx" ]; then
        echo -e "  TTS (matcha):      ${GREEN}✓ 已下载${NC} - ${TTS_MATCHA}"
    else
        echo -e "  TTS (matcha):      ${YELLOW}○ 未下载${NC} - ${TTS_MATCHA} (可选)"
    fi
}

# ============================================================
# 独立运行时的入口
# ============================================================
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    case "${1:-vits-melo}" in
        vits-melo|vits|melo|matcha|matcha-icefall)
            download_tts "$1"
            ;;
        clean)
            clean_tts
            ;;
        status)
            echo ""; info "========== TTS 模型状态 =========="; show_tts_status; echo ""
            ;;
        *)
            echo "用法: $0 [vits-melo|matcha|clean|status]"
            echo ""
            echo "  vits-melo - 下载 VITS MeloTTS 中英文 (~163MB，默认)"
            echo "  matcha    - 下载 Matcha-Icefall 中英文"
            echo "  clean     - 清理所有 TTS 模型"
            echo "  status    - 查看状态"
            exit 1
            ;;
    esac
fi
