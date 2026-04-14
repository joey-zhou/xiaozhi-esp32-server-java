#!/usr/bin/env bash
# =============================================================================
# 下载基础依赖: VAD 模型 + 原生库 (sherpa-onnx JNI + Vosk)
# 可独立运行，也可被 download_models.sh 总控脚本调用
#
# 用法:
#   ./scripts/download_base.sh              # 下载全部基础依赖
#   ./scripts/download_base.sh vad          # 仅下载 VAD 模型
#   ./scripts/download_base.sh jni          # 仅下载 sherpa-onnx JNI 原生库
#   ./scripts/download_base.sh vosk-lib     # 仅下载 Vosk 原生库
#   ./scripts/download_base.sh libs         # 下载所有原生库
#   ./scripts/download_base.sh clean        # 清理
#   ./scripts/download_base.sh status       # 查看状态
# =============================================================================

set -e
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/_common.sh"

# ---- VAD 模型配置 ----
VAD_MODEL_NAME="silero_vad.onnx"
VAD_MODEL_URL="https://github.com/snakers4/silero-vad/raw/master/src/silero_vad/data/silero_vad.onnx"
VAD_MODEL_DIR="${MODELS_DIR}"

# ---- 原生库下载地址 ----
SHERPA_GITHUB="https://github.com/k2-fsa/sherpa-onnx/releases/download/v${SHERPA_VERSION}"
VOSK_GITHUB="https://github.com/alphacep/vosk-api/releases/download/v${VOSK_VERSION}"

# ============================================================
# 下载 VAD 模型 (Silero VAD)
# ============================================================
download_vad() {
    info "========== 下载 VAD 语音检测模型 =========="
    info "模型: ${VAD_MODEL_NAME} (~2.2MB)"

    mkdir -p "$VAD_MODEL_DIR"

    if [ -f "${VAD_MODEL_DIR}/${VAD_MODEL_NAME}" ]; then
        info "VAD模型已存在: ${VAD_MODEL_DIR}/${VAD_MODEL_NAME}"
        info "如需重新下载，请先运行: $0 clean"
        return 0
    fi

    info "正在下载..."
    download_file "$VAD_MODEL_URL" "${VAD_MODEL_DIR}/${VAD_MODEL_NAME}"

    info "VAD模型下载完成！"
    echo ""
}

# ============================================================
# 下载 sherpa-onnx JNI 原生库（全平台，从 JNI tarball 提取）
# ============================================================
download_jni_lib() {
    detect_platform
    info "========== 下载 sherpa-onnx JNI 原生库 =========="
    info "版本: v${SHERPA_VERSION} (${PLATFORM})"

    mkdir -p "$LIB_DIR"

    if [ -f "${LIB_DIR}/${SHERPA_JNI_LIB}" ] && [ -f "${LIB_DIR}/${ONNXRT_LIB}" ]; then
        info "JNI原生库已存在: ${LIB_DIR}/"
        info "如需重新下载，请先运行: $0 clean"
        return 0
    fi

    local tar_name="sherpa-onnx-v${SHERPA_VERSION}-${PLATFORM}-jni.tar.bz2"
    local tar_url="${SHERPA_GITHUB}/${tar_name}"

    local WORK_DIR=$(mktemp -d)
    trap "rm -rf '$WORK_DIR'" EXIT

    info "正在下载 ${tar_name} ..."
    download_file "$tar_url" "${WORK_DIR}/${tar_name}"

    info "正在解压原生库..."
    tar xf "${WORK_DIR}/${tar_name}" -C "$WORK_DIR"

    local found=0
    while IFS= read -r -d '' lib_file; do
        cp "$lib_file" "$LIB_DIR/"
        info "  提取: $(basename "$lib_file")"
        found=$((found + 1))
    done < <(find "$WORK_DIR" -type f \( -name "*.${LIB_EXT}" -o -name "*.${LIB_EXT}.*" \) -print0)

    # macOS: 创建 libonnxruntime.dylib 符号链接
    if [[ "$PLATFORM" == osx-* ]] && [ ! -e "${LIB_DIR}/libonnxruntime.dylib" ]; then
        local versioned_ort
        versioned_ort=$(ls "${LIB_DIR}"/libonnxruntime.*.dylib 2>/dev/null | head -1)
        if [ -n "$versioned_ort" ]; then
            ln -sf "$(basename "$versioned_ort")" "${LIB_DIR}/libonnxruntime.dylib"
            info "  创建符号链接: libonnxruntime.dylib -> $(basename "$versioned_ort")"
        fi
    fi

    rm -rf "$WORK_DIR"
    trap - EXIT

    if [ "$found" -eq 0 ]; then
        error "提取失败，未找到任何 .${LIB_EXT} 文件"
        return 1
    fi

    info "sherpa-onnx JNI 原生库下载完成！(共 ${found} 个文件)"
    echo ""
}

# ============================================================
# 下载 Vosk 原生库（从 Maven JAR 中提取）
# ============================================================
download_vosk_lib() {
    detect_platform
    info "========== 下载 Vosk 原生库 =========="
    info "版本: v${VOSK_VERSION} (${PLATFORM})"

    mkdir -p "$LIB_DIR"

    if [ -f "${LIB_DIR}/${VOSK_LIB}" ]; then
        info "Vosk原生库已存在: ${LIB_DIR}/${VOSK_LIB}"
        info "如需重新下载，请先运行: $0 clean"
        return 0
    fi

    local jar_subdir
    case "$PLATFORM" in
        linux-x64)      jar_subdir="linux-x86-64" ;;
        linux-aarch64)
            warn "Vosk Maven JAR 不包含 linux-aarch64 原生库"
            warn "请从 ${VOSK_GITHUB}/vosk-linux-aarch64-${VOSK_VERSION}.zip 手动下载"
            return 0
            ;;
        osx-*)          jar_subdir="darwin" ;;
        win-x64)        jar_subdir="win32-x86-64" ;;
    esac

    local vosk_jar=""
    local m2_jar="${HOME}/.m2/repository/com/alphacephei/vosk/${VOSK_VERSION}/vosk-${VOSK_VERSION}.jar"
    if [ -f "$m2_jar" ]; then
        info "从本地 Maven 缓存提取..."
        vosk_jar="$m2_jar"
    else
        local jar_url="https://repo1.maven.org/maven2/com/alphacephei/vosk/${VOSK_VERSION}/vosk-${VOSK_VERSION}.jar"
        local WORK_DIR=$(mktemp -d)
        vosk_jar="${WORK_DIR}/vosk-${VOSK_VERSION}.jar"
        info "正在从 Maven Central 下载 vosk-${VOSK_VERSION}.jar ..."
        download_file "$jar_url" "$vosk_jar"
    fi

    local EXTRACT_DIR=$(mktemp -d)
    unzip -q -o "$vosk_jar" "${jar_subdir}/${VOSK_LIB}" -d "$EXTRACT_DIR" 2>/dev/null || true

    if [ -f "${EXTRACT_DIR}/${jar_subdir}/${VOSK_LIB}" ]; then
        cp "${EXTRACT_DIR}/${jar_subdir}/${VOSK_LIB}" "$LIB_DIR/"
        info "Vosk 原生库提取完成: ${VOSK_LIB}"
    else
        error "未在 JAR 中找到 ${jar_subdir}/${VOSK_LIB}"
        rm -rf "$EXTRACT_DIR" "${WORK_DIR:-}"
        return 1
    fi

    rm -rf "$EXTRACT_DIR" "${WORK_DIR:-}"
    echo ""
}

# ============================================================
# 下载所有原生库
# ============================================================
download_libs() {
    download_jni_lib
    download_vosk_lib
}

# ============================================================
# 下载全部基础依赖
# ============================================================
download_base_all() {
    download_vad
    download_libs
}

# ============================================================
# 清理
# ============================================================
clean_base() {
    warn "========== 清理基础依赖 =========="

    if [ -f "${VAD_MODEL_DIR}/${VAD_MODEL_NAME}" ]; then
        rm -f "${VAD_MODEL_DIR}/${VAD_MODEL_NAME}"
        info "已删除 VAD 模型: ${VAD_MODEL_NAME}"
    fi

    local cleaned=0
    for pattern in \
        "libsherpa-onnx-jni.*" "libsherpa-onnx-c-api.*" "libsherpa-onnx-cxx-api.*" \
        "sherpa-onnx-jni.*" "sherpa-onnx-c-api.*" \
        "libonnxruntime*" "onnxruntime.*" \
        "libvosk.*" "vosk.dll"; do
        for f in "${LIB_DIR}"/${pattern}; do
            if [ -f "$f" ] || [ -L "$f" ]; then
                rm -f "$f"
                info "已删除: $(basename "$f")"
                cleaned=$((cleaned + 1))
            fi
        done
    done

    [ "$cleaned" -eq 0 ] && info "lib/ 目录无需清理"
    info "清理完成！"
}

# ============================================================
# 状态
# ============================================================
show_base_status() {
    detect_platform

    # VAD
    if [ -f "${VAD_MODEL_DIR}/${VAD_MODEL_NAME}" ]; then
        echo -e "  VAD (语音检测):    ${GREEN}✓ 已下载${NC} - ${VAD_MODEL_NAME}"
    else
        echo -e "  VAD (语音检测):    ${RED}✗ 未下载${NC} - ${VAD_MODEL_NAME}"
    fi

    # sherpa-onnx JNI
    if [ -f "${LIB_DIR}/${SHERPA_JNI_LIB}" ]; then
        echo -e "  sherpa-onnx JNI:   ${GREEN}✓ 已存在${NC} - ${SHERPA_JNI_LIB}"
    else
        echo -e "  sherpa-onnx JNI:   ${RED}✗ 不存在${NC} - ${SHERPA_JNI_LIB}"
    fi

    # onnxruntime
    if [ -f "${LIB_DIR}/${ONNXRT_LIB}" ] || ls "${LIB_DIR}"/libonnxruntime.*.${LIB_EXT} &>/dev/null 2>&1; then
        echo -e "  onnxruntime:       ${GREEN}✓ 已存在${NC} - ${ONNXRT_LIB}"
    else
        echo -e "  onnxruntime:       ${RED}✗ 不存在${NC} - ${ONNXRT_LIB}"
    fi

    # Vosk
    if [ -f "${LIB_DIR}/${VOSK_LIB}" ]; then
        echo -e "  Vosk 原生库:       ${GREEN}✓ 已存在${NC} - ${VOSK_LIB}"
    else
        echo -e "  Vosk 原生库:       ${RED}✗ 不存在${NC} - ${VOSK_LIB}"
    fi
}

# ============================================================
# 独立运行时的入口
# ============================================================
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    case "${1:-all}" in
        vad)        download_vad ;;
        jni)        download_jni_lib ;;
        vosk-lib)   download_vosk_lib ;;
        libs)       download_libs ;;
        all)        download_base_all ;;
        clean)      clean_base ;;
        status)     detect_platform; echo ""; info "========== 基础依赖状态 (${PLATFORM}) =========="; show_base_status; echo "" ;;
        *)
            echo "用法: $0 [vad|jni|vosk-lib|libs|all|clean|status]"
            echo ""
            echo "  vad      - 下载 VAD 语音检测模型 (silero_vad)"
            echo "  jni      - 下载 sherpa-onnx JNI 原生库 (含 onnxruntime)"
            echo "  vosk-lib - 下载 Vosk 原生库"
            echo "  libs     - 下载所有原生库 (JNI + Vosk)"
            echo "  all      - 下载全部基础依赖 (默认)"
            echo "  clean    - 清理所有基础依赖"
            echo "  status   - 查看状态"
            exit 1
            ;;
    esac
fi
