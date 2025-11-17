package com.xiaozhi.service;

import com.xiaozhi.common.exception.UserPasswordNotMatchException;
import com.xiaozhi.common.exception.UsernameNotFoundException;
import com.xiaozhi.common.web.PageFilter;
import com.xiaozhi.entity.SysUser;

import java.util.List;

/**
 * 用户操作
 * 
 * @author Joey
 * 
 */
public interface SysUserService {

    /**
     * 用户名sessionkey
     */
    public static final String USER_SESSIONKEY = "user_sessionkey";

    /**
     * 登录校验
     * 
     * @param username
     * @param password
     * @return
     */
    SysUser login(String username, String password)
            throws UsernameNotFoundException, UserPasswordNotMatchException;

    /**
     * 查询用户信息
     * 
     * @param username
     * @return 用户信息
     */
    SysUser query(String username);

    /**
     * 用户查询列表
     * 
     * @param user
     * @return 用户列表
     */
    List<SysUser> queryUsers(SysUser user, PageFilter pageFilter);

    SysUser selectUserByUserId(Integer userId);

    SysUser selectUserByUsername(String username);

    SysUser selectUserByWxOpenId(String wxOpenId);

    SysUser selectUserByEmail(String email);

    SysUser selectUserByTel(String tel);

    /**
     * 新增用户
     * 
     * @param user
     * @return
     */
    int add(SysUser user);

    /**
     * 修改用户信息
     * 
     * @param user
     * @return
     */
    int update(SysUser user);

    /**
     * 生成验证码
     * 
     */
    SysUser generateCode(SysUser user);

    /**
     * 查询验证码是否有效
     * 
     * @param code
     * @param email
     * @return
     */
    int queryCaptcha(SysUser user);

}