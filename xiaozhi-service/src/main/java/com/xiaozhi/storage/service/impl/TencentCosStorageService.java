package com.xiaozhi.storage.service.impl;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.region.Region;
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
 * 腾讯云 COS 存储实现。
 * <p>
 * ConfigBO 字段映射：
 * <ul>
 *   <li>apiKey → SecretId</li>
 *   <li>apiSecret → SecretKey</li>
 *   <li>appId → Region</li>
 *   <li>configName → BucketName</li>
 *   <li>apiUrl → 路径前缀（可选，默认 "uploads/"）</li>
 * </ul>
 */
public class TencentCosStorageService implements StorageService {

    private static final Logger logger = LoggerFactory.getLogger(TencentCosStorageService.class);

    private final COSClient cosClient;
    private final String bucketName;
    private final String pathPrefix;
    private final String urlPrefix;

    public TencentCosStorageService(ConfigBO config) {
        String region = config.getAppId();
        COSCredentials cred = new BasicCOSCredentials(config.getApiKey(), config.getApiSecret());
        this.cosClient = new COSClient(cred, new ClientConfig(new Region(region)));
        this.bucketName = config.getConfigName();
        this.urlPrefix = "https://" + bucketName + ".cos." + region + ".myqcloud.com/";
        String prefix = config.getApiUrl();
        if (prefix == null || prefix.isEmpty()) {
            prefix = "uploads/";
        }
        if (!prefix.endsWith("/")) {
            prefix = prefix + "/";
        }
        this.pathPrefix = prefix;
    }

    @Override

    public String upload(MultipartFile file, String relativePath, String fileName) throws IOException {
        String key = pathPrefix + relativePath + "/" + fileName;
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.getSize());
            String contentType = file.getContentType();
            if (contentType != null && !contentType.isEmpty()) {
                metadata.setContentType(contentType);
            }
            cosClient.putObject(new PutObjectRequest(bucketName, key, file.getInputStream(), metadata));
            return urlPrefix + key;
        } catch (Exception e) {
            throw new IOException("上传到腾讯云 COS 失败: " + e.getMessage(), e);
        }
    }

    @Override

    public String upload(Path localFile, String objectKey) throws IOException {
        try (InputStream is = Files.newInputStream(localFile)) {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(Files.size(localFile));
            cosClient.putObject(new PutObjectRequest(bucketName, objectKey, is, metadata));
            return urlPrefix + objectKey;
        } catch (Exception e) {
            throw new IOException("上传到腾讯云 COS 失败: " + e.getMessage(), e);
        } finally {
            Files.deleteIfExists(localFile);
        }
    }

    @Override
    public byte[] download(String storedPath) {
        String key = extractObjectKey(storedPath);
        if (key == null) return null;
        try {
            COSObject cosObject = cosClient.getObject(bucketName, key);
            try (InputStream is = cosObject.getObjectContent()) {
                return is.readAllBytes();
            }
        } catch (Exception e) {
            logger.warn("从 COS 下载失败: {}", storedPath, e);
            return null;
        }
    }

    @Override
    public void remove(String storedPath) {
        String key = extractObjectKey(storedPath);
        if (key == null) return;
        try {
            cosClient.deleteObject(bucketName, key);
        } catch (Exception e) {
            logger.warn("从 COS 删除失败: {}", storedPath, e);
        }
    }

    @Override
    public boolean exists(String storedPath) {
        String key = extractObjectKey(storedPath);
        if (key == null) return false;
        try {
            cosClient.getObjectMetadata(bucketName, key);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getProvider() {
        return "tencent";
    }

    private String extractObjectKey(String storedPath) {
        if (storedPath == null) return null;
        return storedPath.startsWith(urlPrefix) ? storedPath.substring(urlPrefix.length()) : storedPath;
    }

    public void shutdown() {
        cosClient.shutdown();
    }
}
