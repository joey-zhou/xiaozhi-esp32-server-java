package com.xiaozhi.server.web.chat;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.xiaozhi.common.annotation.AuditLog;
import com.xiaozhi.common.model.PageResult;
import com.xiaozhi.common.model.req.ConversationDeleteReq;
import com.xiaozhi.common.model.req.ConversationPageReq;
import com.xiaozhi.common.model.req.ConversationRenameReq;
import com.xiaozhi.common.model.resp.ConversationResp;
import com.xiaozhi.common.web.ApiResponse;
import com.xiaozhi.message.convert.MessageConvert;
import com.xiaozhi.message.service.ConversationService;
import com.xiaozhi.server.web.BaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


/**
 * Web 聊天侧栏的会话列表、重命名与删除。会话一律按当前登录用户过滤，不需要额外的归属校验。
 */
@RestController
@RequestMapping("/api/conversations")
@Tag(name = "Web 聊天会话", description = "网页聊天的会话列表、重命名与删除")
public class ConversationController extends BaseController {

    @Resource
    private ConversationService conversationService;

    @Resource
    private WebChatAppService webChatAppService;

    @Resource
    private MessageConvert messageConvert;

    @GetMapping
    @SaCheckPermission("system:chat")
    @Operation(summary = "查询当前用户的会话列表", description = "按最后对话时间倒序")
    public ApiResponse<PageResult<ConversationResp>> list(@Valid ConversationPageReq req) {
        return ApiResponse.success(conversationService
            .page(StpUtil.getLoginIdAsInt(), req.getRoleId(), req.getPageNo(), req.getPageSize())
            .map(messageConvert::toResp));
    }

    @PatchMapping("/{sessionId}")
    @SaCheckPermission("system:chat")
    @Operation(summary = "重命名会话")
    public ApiResponse<Void> rename(@PathVariable String sessionId, @Valid @RequestBody ConversationRenameReq req) {
        conversationService.rename(StpUtil.getLoginIdAsInt(), sessionId, req.getTitle());
        return ApiResponse.success("重命名成功");
    }

    @DeleteMapping
    @SaCheckPermission("system:chat")
    @AuditLog(module = "Web 聊天", operation = "删除会话")
    @Operation(summary = "删除会话", description = "连同会话的聊天记录与摘要一起删除")
    public ApiResponse<Integer> delete(@Valid @RequestBody ConversationDeleteReq req) {
        return ApiResponse.success(webChatAppService.deleteConversations(StpUtil.getLoginIdAsInt(), req.getSessionIds()));
    }
}
