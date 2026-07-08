package com.xiaozhi.storage.service.impl;

import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.storage.service.StorageService;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import lombok.extern.slf4j.Slf4j;
/**
 * S3 兼容对象存储实现（MinIO / AWS S3 / 其它 S3 兼容服务）。
 * <p>
 * 采用 path-style 访问（{@code forcePathStyle(true)}），兼容 MinIO 等未配置虚拟主机域名的服务。
 * ConfigBO 字段映射：
 * <ul>
 *   <li>apiUrl → Endpoint（如 http://localhost:9000）</li>
 *   <li>ak → Access Key</li>
 *   <li>sk → Secret Key</li>
 *   <li>configName → Bucket 名称</li>
 *   <li>appId → Region（可选，默认 us-east-1）</li>
 * </ul>
 */
@Slf4j
public class S3StorageService implements StorageService {

    /** 签名 URL 有效期：1 小时 */
    private static final Duration PRESIGN_EXPIRE = Duration.ofHours(1);

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String bucketName;
    private final String urlPrefix;

    public S3StorageService(ConfigBO config) {
        String endpoint = config.getApiUrl();
        String region = config.getAppId();
        if (region == null || region.isEmpty()) {
            region = "us-east-1";
        }
        this.bucketName = config.getConfigName();
        StaticCredentialsProvider credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(config.getAk(), config.getSk()));
        URI endpointUri = URI.create(endpoint);
        // path-style：URL 形如 <endpoint>/<bucket>/<key>，MinIO 必需
        S3Configuration s3Config = S3Configuration.builder().pathStyleAccessEnabled(true).build();
        this.s3Client = S3Client.builder()
                .endpointOverride(endpointUri)
                .region(Region.of(region))
                .credentialsProvider(credentials)
                .serviceConfiguration(s3Config)
                .build();
        this.s3Presigner = S3Presigner.builder()
                .endpointOverride(endpointUri)
                .region(Region.of(region))
                .credentialsProvider(credentials)
                .serviceConfiguration(s3Config)
                .build();
        String host = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        this.urlPrefix = host + "/" + bucketName + "/";
    }

    @Override
    public String upload(MultipartFile file, String relativePath, String fileName) throws IOException {
        String key = relativePath + "/" + fileName;
        try {
            PutObjectRequest.Builder req = PutObjectRequest.builder().bucket(bucketName).key(key);
            String contentType = file.getContentType();
            if (contentType != null && !contentType.isEmpty()) {
                req.contentType(contentType);
            }
            s3Client.putObject(req.build(), RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
            return urlPrefix + key;
        } catch (Exception e) {
            throw new IOException("上传到 S3 失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String upload(Path localFile, String objectKey) throws IOException {
        try (InputStream is = Files.newInputStream(localFile)) {
            long size = Files.size(localFile);
            // 显式设置 Content-Type：否则以 octet-stream 存储，OGG/Opus 在浏览器里表现为 0s、无法播放
            PutObjectRequest req = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .contentType(StorageContentTypes.resolve(localFile))
                    .build();
            s3Client.putObject(req, RequestBody.fromInputStream(is, size));
            return urlPrefix + objectKey;
        } catch (Exception e) {
            throw new IOException("上传到 S3 失败: " + e.getMessage(), e);
        } finally {
            Files.deleteIfExists(localFile);
        }
    }

    @Override
    public byte[] download(String storedPath) {
        String key = extractObjectKey(storedPath);
        if (key == null) return null;
        try {
            return s3Client.getObjectAsBytes(GetObjectRequest.builder().bucket(bucketName).key(key).build())
                    .asByteArray();
        } catch (Exception e) {
            log.warn("从 S3 下载失败: {}", storedPath, e);
            return null;
        }
    }

    @Override
    public void remove(String storedPath) {
        String key = extractObjectKey(storedPath);
        if (key == null) return;
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucketName).key(key).build());
        } catch (Exception e) {
            log.warn("从 S3 删除失败: {}", storedPath, e);
        }
    }

    @Override
    public boolean exists(String storedPath) {
        String key = extractObjectKey(storedPath);
        if (key == null) return false;
        try {
            s3Client.headObject(HeadObjectRequest.builder().bucket(bucketName).key(key).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getProvider() {
        return "s3";
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
            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(PRESIGN_EXPIRE)
                    .getObjectRequest(GetObjectRequest.builder().bucket(bucketName).key(key).build())
                    .build();
            return s3Presigner.presignGetObject(presignRequest).url().toString();
        } catch (Exception e) {
            log.warn("生成 S3 签名 URL 失败，返回原路径: {}", storedPath, e);
            return storedPath;
        }
    }

    private String extractObjectKey(String storedPath) {
        if (storedPath == null) return null;
        // 先去掉可能存在的 query 串（如已签名 URL 的 ?X-Amz-Signature=...），保证重新签名幂等
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
        s3Client.close();
        s3Presigner.close();
    }
}
