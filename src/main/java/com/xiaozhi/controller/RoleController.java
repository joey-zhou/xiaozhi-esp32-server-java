package com.xiaozhi.controller;

import java.util.List;

import javax.annotation.Resource;

import com.github.pagehelper.PageInfo;
import com.xiaozhi.common.web.AjaxResult;
import com.xiaozhi.entity.SysConfig;
import com.xiaozhi.entity.SysRole;
import com.xiaozhi.entity.SysUser;
import com.xiaozhi.service.SysConfigService;
import com.xiaozhi.service.SysRoleService;
import com.xiaozhi.utils.CmsUtils;
import com.xiaozhi.websocket.tts.factory.TtsServiceFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * 角色管理
 * 
 * @author Joey
 * 
 */

@RestController
@RequestMapping("/api/role")
public class RoleController {

    private static final Logger logger = LoggerFactory.getLogger(RoleController.class);

    @Resource
    private SysRoleService roleService;

    @Resource
    private TtsServiceFactory ttsService;

    @Resource
    private SysConfigService configService;

    /**
     * 角色查询
     * 
     * @param role
     * @return roleList
     */
    @GetMapping("/query")
    public Mono<AjaxResult> query(SysRole role, ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
            try {
                // 从请求属性中获取用户信息
                SysUser user = exchange.getAttribute(CmsUtils.USER_ATTRIBUTE_KEY);
                if (user != null) {
                    role.setUserId(user.getUserId());
                }

                List<SysRole> roleList = roleService.query(role);
                AjaxResult result = AjaxResult.success();
                result.put("data", new PageInfo<>(roleList));
                return result;
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
                return AjaxResult.error();
            }
        });
    }

    /**
     * 角色信息更新
     * 
     * @param role
     * @return
     */
    @PostMapping("/update")
    public Mono<AjaxResult> update(SysRole role, ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
            try {
                roleService.update(role);
                return AjaxResult.success();
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
                return AjaxResult.error();
            }
        });
    }

    /**
     * 添加角色
     * 
     * @param role
     */
    @PostMapping("/add")
    public Mono<AjaxResult> add(SysRole role, ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
            try {
                // 从请求属性中获取用户信息
                SysUser user = exchange.getAttribute(CmsUtils.USER_ATTRIBUTE_KEY);
                if (user != null) {
                    role.setUserId(user.getUserId());
                }

                roleService.add(role);
                return AjaxResult.success();
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
                return AjaxResult.error();
            }
        });
    }

    @GetMapping("/testVoice")
    public Mono<AjaxResult> testAudio(String message, String provider, Integer ttsId, String voiceName,
            ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
            SysConfig config = null;
            try {
                if (!provider.equals("edge")) {
                    config = configService.selectConfigById(ttsId);
                }
                String audioFilePath = ttsService.getTtsService(config, voiceName).textToSpeech(message);
                AjaxResult result = AjaxResult.success();
                result.put("data", audioFilePath);
                return result;
            } catch (IndexOutOfBoundsException e) {
                return AjaxResult.error("请先到语音合成配置页面配置对应Key");
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
                return AjaxResult.error();
            }
        });
    }
}