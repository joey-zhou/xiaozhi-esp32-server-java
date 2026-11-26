package com.xiaozhi.music;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xiaozhi.common.annotation.AuditLog;
import com.xiaozhi.common.web.ApiResponse;
import com.xiaozhi.music.service.MusicFileService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/file")
@Tag(name = "音乐控制器", description = "音乐相关操作")
public class MusicController {

    @Resource
    private MusicFileService musicFileService;

    @PostMapping("/music")
    @ResponseBody
    @SaCheckPermission("system:file:api:upload")
    @AuditLog(module = "音乐管理", operation = "上传音乐")
    public ApiResponse<String> uploadMusic(@Parameter(description = "上传的音乐文件") @RequestParam("file") MultipartFile file) {
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("上传文件读取失败", e);
        }
        return ApiResponse.success("上传成功", musicFileService.save(file.getOriginalFilename(), content));
    }
}
