package com.xiaozhi.storage.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;

/**
 * 对象存储服务接口。
 * 通过 {@link StorageServiceFactory} 获取当前生效的实现。
 * <p>
 * 支持本地文件存储和多云 OSS（腾讯云 COS、阿里云 OSS）。
 * 配置通过 sys_config 表（configType="oss"）管理，前端管理员可在设置页面选择存储方式。
 */
public interface StorageService {

    /** 默认文件大小上限：50MB */
    long DEFAULT_MAX_SIZE = 50 * 1024 * 1024;

    /**
     * 上传文件（Web 端）
     *
     * @param file         上传的文件
     * @param relativePath 相对路径（如 "avatar/2026/02/28"）
     * @param fileName     文件名（如 "xxxx.png"）
     * @return 访问路径（本地返回相对路径，云端返回完整 URL）
     * @throws IOException 上传失败
     */
    String upload(MultipartFile file, String relativePath, String fileName) throws IOException;

    /**
     * 上传本地文件（内部使用，如音频缓存）。
     * 方法会接管 localFile 的生命周期，调用者不再需要关心源文件。
     *
     * @param localFile 本地文件
     * @param objectKey 存储键（本地作为相对路径，云端作为对象键）
     * @return 存储路径（本地返回文件路径，云端返回完整 URL）
     * @throws IOException 上传失败
     */
    String upload(Path localFile, String objectKey) throws IOException;

    /**
     * 下载文件内容
     *
     * @param storedPath {@link #upload} 返回的路径
     * @return 文件字节，不存在或失败返回 {@code null}
     */
    byte[] download(String storedPath);

    /**
     * 删除文件（静默处理不存在的情况）
     */
    void remove(String storedPath);

    /**
     * 检查文件是否存在
     */
    boolean exists(String storedPath);

    /**
     * 获取 provider 名称，用于 Factory 路由
     */
    String getProvider();

    /**
     * 检查文件大小
     */
    static void assertAllowed(MultipartFile file) {
        if (file.getSize() > DEFAULT_MAX_SIZE) {
            throw new IllegalArgumentException("文件大小超过限制，最大允许：" + (DEFAULT_MAX_SIZE / 1024 / 1024) + "MB");
        }
    }
}
