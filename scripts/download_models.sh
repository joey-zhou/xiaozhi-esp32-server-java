#!/usr/bin/env bash
# =============================================================================
# 总控入口：下载所有模型和原生库
# 聚合 download_base.sh / download_stt.sh / download_tts.sh
#
# 支持平台: linux-x64, linux-aarch64, osx-x86_64, osx-arm64, win-x64
# 自动检测当前平台，也可通过环境变量 TARGET_PLATFORM 指定
#
# 用法:
#   ./scripts/download_models.sh              # 下载所有模型和原生库
#   ./scripts/download_models.sh stt          # 下载STT模型(Vosk)
#   ./scripts/download_models.sh tts          # 下载TTS模型 (vits-melo, 默认)
#   ./scripts/download_models.sh vad          # 下载VAD模型 (silero_vad)
#   ./scripts/download_models.sh models       # 下载所有模型
#   ./scripts/download_models.sh jni          # 仅下载sherpa-onnx JNI原生库
#   ./scripts/download_models.sh vosk-lib     # 仅下载Vosk原生库
#   ./scripts/download_models.sh libs         # 下载所有原生库(JNI + Vosk)
#   ./scripts/download_models.sh clean        # 清理所有下载的模型和库
#   ./scripts/download_models.sh status       # 查看模型和库状态
#
# 环境变量:
#   TARGET_PLATFORM=osx-arm64 ./scripts/download_models.sh  # 指定目标平台
# =============================================================================

set -e

_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# 加载所有子脚本（仅加载函数，不触发独立入口）
source "${_SCRIPT_DIR}/_common.sh"
source "${_SCRIPT_DIR}/download_base.sh"
source "${_SCRIPT_DIR}/download_stt.sh"
source "${_SCRIPT_DIR}/download_tts.sh"

# ============================================================
# 聚合操作
# ============================================================
download_all_models() {
    download_stt "${2:-small}"
    download_tts "vits-melo"
    download_vad
}

clean_all() {
    clean_stt
    clean_tts
    clean_base
}

show_status() {
    detect_platform
    echo ""
    info "========== 模型和原生库状态 (${PLATFORM}) =========="

    echo -e "  ${BLUE}── 模型 ──${NC}"
    show_stt_status
    show_tts_status

    echo -e "  ${BLUE}── 基础依赖 ──${NC}"
    show_base_status

    echo ""
}

# ============================================================
# 主逻辑
# ============================================================
case "${1:-all}" in
    stt)        download_stt "$2" ;;
    tts)        download_tts "$2" ;;
    vad)        download_vad ;;
    models)     download_all_models ;;
    jni)        download_jni_lib ;;
    vosk-lib)   download_vosk_lib ;;
    libs)       download_libs ;;
    all)
        download_all_models
        download_libs
        show_status
        info "所有模型和原生库下载完成！"
        ;;
    clean)
        clean_all
        show_status
        ;;
    status)
        show_status
        ;;
    *)
        echo "用法: $0 <command> [options]"
        echo ""
        echo "模型下载:"
        echo "  stt [small|standard]   - 下载Vosk语音识别模型 (默认small)"
        echo "  tts [vits-melo|matcha] - 下载TTS语音合成模型 (默认vits-melo)"
        echo "  vad                    - 下载VAD语音检测模型 (silero_vad)"
        echo "  models                 - 下载所有模型"
        echo ""
        echo "原生库下载:"
        echo "  jni                    - 下载sherpa-onnx JNI原生库 (含onnxruntime)"
        echo "  vosk-lib               - 下载Vosk原生库"
        echo "  libs                   - 下载所有原生库 (JNI + Vosk)"
        echo ""
        echo "其他:"
        echo "  all                    - 下载所有模型和原生库 (默认)"
        echo "  clean                  - 清理所有下载的模型和原生库"
        echo "  status                 - 查看下载状态"
        echo ""
        echo "支持平台: linux-x64, linux-aarch64, osx-x86_64, osx-arm64, win-x64"
        echo "自动检测当前平台，或通过 TARGET_PLATFORM 环境变量指定"
        echo "示例: TARGET_PLATFORM=linux-x64 $0 libs"
        echo ""
        echo "各子脚本也可独立运行:"
        echo "  ./scripts/download_base.sh       # 基础依赖 (VAD + 原生库)"
        echo "  ./scripts/download_stt.sh        # STT 模型"
        echo "  ./scripts/download_tts.sh        # TTS 模型"
        exit 1
        ;;
esac
