package com.xiaozhi.service;

import com.xiaozhi.entity.SysUserAuth;

/**
 * 用户第三方认证服务接口
 *
 * @author Joey
 */
public interface SysUserAuthService {

    /**
     * 根据openid和平台查询认证信息
     *
     * @param openId 第三方平台唯一标识
     * @param platform 平台标识
     * @return 认证信息
     */
    SysUserAuth getByOpenIdAndPlatform(String openId, String platform);

    /**
     * 根据用户ID和平台查询认证信息
     *
     * @param userId 用户ID
     * @param platform 平台标识
     * @return 认证信息
     */
    SysUserAuth getByUserIdAndPlatform(Integer userId, String platform);

    /**
     * 保存认证信息
     *
     * @param userAuth 认证信息
     * @return 是否成功
     */
    boolean save(SysUserAuth userAuth);

    /**
     * 更新认证信息
     *
     * @param userAuth 认证信息
     * @return 是否成功
     */
    boolean update(SysUserAuth userAuth);

    /**
     * 删除认证信息
     *
     * @param id 主键ID
     * @return 是否成功
     */
    boolean deleteById(Long id);
}
