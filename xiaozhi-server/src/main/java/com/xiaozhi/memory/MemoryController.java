package com.xiaozhi.memory;

import com.xiaozhi.server.web.BaseController;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.xiaozhi.common.annotation.AuditLog;
import com.xiaozhi.common.annotation.CheckOwner;
import com.xiaozhi.common.web.ApiResponse;
import com.xiaozhi.summary.service.SummaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/memory")
@Tag(name = "记忆管理", description = "管理聊天相关的摘要记忆与长期记忆")
public class MemoryController extends BaseController {

    @Resource
    private SummaryService summaryService;

    @GetMapping("/summary/{roleId}/{deviceId}")
    @SaCheckPermission("system:role:memory:summary:api:list")
    @CheckOwner(resource = "role", id = "#roleId")
    @CheckOwner(resource = "device", id = "#deviceId")
    @Operation(summary = "查询指定角色的摘要记忆", description = "返回摘要记忆列表，可按设备 ID 筛选")
    public ApiResponse<?> querySummary(@PathVariable Integer roleId,
                                      @PathVariable String deviceId,
                                      @RequestParam(defaultValue = "1") Integer pageNo,
                                      @RequestParam(defaultValue = "10") Integer pageSize) {
        return ApiResponse.success(summaryService.page(deviceId, roleId, pageNo, pageSize));
    }

    @DeleteMapping("/summary/{roleId}/{deviceId}")
    @SaCheckPermission("system:role:memory:summary:api:delete")
    @CheckOwner(resource = "role", id = "#roleId")
    @CheckOwner(resource = "device", id = "#deviceId")
    @AuditLog(module = "记忆管理", operation = "删除摘要记忆")
    @Operation(summary = "批量删除指定角色的摘要记忆", description = "根据角色 ID 和设备 ID 批量删除摘要记忆")
    public ApiResponse<?> deleteSummary(@PathVariable Integer roleId,
                                       @PathVariable String deviceId,
                                       @RequestParam(required = false) Long id) {
        return ApiResponse.success(summaryService.delete(roleId, deviceId, id));
    }
}
