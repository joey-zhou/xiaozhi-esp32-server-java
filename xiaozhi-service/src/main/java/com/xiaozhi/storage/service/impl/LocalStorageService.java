package com.xiaozhi.storage.service.impl;

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

    @Value("${xiaozhi.upload-path:uploads}")
    private String baseDir;

    @Override

    public String upload(MultipartFile file, String relativePath, String fileName) throws IOException {
        String fullPath = baseDir;
        if (!relativePath.isEmpty()) {
            fullPath = fullPath + File.separator + relativePath;
        }

        File directory = new File(fullPath);
        if (!directory.exists()) {
            boolean created = directory.mkdirs();
            if (!created) {
                throw new IOException("无法创建目录: " + fullPath);
            }
        }

        File destFile = new File(directory, fileName);
        try (FileOutputStream fos = new FileOutputStream(destFile);
            InputStream inputStream = file.getInputStream()) {
            inputStream.transferTo(fos);
        }

        // 返回相对路径（统一使用正斜杠，便于 URL 访问）
        String relativeFilePath = baseDir + File.separator + relativePath + File.separator + fileName;
        return relativeFilePath.replace(File.separator, "/");
    }

    @Override

    public String upload(Path localFile, String objectKey) throws IOException {
        Path target = Path.of(objectKey);
        if (!localFile.equals(target)) {
            Files.createDirectories(target.getParent());
            Files.move(localFile, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target.toString();
    }

    @Override
    public byte[] download(String storedPath) {
        try {
            Path path = Path.of(storedPath);
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
            Files.deleteIfExists(Path.of(storedPath));
        } catch (Exception e) {
            log.warn("删除本地文件失败: {}", storedPath, e);
        }
    }

    @Override
    public boolean exists(String storedPath) {
        return storedPath != null && Files.exists(Path.of(storedPath));
    }

    @Override
    public String getProvider() {
        return "local";
    }
}
