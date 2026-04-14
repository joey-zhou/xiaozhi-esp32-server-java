package com.xiaozhi.message;

import com.xiaozhi.server.web.BaseController;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.xiaozhi.common.annotation.AuditLog;
import com.xiaozhi.common.annotation.CheckOwner;
import com.xiaozhi.common.model.req.MessagePageReq;
import com.xiaozhi.common.web.ApiResponse;
import com.xiaozhi.message.MessageAppService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/message")
@Tag(name = "消息管理", description = "消息相关操作")
public class MessageController extends BaseController {

    @Resource
    private MessageAppService messageAppService;

    @GetMapping("")
    @ResponseBody
    @SaCheckPermission("system:role:memory:chat:api:list")
    @Operation(summary = "根据条件查询对话消息", description = "返回对话消息列表")
    public ApiResponse<?> list(@Valid MessagePageReq req) {
        return ApiResponse.success(messageAppService.page(req, StpUtil.getLoginIdAsInt()));
    }

    @DeleteMapping("/{messageId}")
    @ResponseBody
    @SaCheckPermission("system:role:memory:chat:api:delete")
    @CheckOwner(resource = "message", id = "#messageId")
    @AuditLog(module = "消息管理", operation = "删除消息")
    @Operation(summary = "删除对话消息", description = "删除指定的对话消息，逻辑删除")
    public ApiResponse<?> delete(@PathVariable Integer messageId) {
        messageAppService.delete(messageId);
        return ApiResponse.success("删除成功");
    }

    @DeleteMapping("")
    @ResponseBody
    @SaCheckPermission("system:device:memory:api:delete")
    @CheckOwner(resource = "device", id = "#deviceId")
    @AuditLog(module = "消息管理", operation = "批量删除设备消息")
    @Operation(summary = "批量删除设备消息", description = "清除指定设备的所有聊天记录")
    public ApiResponse<?> batchDelete(@RequestParam String deviceId) {
        int rows = messageAppService.deleteByDeviceId(deviceId);
        logger.info("清除设备记忆，删除聊天记录：{}行。", rows);
        return ApiResponse.success("删除成功，共删除" + rows + "条消息");
    }
}
