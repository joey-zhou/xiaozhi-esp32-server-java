package com.xiaozhi.storage.service.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.ObjectMetadata;
import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.storage.service.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 阿里云 OSS 存储实现。
 * <p>
 * ConfigBO 字段映射：
 * <ul>
 *   <li>ak → AccessKey ID</li>
 *   <li>sk → AccessKey Secret</li>
 *   <li>apiUrl → Endpoint（如 oss-cn-hangzhou.aliyuncs.com）</li>
 *   <li>configName → Bucket 名称</li>
 * </ul>
 */
public class AliyunOssStorageService implements StorageService {

    private static final Logger logger = LoggerFactory.getLogger(AliyunOssStorageService.class);

    private final OSS ossClient;
    private final String bucketName;
    private final String urlPrefix;

    public AliyunOssStorageService(ConfigBO config) {
        String endpoint = config.getApiUrl();
        this.bucketName = config.getConfigName();
        this.ossClient = new OSSClientBuilder().build(endpoint, config.getAk(), config.getSk());
        String host = endpoint.replaceFirst("^https?://", "");
        this.urlPrefix = "https://" + bucketName + "." + host + "/";
    }

    @Override

    public String upload(MultipartFile file, String relativePath, String fileName) throws IOException {
        String key = relativePath + "/" + fileName;
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.getSize());
            String contentType = file.getContentType();
            if (contentType != null && !contentType.isEmpty()) {
                metadata.setContentType(contentType);
            }
            ossClient.putObject(bucketName, key, file.getInputStream(), metadata);
            return urlPrefix + key;
        } catch (Exception e) {
            throw new IOException("上传到阿里云 OSS 失败: " + e.getMessage(), e);
        }
    }

    @Override

    public String upload(Path localFile, String objectKey) throws IOException {
        try (InputStream is = Files.newInputStream(localFile)) {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(Files.size(localFile));
            ossClient.putObject(bucketName, objectKey, is, metadata);
            return urlPrefix + objectKey;
        } catch (Exception e) {
            throw new IOException("上传到阿里云 OSS 失败: " + e.getMessage(), e);
        } finally {
            Files.deleteIfExists(localFile);
        }
    }

    @Override
    public byte[] download(String storedPath) {
        String key = extractObjectKey(storedPath);
        if (key == null) return null;
        try {
            OSSObject ossObject = ossClient.getObject(bucketName, key);
            try (InputStream is = ossObject.getObjectContent()) {
                return is.readAllBytes();
            }
        } catch (Exception e) {
            logger.warn("从 OSS 下载失败: {}", storedPath, e);
            return null;
        }
    }

    @Override
    public void remove(String storedPath) {
        String key = extractObjectKey(storedPath);
        if (key == null) return;
        try {
            ossClient.deleteObject(bucketName, key);
        } catch (Exception e) {
            logger.warn("从 OSS 删除失败: {}", storedPath, e);
        }
    }

    @Override
    public boolean exists(String storedPath) {
        String key = extractObjectKey(storedPath);
        if (key == null) return false;
        try {
            return ossClient.doesObjectExist(bucketName, key);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getProvider() {
        return "aliyun";
    }

    private String extractObjectKey(String storedPath) {
        if (storedPath == null) return null;
        return storedPath.startsWith(urlPrefix) ? storedPath.substring(urlPrefix.length()) : storedPath;
    }

    public void shutdown() {
        ossClient.shutdown();
    }
}
