package com.xiaozhi.storage.service.impl;

import com.xiaozhi.common.config.RuntimePathConfig;
import com.xiaozhi.common.web.LocalFileUrlPolicy;
import jakarta.annotation.Resource;
import com.xiaozhi.storage.service.StorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import lombok.extern.slf4j.Slf4j;
/**
 * 本地文件存储实现
 */
@Slf4j
@Component
public class LocalStorageService implements StorageService {

    @Resource
    private LocalFileUrlPolicy localFileUrlPolicy;

    @Resource
    private RuntimePathConfig runtimePathConfig;

    @Value("${xiaozhi.upload-path:uploads}")
    private String baseDir;

    // resolveStorageKey 对绝对路径原样放行、不保证落在 dataDir 内，读写前必须再校验一次，否则外部传入的越界路径会被直接读写
    private boolean withinDataDir(Path resolved) {
        Path root = runtimePathConfig.resolveDataDir();
        return resolved.equals(root) || resolved.startsWith(root);
    }

    @Override

    public String upload(MultipartFile file, String relativePath, String fileName) throws IOException {
        String key = baseDir;
        if (!relativePath.isEmpty()) {
            key = key + File.separator + relativePath;
        }

        // key 是入库用的相对路径，文件按 data-dir 落到实际位置，两者不能混用
        File directory = runtimePathConfig.resolveStorageKey(key).toFile();
        if (!directory.exists()) {
            boolean created = directory.mkdirs();
            if (!created) {
                throw new IOException("无法创建目录: " + directory);
            }
        }

        File destFile = new File(directory, fileName);
        try (FileOutputStream fos = new FileOutputStream(destFile);
            InputStream inputStream = file.getInputStream()) {
            inputStream.transferTo(fos);
        }

        // 返回相对路径（统一使用正斜杠，便于 URL 访问）
        String relativeFilePath = key + File.separator + fileName;
        return relativeFilePath.replace(File.separator, "/");
    }

    @Override

    public String upload(Path localFile, String objectKey) throws IOException {
        Path target = runtimePathConfig.resolveStorageKey(objectKey);
        if (!localFile.toAbsolutePath().normalize().equals(target)) {
            Files.createDirectories(target.getParent());
            Files.move(localFile, target, StandardCopyOption.REPLACE_EXISTING);
        }
        // 返回传入的键而不是落地的绝对路径：入库的必须是相对键，否则签不上名也匹配不上 /audio/** 映射
        return objectKey.replace('\\', '/');
    }

    @Override
    public byte[] download(String storedPath) {
        try {
            Path path = runtimePathConfig.resolveStorageKey(storedPath);
            if (!withinDataDir(path)) {
                log.warn("拒绝越界的存储路径: {}", storedPath);
                return null;
            }
            return Files.exists(path) ? Files.readAllBytes(path) : null;
        } catch (Exception e) {
            log.warn("读取本地文件失败: {}", storedPath, e);
            return null;
        }
    }

    @Override
    public void remove(String storedPath) {
        if (storedPath == null) return;
        try {
            Path path = runtimePathConfig.resolveStorageKey(storedPath);
            if (!withinDataDir(path)) {
                log.warn("拒绝越界的存储路径: {}", storedPath);
                return;
            }
            Files.deleteIfExists(path);
        } catch (Exception e) {
            log.warn("删除本地文件失败: {}", storedPath, e);
        }
    }

    @Override
    public boolean exists(String storedPath) {
        if (storedPath == null) return false;
        Path path = runtimePathConfig.resolveStorageKey(storedPath);
        return withinDataDir(path) && Files.exists(path);
    }

    @Override
    public String getAccessUrl(String storedPath) {
        // 本地存储返回相对路径由前端拼接后端地址访问；受保护目录追加时效签名，与云端私有桶预签名对齐
        return localFileUrlPolicy.sign(storedPath);
    }

    @Override
    public String stripSignature(String url) {
        return localFileUrlPolicy.stripSignature(url);
    }

    @Override
    public String urlPrefix() {
        // 本地存储写出的是相对路径，没有前缀可言
        return "";
    }

    @Override
    public String getProvider() {
        return "local";
    }
}
