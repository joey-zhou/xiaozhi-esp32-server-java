package com.xiaozhi.music;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xiaozhi.common.config.RuntimePathConfig;
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
    public String uploadMusic(@Parameter(description = "上传的音乐文件") @RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return "上传失败";
        }
        StorageService.assertAllowed(file);

        // getOriginalFilename 原样返回客户端给的 Content-Disposition filename，含目录分隔符时会写出音乐目录之外
        String safeName = baseName(file.getOriginalFilename());
        if (!StringUtils.hasText(safeName)) {
            return "上传失败";
        }
        if (!safeName.equals("playlist.txt") && !safeName.endsWith(".mp3")) {
            return "上传失败";
        }
        try {
            Path musicPath = Path.of(runtimePathConfig.getMusicDir()).toAbsolutePath().normalize();
            Files.createDirectories(musicPath);
            Path target = musicPath.resolve(safeName).normalize();
            if (!target.startsWith(musicPath)) {
                return "上传失败";
            }
            file.transferTo(target);
            return safeName + "，上传成功";
        } catch (IOException e) {
            log.error("上传失败", e);
            return "上传失败";
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
