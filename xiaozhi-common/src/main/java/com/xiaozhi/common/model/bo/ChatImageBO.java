package com.xiaozhi.common.model.bo;

/**
 * 交给模型看的一张图
 *
 * @param mimeType 如 image/png
 */
public record ChatImageBO(String mimeType, byte[] content) {
}
