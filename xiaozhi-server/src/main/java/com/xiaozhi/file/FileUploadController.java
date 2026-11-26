package com.xiaozhi.file;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xiaozhi.common.annotation.AuditLog;
import com.xiaozhi.common.model.resp.FileUploadResp;
import com.xiaozhi.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件上传控制器
 * 
 * @author Joey
 */
@RestController
@RequestMapping("/api/file")
@Tag(name = "文件上传控制器", description = "文件上传相关操作")
public class FileUploadController {

    @Resource
    private FileUploadAppService fileUploadAppService;

    /**
     * 通用文件上传，按 type 类别校验扩展名、文件头与大小（{@code UploadPolicy}）
     */
    @PostMapping("/upload")
    @ResponseBody
    @SaCheckPermission("system:file:api:upload")
    @AuditLog(module = "文件管理", operation = "上传文件")
    @Operation(summary = "文件上传", description = "存到当前生效的对象存储")
    public ApiResponse<FileUploadResp> uploadFile(
            @Parameter(description = "上传的文件") @RequestParam("file") MultipartFile file,
            @Parameter(description = "文件类型") @RequestParam(value = "type", required = false, defaultValue = "common") String type) {
        return ApiResponse.success("上传成功", fileUploadAppService.upload(file, type));
    }
}
