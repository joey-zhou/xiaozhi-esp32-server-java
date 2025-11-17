package com.xiaozhi.controller;

import com.github.pagehelper.PageInfo;
import com.xiaozhi.common.web.ResultMessage;
import com.xiaozhi.common.web.PageFilter;
import com.xiaozhi.communication.common.SessionManager;
import com.xiaozhi.entity.SysMessage;
import com.xiaozhi.service.SysMessageService;
import com.xiaozhi.utils.CmsUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;


/**
 * @Author: Joey
 * @Date: 2025/2/28 下午2:46
 * @Description:
 */

@RestController
@RequestMapping("/api/message")
@Tag(name = "消息管理", description = "消息相关操作")
public class MessageController extends BaseController {

    @Resource
    private SysMessageService sysMessageService;

    // 后续考虑：未来如果将设备对话与管理台分离部署，则此SessionManager将不可使用。
    @Resource
    private SessionManager sessionManager;
    /**
     * 查询对话
     *
     * @param message
     * @return
     */
    @GetMapping("/query")
    @ResponseBody
    @Operation(summary = "根据条件查询对话消息", description = "返回对话消息列表")
    public ResultMessage query(SysMessage message, HttpServletRequest request) {
        try {
            PageFilter pageFilter = initPageFilter(request);
            message.setUserId(CmsUtils.getUserId());
            List<SysMessage> messageList = sysMessageService.query(message, pageFilter);
            return ResultMessage.success(new PageInfo<>(messageList));
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return ResultMessage.error();
        }
    }

    /**
     * 删除聊天记录
     * 
     * @param message
     * @return
     */
    @PostMapping("/delete")
    @ResponseBody
    @Operation(summary = "删除对话消息", description = "传递messageId只会删除单条消息，传递deviceId会删除该设备所有消息，逻辑删除")
    public ResultMessage delete(SysMessage message) {
        try {
            // 后续考虑：未来如果将设备对话与管理台分离部署，则此SessionManager将不可使用。只能通过设备关机、管理台删消息、再开机的办法实现conversation清空。
            sessionManager.findConversation(message.getDeviceId()).ifPresent(conversation -> {
                conversation.clear();
            });
            message.setUserId(CmsUtils.getUserId());
            int rows = sysMessageService.delete(message);
            logger.info("删除聊天记录：{}行。", rows);
            return ResultMessage.success();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return ResultMessage.error();
        }
    }
    
}