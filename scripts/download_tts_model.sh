#!/bin/bash
# 下载本地TTS模型脚本 - vits-melo-tts-zh_en（中英文）
# 该模型为 sherpa-onnx 本地语音合成的默认模型

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
MODEL_DIR="$PROJECT_DIR/models/tts"

mkdir -p "$MODEL_DIR"
cd "$MODEL_DIR"

if [ -d "vits-melo-tts-zh_en" ] && [ -f "vits-melo-tts-zh_en/model.onnx" ]; then
    echo "模型已存在: $MODEL_DIR/vits-melo-tts-zh_en/"
    echo "如需重新下载，请先删除该目录"
    exit 0
fi

echo "正在下载 vits-melo-tts-zh_en 中英文TTS模型..."
echo "模型大小约 163MB，请耐心等待..."

wget -c https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-melo-tts-zh_en.tar.bz2

echo "正在解压模型..."
tar xvf vits-melo-tts-zh_en.tar.bz2

rm -f vits-melo-tts-zh_en.tar.bz2

echo ""
echo "TTS模型下载完成！"
echo "模型路径: $MODEL_DIR/vits-melo-tts-zh_en/"
echo "包含文件: model.onnx, tokens.txt, lexicon.txt"
