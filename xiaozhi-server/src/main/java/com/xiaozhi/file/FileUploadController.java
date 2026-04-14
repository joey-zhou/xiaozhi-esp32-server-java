package com.xiaozhi.file;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xiaozhi.common.exception.OperationFailedException;
import com.xiaozhi.common.web.ApiResponse;
import com.xiaozhi.communication.ServerAddressProvider;
import com.xiaozhi.storage.service.StorageService;
import com.xiaozhi.storage.service.StorageServiceFactory;
import com.xiaozhi.utils.FileHashUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 文件上传控制器
 * 
 * @author Joey
 */
@RestController
@RequestMapping("/api/file")
@Tag(name = "文件上传控制器", description = "文件上传相关操作")
public class FileUploadController {
    private static final Logger logger = LoggerFactory.getLogger(FileUploadController.class);

    /** 允许的文件类型分类（防止路径遍历） */
    private static final Set<String> ALLOWED_TYPES = Set.of("common", "image", "audio", "video", "document", "avatar");

    /** 允许的文件扩展名白名单 */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp", ".svg",
            ".mp3", ".wav", ".ogg", ".opus", ".flac", ".aac", ".m4a",
            ".mp4", ".avi", ".mov", ".mkv", ".webm",
            ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx", ".txt", ".csv",
            ".zip", ".rar", ".7z", ".tar", ".gz",
            ".bin"
    );

    @Resource
    private StorageServiceFactory storageServiceFactory;

    @Autowired
    private ServerAddressProvider serverAddressProvider;

    /**
     * 通用文件上传方法
     * 
     * @param file 上传的文件
     * @param type 文件类型（可选，用于分类存储）
     * @return 文件访问URL
     */
    @PostMapping("/upload")
    @ResponseBody
    @SaCheckPermission("system:file:api:upload")
    @Operation(summary = "文件上传", description = "如果有配置腾讯云对象存储的话默认会存储到对象存储中")
    public ApiResponse<?> uploadFile(
            @Parameter(description = "上传的文件") @RequestParam("file") MultipartFile file,
            @Parameter(description = "文件类型") @RequestParam(value = "type", required = false, defaultValue = "common") String type) {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }

        // 检查文件大小
        StorageService.assertAllowed(file);

        // 防止路径遍历：type 必须在白名单中
        if (!ALLOWED_TYPES.contains(type)) {
            throw new IllegalArgumentException("不支持的文件类型分类: " + type);
        }

        // 验证文件名和扩展名
        String originalFilename = file.getOriginalFilename();
        if (!StringUtils.hasText(originalFilename) || !originalFilename.contains(".")) {
            throw new IllegalArgumentException("文件名无效或缺少扩展名");
        }
        String extension = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("不支持的文件扩展名: " + extension);
        }

        // 验证 MIME 类型与扩展名一致性
        String contentType = file.getContentType();
        if (contentType != null && !isContentTypeMatchExtension(contentType, extension)) {
            throw new IllegalArgumentException("文件MIME类型与扩展名不匹配");
        }

        // 构建文件存储路径，按日期和类型分类
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String relativePath = type + "/" + datePath;

        // 生成唯一文件名
        String fileName = UUID.randomUUID().toString().replaceAll("-", "") + extension;

        StorageService storageService = storageServiceFactory.getStorageService();
        String filePathOrUrl;
        try {
            filePathOrUrl = storageService.upload(file, relativePath, fileName);
        } catch (IOException e) {
            logger.error("文件上传失败: {}", e.getMessage(), e);
            throw new OperationFailedException("文件上传失败，请稍后重试", e);
        }

        logger.info("文件上传成功（{}）: {}", storageService.getProvider(), filePathOrUrl);

        // 计算文件哈希值
        String fileHash = FileHashUtil.calculateSha256(file);

        Map<String, Object> data = new HashMap<>();
        data.put("fileName", originalFilename);
        data.put("newFileName", fileName);
        data.put("hash", fileHash);

        // 判断是否是完整 URL（云存储返回 https URL，本地返回相对路径）
        if (filePathOrUrl.startsWith("http://") || filePathOrUrl.startsWith("https://")) {
            data.put("url", filePathOrUrl);
        } else {
            String fullUrl = serverAddressProvider.getServerAddress() + "/" + filePathOrUrl;
            data.put("url", fullUrl);
            data.put("relativePath", filePathOrUrl);
        }

        return ApiResponse.success("上传成功", data);
    }

    /**
     * 验证 MIME 类型与文件扩展名是否匹配
     */
    private boolean isContentTypeMatchExtension(String contentType, String extension) {
        String ct = contentType.toLowerCase();
        return switch (extension) {
            case ".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp", ".svg" -> ct.startsWith("image/");
            case ".mp3", ".wav", ".ogg", ".opus", ".flac", ".aac", ".m4a" -> ct.startsWith("audio/") || ct.equals("application/ogg");
            case ".mp4", ".avi", ".mov", ".mkv", ".webm" -> ct.startsWith("video/");
            case ".pdf" -> ct.equals("application/pdf");
            case ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx" -> ct.startsWith("application/");
            case ".txt", ".csv" -> ct.startsWith("text/");
            case ".zip", ".rar", ".7z", ".tar", ".gz" -> ct.startsWith("application/");
            case ".bin" -> ct.equals("application/octet-stream") || ct.startsWith("application/");
            default -> true;
        };
    }
}
