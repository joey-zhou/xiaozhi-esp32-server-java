package com.xiaozhi.music;

import com.xiaozhi.common.config.RuntimePathConfig;
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

@RestController
@RequestMapping("/api/file")
@Tag(name = "音乐控制器", description = "音乐相关操作")
@Slf4j
public class MusicController {

    @Resource
    private RuntimePathConfig runtimePathConfig;

    @PostMapping("/music")
    @ResponseBody
    public String uploadMusic(@Parameter(description = "上传的音乐文件") @RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return "上传失败";
        }

        String originalFilename = file.getOriginalFilename();
        if (!StringUtils.hasText(originalFilename)) {
            return "上传失败";
        }
        if (!originalFilename.equals("playlist.txt") && !originalFilename.endsWith(".mp3")) {
            return "上传失败";
        }
        try {
            Path musicPath = Path.of(runtimePathConfig.getMusicDir());
            Files.createDirectories(musicPath);
            file.transferTo(musicPath.resolve(originalFilename));
            return originalFilename + "，上传成功";
        } catch (IOException e) {
            log.error("上传失败", e);
            return "上传失败";
        }
    }
}
