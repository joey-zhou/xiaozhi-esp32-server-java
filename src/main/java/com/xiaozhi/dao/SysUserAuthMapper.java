package com.xiaozhi.dao;

import com.xiaozhi.entity.SysUserAuth;
import org.apache.ibatis.annotations.Param;

/**
 * 用户第三方认证信息 数据层
 *
 * @author Joey
 */
public interface SysUserAuthMapper {

    /**
     * 根据openid和平台查询认证信息
     *
     * @param openId 第三方平台唯一标识
     * @param platform 平台标识
     * @return 认证信息
     */
    SysUserAuth selectByOpenIdAndPlatform(@Param("openId") String openId, @Param("platform") String platform);

    /**
     * 根据用户ID和平台查询认证信息
     *
     * @param userId 用户ID
     * @param platform 平台标识
     * @return 认证信息
     */
    SysUserAuth selectByUserIdAndPlatform(@Param("userId") Integer userId, @Param("platform") String platform);

    /**
     * 插入认证信息
     *
     * @param userAuth 认证信息
     * @return 影响行数
     */
    int insert(SysUserAuth userAuth);

    /**
     * 更新认证信息
     *
     * @param userAuth 认证信息
     * @return 影响行数
     */
    int update(SysUserAuth userAuth);

    /**
     * 删除认证信息
     *
     * @param id 主键ID
     * @return 影响行数
     */
    int deleteById(@Param("id") Long id);
}
