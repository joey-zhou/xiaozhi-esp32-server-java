package com.xiaozhi.service.impl;

import com.github.pagehelper.PageHelper;
import com.xiaozhi.common.exception.UserPasswordNotMatchException;
import com.xiaozhi.common.exception.UsernameNotFoundException;
import com.xiaozhi.dao.UserMapper;
import com.xiaozhi.entity.SysUser;
import com.xiaozhi.security.AuthenticationService;
import com.xiaozhi.service.SysUserService;
import com.xiaozhi.utils.DateUtils;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.util.List;

import javax.annotation.Resource;

/**
 * 用户操作
 * 
 * @author Joey
 * 
 */

@Service
public class SysUserServiceImpl implements SysUserService {

    private static final String dayOfMonthStart = DateUtils.dayOfMonthStart();
    private static final String dayOfMonthEnd = DateUtils.dayOfMonthEnd();

    @Resource
    private UserMapper userMapper;

    @Resource
    private AuthenticationService authenticationService;

    /**
     * 
     * @param username
     * @param password
     * @return 用户登录信息
     * @throws UsernameNotFoundException
     * @throws UserPasswordNotMatchException
     */
    @Override
    public SysUser login(String username, String password)
            throws UsernameNotFoundException, UserPasswordNotMatchException {
        SysUser user = userMapper.selectUserByUsername(username);
        if (ObjectUtils.isEmpty(user)) {
            throw new UsernameNotFoundException();
        } else if (!authenticationService.isPasswordValid(password, user.getPassword())) {
            throw new UserPasswordNotMatchException();
        }
        return user;
    }

    /**
     * 用户信息查询
     * 
     * @param username
     * @return 用户信息
     */
    @Override
    public SysUser query(String username) {
        return userMapper.query(username, dayOfMonthStart, dayOfMonthEnd);
    }

    /**
     * 用户列表查询
     * 
     * @param user
     * @return 用户列表
     */
    @Override
    public List<SysUser> queryUsers(SysUser user) {
        if (user.getStart() != null) {
            PageHelper.startPage(user.getStart(), user.getLimit());
        }
        // 日期是用于后续做统计使用，但功能还未实现，所以这里先将日期设置为本月第一天和最后一天
        // todo
        return userMapper.queryUsers(user,
                !StringUtils.hasText(user.getStartTime()) ? user.getStartTime() : dayOfMonthStart,
                !StringUtils.hasText(user.getEndTime()) ? user.getEndTime() : dayOfMonthEnd);
    }

    @Override
    public SysUser selectUserByUserId(Integer userId) {
        return userMapper.selectUserByUserId(userId);
    }

    @Override
    public SysUser selectUserByUsername(String username) {
        return userMapper.selectUserByUsername(username);
    }

    @Override
    public SysUser selectUserByEmail(String email) {
        return userMapper.selectUserByEmail(email);
    }

    /**
     * 新增用户
     * 
     * @param user
     * @return
     */
    @Override
    @Transactional
    public int add(SysUser user) {
        return userMapper.add(user);
    }

    /**
     * 用户信息更改
     * 
     * @param user
     * @return
     */
    @Override
    @Transactional
    public int update(SysUser user) {
        return userMapper.update(user);
    }

    /**
     * 生成验证码
     * 
     */
    @Override
    public SysUser generateCode(SysUser user) {
        SysUser result = new SysUser();
        userMapper.generateCode(user);
        result.setCode(user.getCode());
        return result;
    }

    /**
     * 查询验证码是否有效
     * 
     * @param code
     * @param email
     * @return
     */
    @Override
    public int queryCaptcha(String code, String email) {
        return userMapper.queryCaptcha(code, email);
    }

}