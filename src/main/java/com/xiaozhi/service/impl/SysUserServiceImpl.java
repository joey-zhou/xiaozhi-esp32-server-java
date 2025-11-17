package com.xiaozhi.service.impl;

import com.github.pagehelper.PageHelper;
import com.xiaozhi.common.exception.UserPasswordNotMatchException;
import com.xiaozhi.common.exception.UsernameNotFoundException;
import com.xiaozhi.common.web.PageFilter;
import com.xiaozhi.dao.ConfigMapper;
import com.xiaozhi.dao.DeviceMapper;
import com.xiaozhi.dao.RoleMapper;
import com.xiaozhi.dao.TemplateMapper;
import com.xiaozhi.dao.UserMapper;
import com.xiaozhi.entity.SysDevice;
import com.xiaozhi.entity.SysRole;
import com.xiaozhi.entity.SysTemplate;
import com.xiaozhi.entity.SysUser;
import com.xiaozhi.security.AuthenticationService;
import com.xiaozhi.service.SysUserService;
import com.xiaozhi.utils.DateUtils;
import com.xiaozhi.utils.EmailUtils;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.util.List;


/**
 * 用户操作
 * 
 * @author Joey
 * 
 */

@Service
public class SysUserServiceImpl extends BaseServiceImpl implements SysUserService {

    private static final Logger logger = LoggerFactory.getLogger(SysUserServiceImpl.class);
    private static final String dayOfMonthStart = DateUtils.dayOfMonthStart();
    private static final String dayOfMonthEnd = DateUtils.dayOfMonthEnd();

    @Resource
    private UserMapper userMapper;

    @Resource
    private ConfigMapper configMapper;

    @Resource
    private RoleMapper roleMapper;

    @Resource
    private TemplateMapper templateMapper;

    @Resource
    private DeviceMapper deviceMapper;

    @Resource
    private AuthenticationService authenticationService;

    @Resource
    private EmailUtils emailUtils;

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
    public List<SysUser> queryUsers(SysUser user, PageFilter pageFilter) {
        if(pageFilter != null){
            PageHelper.startPage(pageFilter.getStart(), pageFilter.getLimit());
        }
        return userMapper.queryUsers(user);
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
    public SysUser selectUserByWxOpenId(String wxOpenId) {
        return userMapper.selectUserByWxOpenId(wxOpenId);
    }

    @Override
    public SysUser selectUserByEmail(String email) {
        return userMapper.selectUserByEmail(email);
    }

    @Override
    public SysUser selectUserByTel(String tel) {
        return userMapper.selectUserByTel(tel);
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
        // 用户注册默认应该是普通用户，roleId 目前写死为 2
        int rows = userMapper.add(user.setRoleId(2));
        if (rows > 0) {
            SysUser queryUser = userMapper.selectUserByUsername(user.getUsername());
            int adminUserId = 1;

            // 查询是否有默认角色
            SysRole queryRole = new SysRole();

            queryRole.setIsDefault("1");
            queryRole.setUserId(1);
            List<SysRole> adminRoles = roleMapper.query(queryRole);
            // 遍历获取默认roleId
            Integer defaultRoleId = null;
            Integer userId = queryUser.getUserId();
            for (SysRole role : adminRoles) {
                role.setUserId(userId);
                roleMapper.add(role);
                // 记录默认角色ID，用于创建虚拟设备
                if (defaultRoleId == null && "1".equals(role.getIsDefault())) {
                    defaultRoleId = role.getRoleId();
                }
            }
            // 把管理员所有模板复制给用户一份
            SysTemplate template = new SysTemplate();
            template.setUserId(adminUserId);
            List<SysTemplate> queryTemplate = templateMapper.query(template);
            for (SysTemplate temp : queryTemplate) {
                temp.setUserId(queryUser.getUserId());
                templateMapper.add(temp);
            }

            // 自动创建一个默认虚拟设备，用于网页端对话
            // 生成设备ID：user_ + 用户ID
            String virtualDeviceId = "user_chat_" + userId;
            // 创建虚拟设备
            SysDevice virtualDevice = new SysDevice();
            virtualDevice.setDeviceId(virtualDeviceId);
            virtualDevice.setDeviceName("网页聊天");
            virtualDevice.setUserId(userId);
            virtualDevice.setType("web");
            virtualDevice.setState(SysDevice.DEVICE_STATE_OFFLINE);
            virtualDevice.setRoleId(defaultRoleId);
            deviceMapper.add(virtualDevice);
        }
        return rows;
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
    public int queryCaptcha(SysUser user) {
        String email = StringUtils.hasText(user.getEmail()) ? user.getEmail() : user.getTel();
        return userMapper.queryCaptcha(user.getCode(), email);
    }

}