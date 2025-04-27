package com.xiaozhi.controller;

import com.github.pagehelper.PageInfo;
import com.xiaozhi.common.web.AjaxResult;
import com.xiaozhi.entity.*;
import com.xiaozhi.service.SysDeviceService;
import com.xiaozhi.service.SysMessageService;
import com.xiaozhi.utils.CmsUtils;

import org.bytedeco.librealsense.device;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;

import java.util.List;

import javax.annotation.Resource;

import reactor.core.publisher.Mono;
import org.springframework.web.bind.annotation.GetMapping;


/**
 * @Author: Joey
 * @Date: 2025/2/28 下午2:46
 * @Description:
 */

@RestController
@RequestMapping("/api/message")
public class MessageController {
    private static final Logger logger = LoggerFactory.getLogger(MessageController.class);

    @Resource
    private SysMessageService messageService;

    @Resource
    private SysDeviceService deviceService;

    /**
     * 查询对话
     *
     * @param message
     * @return
     */
    @GetMapping("/query")
    public Mono<AjaxResult> query(SysMessage message, ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
            try {
                // 从请求属性中获取用户信息
                SysUser user = exchange.getAttribute(CmsUtils.USER_ATTRIBUTE_KEY);
                if (user != null) {
                    message.setUserId(user.getUserId());
                }
                
                List<SysMessage> messageList = messageService.query(message);
                AjaxResult result = AjaxResult.success();
                result.put("data", new PageInfo<>(messageList));
                return result;
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
                return AjaxResult.error();
            }
        });
    }

    /**
     * 删除聊天记录
     * 
     * @param message
     * @return
     */
    @PostMapping("/delete")
    public Mono<AjaxResult> delete(SysMessage message, ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
            try {
                // 从请求属性中获取用户信息
                SysUser user = exchange.getAttribute(CmsUtils.USER_ATTRIBUTE_KEY);
                if (user != null) {
                    message.setUserId(user.getUserId());
                }
                messageService.delete(message);
                return AjaxResult.success();
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
                return AjaxResult.error();
            }
        });
    }
    
}