package com.xiaozhi.controller;

import java.util.List;

import javax.annotation.Resource;

import com.github.pagehelper.PageInfo;
import com.xiaozhi.common.web.AjaxResult;
import com.xiaozhi.entity.SysConfig;
import com.xiaozhi.entity.SysUser;
import com.xiaozhi.service.SysConfigService;
import com.xiaozhi.utils.CmsUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * 配置管理
 * 
 * @author Joey
 * 
 */

@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private static final Logger logger = LoggerFactory.getLogger(ConfigController.class);

    @Resource
    private SysConfigService configService;

    /**
     * 配置查询
     * 
     * @param config
     * @return configList
     */
    @GetMapping("/query")
    public Mono<AjaxResult> query(SysConfig config, ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
            try {
                // 从请求属性中获取用户信息
                SysUser user = exchange.getAttribute(CmsUtils.USER_ATTRIBUTE_KEY);
                if (user != null) {
                    config.setUserId(user.getUserId());
                }
                
                List<SysConfig> configList = configService.query(config);
                AjaxResult result = AjaxResult.success();
                result.put("data", new PageInfo<>(configList));
                return result;
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
                return AjaxResult.error();
            }
        });
    }

    /**
     * 配置信息更新
     * 
     * @param config
     * @return
     */
    @PostMapping("/update")
    public Mono<AjaxResult> update(SysConfig config, ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
            try {
                // 从请求属性中获取用户信息
                SysUser user = exchange.getAttribute(CmsUtils.USER_ATTRIBUTE_KEY);
                if (user != null) {
                    config.setUserId(user.getUserId());
                }
                configService.update(config);
                return AjaxResult.success();
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
                return AjaxResult.error();
            }
        });
    }

    /**
     * 添加配置
     * 
     * @param config
     */
    @PostMapping("/add")
    public Mono<AjaxResult> add(SysConfig config, ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
            try {
                // 从请求属性中获取用户信息
                SysUser user = exchange.getAttribute(CmsUtils.USER_ATTRIBUTE_KEY);
                if (user != null) {
                    config.setUserId(user.getUserId());
                }
                
                configService.add(config);
                return AjaxResult.success();
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
                return AjaxResult.error();
            }
        });
    }
}