package com.xiaozhi.file;

import com.xiaozhi.common.exception.OperationFailedException;
import com.xiaozhi.common.model.resp.FileUploadResp;
import com.xiaozhi.communication.ServerAddressProvider;
import com.xiaozhi.storage.UploadPolicy;
import com.xiaozhi.storage.service.StorageService;
import com.xiaozhi.storage.service.StorageServiceFactory;
import com.xiaozhi.utils.DateUtils;
import com.xiaozhi.utils.FileHashUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 通用上传：按类别校验后存进当前生效的对象存储，按 类别/yyyy/MM/dd 分目录、换成随机文件名
 */
@Slf4j
@Service
public class FileUploadAppService {

    @Resource
    private StorageServiceFactory storageServiceFactory;
    @Resource
    private ServerAddressProvider serverAddressProvider;
    @Resource
    private UploadPolicy uploadPolicy;

    public FileUploadResp upload(MultipartFile file, String type) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        UploadPolicy.Category category = UploadPolicy.Category.of(type);
        String originalFilename = file.getOriginalFilename();
        uploadPolicy.check(category, originalFilename, file.getSize(), head(file));

        String relativePath = category.key() + "/" + DateUtils.today().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String fileName = UUID.randomUUID().toString().replace("-", "") + UploadPolicy.extensionOf(originalFilename);
        StorageService storageService = storageServiceFactory.getStorageService();
        String stored;
        String hash;
        try {
            try (InputStream content = file.getInputStream()) {
                stored = storageService.upload(content, file.getSize(), relativePath, fileName);
            }
            try (InputStream content = file.getInputStream()) {
                hash = FileHashUtil.sha256(content);
            }
        } catch (IOException e) {
            throw new OperationFailedException("文件上传失败，请稍后重试", e);
        }
        log.info("文件上传成功（{}）: {}", storageService.getProvider(), stored);

        FileUploadResp resp = new FileUploadResp();
        resp.setFileName(originalFilename);
        resp.setNewFileName(fileName);
        resp.setHash(hash);
        if (stored.startsWith("http://") || stored.startsWith("https://")) {
            // 云存储私有桶下裸地址访问不了，给带签名的临时地址供即时预览
            resp.setUrl(storageServiceFactory.accessUrlOf(stored));
        } else {
            // url 供即时预览，受保护目录要带签名；relativePath 是入库用的裸路径
            resp.setUrl(serverAddressProvider.getServerAddress() + "/" + storageServiceFactory.accessUrlOf(stored));
            resp.setRelativePath(stored);
        }
        return resp;
    }

    private static byte[] head(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(UploadPolicy.HEAD_BYTES);
        } catch (IOException e) {
            throw new IllegalArgumentException("上传文件读取失败", e);
        }
    }
}
