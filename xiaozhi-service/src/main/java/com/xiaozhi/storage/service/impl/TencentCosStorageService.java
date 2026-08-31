package com.xiaozhi.storage.service.impl;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.http.HttpMethodName;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.region.Region;
import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.storage.service.StorageService;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;

import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class TencentCosStorageService implements StorageService {

    /** 签名 URL 有效期：1 小时 */
    private static final long PRESIGN_EXPIRE_MILLIS = 60 * 60 * 1000L;

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
            // 显式设置 Content-Type：否则 COS 以 octet-stream 存储，OGG/Opus 在浏览器里表现为 0s、无法播放
            metadata.setContentType(StorageContentTypes.resolve(localFile));
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
            log.warn("从 COS 下载失败: {}", storedPath, e);
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
            log.warn("从 COS 删除失败: {}", storedPath, e);
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

    @Override
    public String getAccessUrl(String storedPath) {
        // 仅对本 bucket 的完整 URL 签名；空值、外链、本地相对路径原样返回
        // （云端上传持久化的始终是 urlPrefix 开头的完整 URL，含已签名 URL 亦以此开头）
        if (storedPath == null || !storedPath.startsWith(urlPrefix)) {
            return storedPath;
        }
        try {
            String key = extractObjectKey(storedPath);
            Date expiration = new Date(System.currentTimeMillis() + PRESIGN_EXPIRE_MILLIS);
            return cosClient.generatePresignedUrl(bucketName, key, expiration, HttpMethodName.GET).toString();
        } catch (Exception e) {
            log.warn("生成 COS 签名 URL 失败，返回原路径: {}", storedPath, e);
            return storedPath;
        }
    }

    private String extractObjectKey(String storedPath) {
        if (storedPath == null) return null;
        // 先去掉可能存在的 query 串（如已签名 URL 的 ?sign=...），保证重新签名幂等
        int queryIdx = storedPath.indexOf('?');
        String path = queryIdx >= 0 ? storedPath.substring(0, queryIdx) : storedPath;
        return path.startsWith(urlPrefix) ? path.substring(urlPrefix.length()) : path;
    }

    @Override
    public String stripSignature(String url) {
        // 仅处理本 bucket 的 URL：截断签名 query 还原裸 URL；其它值原样返回
        if (url == null || !url.startsWith(urlPrefix)) {
            return url;
        }
        int queryIdx = url.indexOf('?');
        return queryIdx >= 0 ? url.substring(0, queryIdx) : url;
    }

    public void shutdown() {
        cosClient.shutdown();
    }
}
