package com.xiaozhi.controller;

import com.xiaozhi.common.web.ResultMessage;
import com.xiaozhi.common.web.PageFilter;
import com.xiaozhi.communication.common.SessionManager;
import com.xiaozhi.entity.SysMessage;
import com.xiaozhi.service.SysMessageService;
import com.xiaozhi.utils.CmsUtils;
import com.xiaozhi.utils.DtoConverter;

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
    @GetMapping("")
    @ResponseBody
    @Operation(summary = "根据条件查询对话消息", description = "返回对话消息列表")
    public ResultMessage list(SysMessage message, HttpServletRequest request) {
        try {
            PageFilter pageFilter = initPageFilter(request);
            message.setUserId(CmsUtils.getUserId());
            List<SysMessage> messageList = sysMessageService.query(message, pageFilter);

            return ResultMessage.success(DtoConverter.toPageInfo(messageList, DtoConverter::toMessageDTOList));
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return ResultMessage.error();
        }
    }

    /**
     * 删除聊天记录
     *
     * @param messageId 消息ID
     * @return
     */
    @DeleteMapping("/{messageId}")
    @ResponseBody
    @Operation(summary = "删除对话消息", description = "删除指定的对话消息，逻辑删除")
    public ResultMessage delete(@PathVariable Integer messageId) {
        try {
            SysMessage message = new SysMessage();
            message.setMessageId(messageId);
            message.setUserId(CmsUtils.getUserId());

            int rows = sysMessageService.delete(message);
            logger.info("删除聊天记录：{}行。", rows);
            return ResultMessage.success("删除成功，共删除" + rows + "条消息");
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return ResultMessage.error();
        }
    }

    /**
     * 批量删除设备聊天记录（清除设备记忆）
     *
     * @param deviceId 设备ID
     * @return
     */
    @DeleteMapping("")
    @ResponseBody
    @Operation(summary = "批量删除设备消息", description = "清除指定设备的所有聊天记录")
    public ResultMessage batchDelete(@RequestParam(required = true) String deviceId) {
        try {
            SysMessage message = new SysMessage();
            message.setDeviceId(deviceId);
            message.setUserId(CmsUtils.getUserId());

            int rows = sysMessageService.delete(message);
            logger.info("清除设备记忆，删除聊天记录：{}行。", rows);
            return ResultMessage.success("删除成功，共删除" + rows + "条消息");
        } catch (Exception e) {
            logger.error("批量删除消息失败", e);
            return ResultMessage.error("删除失败");
        }
    }

}