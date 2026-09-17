package com.xiaozhi.memory;

import com.xiaozhi.server.web.BaseController;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.xiaozhi.common.annotation.AuditLog;
import com.xiaozhi.common.annotation.CheckOwner;
import com.xiaozhi.common.model.PageResult;
import com.xiaozhi.common.model.resp.SummaryResp;
import com.xiaozhi.common.web.ApiResponse;
import com.xiaozhi.summary.convert.SummaryConvert;
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
@Tag(name = "记忆管理", description = "管理设备的对话摘要")
public class MemoryController extends BaseController {

    private static final int MAX_PAGE_SIZE = 1000;

    @Resource
    private SummaryService summaryService;

    @Resource
    private SummaryConvert summaryConvert;

    @GetMapping("/summary")
    @SaCheckPermission("system:role:memory:summary:api:list")
    @CheckOwner(resource = "device", id = "#deviceId")
    @CheckOwner(resource = "role", id = "#roleId")
    @Operation(summary = "查询对话摘要", description = "不传设备时查当前用户全部设备，不传角色时不限角色")
    public ApiResponse<PageResult<SummaryResp>> querySummary(@RequestParam(required = false) String deviceId,
                                                             @RequestParam(required = false) Integer roleId,
                                                             @RequestParam(defaultValue = "1") Integer pageNo,
                                                             @RequestParam(defaultValue = "10") Integer pageSize) {
        pageSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        // 指定设备时归属已由 @CheckOwner 校验；不指定时按当前用户名下的设备查
        return ApiResponse.success(summaryService.page(deviceId, StpUtil.getLoginIdAsInt(), roleId, pageNo, pageSize)
            .map(summaryConvert::toResp));
    }

    @DeleteMapping("/summary/{roleId}/{deviceId}")
    @SaCheckPermission("system:role:memory:summary:api:delete")
    @CheckOwner(resource = "role", id = "#roleId")
    @CheckOwner(resource = "device", id = "#deviceId")
    @AuditLog(module = "记忆管理", operation = "删除摘要记忆")
    @Operation(summary = "批量删除指定角色的摘要记忆", description = "根据角色 ID 和设备 ID 批量删除摘要记忆")
    public ApiResponse<Integer> deleteSummary(@PathVariable Integer roleId,
                                       @PathVariable String deviceId,
                                       @RequestParam(required = false) Long id) {
        return ApiResponse.success(summaryService.delete(roleId, deviceId, id));
    }
}
