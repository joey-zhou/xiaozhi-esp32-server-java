package com.xiaozhi.music;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xiaozhi.common.config.RuntimePathConfig;
import com.xiaozhi.common.exception.OperationFailedException;
import com.xiaozhi.common.web.ApiResponse;
import com.xiaozhi.storage.service.StorageService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/api/file")
@Tag(name = "音乐控制器", description = "音乐相关操作")
@Slf4j
public class MusicController {

    @Resource
    private RuntimePathConfig runtimePathConfig;

    @PostMapping("/music")
    @ResponseBody
    @SaCheckPermission("system:file:api:upload")
    public ApiResponse<String> uploadMusic(@Parameter(description = "上传的音乐文件") @RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        StorageService.assertAllowed(file);

        // getOriginalFilename 原样返回客户端给的 Content-Disposition filename，含目录分隔符时会写出音乐目录之外
        String safeName = baseName(file.getOriginalFilename());
        if (!StringUtils.hasText(safeName)) {
            throw new IllegalArgumentException("文件名无效");
        }
        if (!safeName.equals("playlist.txt") && !safeName.endsWith(".mp3")) {
            throw new IllegalArgumentException("仅支持 .mp3 或 playlist.txt");
        }
        try {
            Path musicPath = Path.of(runtimePathConfig.getMusicDir()).toAbsolutePath().normalize();
            Files.createDirectories(musicPath);
            Path target = musicPath.resolve(safeName).normalize();
            if (!target.startsWith(musicPath)) {
                throw new IllegalArgumentException("非法文件名");
            }
            file.transferTo(target);
            return ApiResponse.success("上传成功", safeName);
        } catch (IOException e) {
            log.error("上传失败", e);
            throw new OperationFailedException("上传失败，请稍后重试", e);
        }
    }

    private static String baseName(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            return null;
        }
        Path fileName = Paths.get(originalFilename.replace('\\', '/')).getFileName();
        return fileName == null ? null : fileName.toString();
    }
}
