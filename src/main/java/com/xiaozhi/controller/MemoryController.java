package com.xiaozhi.controller;

import com.github.pagehelper.PageInfo;
import com.xiaozhi.common.web.PageFilter;
import com.xiaozhi.common.web.ResultMessage;
import com.xiaozhi.entity.SysSummary;
import com.xiaozhi.entity.SysUser;
import com.xiaozhi.service.SysDeviceService;
import com.xiaozhi.service.SysSummaryService;
import com.xiaozhi.utils.CmsUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 记忆管理API
 * 提供长期记忆和摘要记忆的查询和删除功能
 * type(summary)
 * role
 * device
 * id
 * LocalDateTime
 * text
 *
 */

@RestController
@RequestMapping("/api/memory")
@Tag(name = "记忆管理", description = "管理聊天相关的记忆")
public class MemoryController extends BaseController {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Resource
    private SysSummaryService sysSummaryService;
    @Resource
    private SysDeviceService sysDeviceService;

    /**
     * 查询指定角色和设备的摘要记忆
     * GET /api/memory/summary/{roleId}/{deviceId}
     */
    @GetMapping("/summary/{roleId}/{deviceId}")
    @ResponseBody
    @Operation(summary = "查询指定角色的摘要记忆", description = "返回摘要记忆列表，可按设备ID筛选")
    public ResultMessage querySummary(@PathVariable Integer roleId,
            @PathVariable String deviceId,
            HttpServletRequest request) {
        try {
            SysUser user = CmsUtils.getUser();
            var device = sysDeviceService.selectDeviceById(deviceId);
            if (device == null || device.getUserId() == null || user.getUserId() == null) {
                return ResultMessage.error("设备不存在");
            }
            if (!device.getUserId().equals(user.getUserId())) {
                return ResultMessage.error("设备不属于当前用户");
            }
            PageFilter pageFilter = initPageFilter(request);
            SysSummary summary = new SysSummary();
            summary.setRoleId(roleId);
            summary.setDeviceId(deviceId);
            List<SysSummary> summaryList = sysSummaryService.query(summary, pageFilter);
            return ResultMessage.success(new PageInfo<>(summaryList));
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return ResultMessage.error(e.getMessage());
        }
    }

    /**
     * 批量删除指定角色的摘要记忆，如果指定了id，则删除单条记录。
     * DELETE /api/memory/summary/{roleId}/{deviceId}?id=xx
     */
    @DeleteMapping("/summary/{roleId}/{deviceId}")
    @ResponseBody
    @Operation(summary = "批量删除指定角色的摘要记忆", description = "根据角色ID和可选的设备ID批量删除摘要记忆")
    public ResultMessage deleteSummary(@PathVariable Integer roleId,
            @PathVariable String deviceId,
            @RequestParam(required = false) Long id) {
        try {
            SysUser user = CmsUtils.getUser();
            var device = sysDeviceService.selectDeviceById(deviceId);
            if (device == null || device.getUserId() == null || user.getUserId() == null) {
                return ResultMessage.error("设备不存在");
            }
            if (!device.getUserId().equals(user.getUserId())) {
                return ResultMessage.error("设备不属于当前用户");
            }

            // 批量删除摘要记忆
            int result = sysSummaryService.delete(roleId, deviceId, id);
            return ResultMessage.success(result);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return ResultMessage.error(e.getMessage());
        }
    }

}
