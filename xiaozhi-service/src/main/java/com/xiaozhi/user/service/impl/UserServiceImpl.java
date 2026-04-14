package com.xiaozhi.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaozhi.common.exception.ResourceNotFoundException;
import com.xiaozhi.common.model.bo.UserBO;
import com.xiaozhi.common.model.resp.PageResp;
import com.xiaozhi.common.model.resp.UserResp;
import com.xiaozhi.user.convert.UserConvert;
import com.xiaozhi.user.dal.mysql.dataobject.UserDO;
import com.xiaozhi.user.dal.mysql.mapper.UserMapper;
import com.xiaozhi.user.service.UserService;
import jakarta.annotation.Resource;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.concurrent.ThreadLocalRandom;

@Service
public class UserServiceImpl implements UserService {

    private static final Integer DEFAULT_AUTH_ROLE_ID = 2;

    @Resource
    private UserMapper userMapper;

    @Resource
    private UserConvert userConvert;

    @Override
    public PageResp<UserResp> page(int pageNo, int pageSize, String name, String email,
                                   String tel, String isAdmin, Integer authRoleId) {
        Page<UserResp> page = new Page<>(pageNo, pageSize);
        IPage<UserResp> result = userMapper.selectPageResp(page, name, email, tel, isAdmin, authRoleId);
        return new PageResp<>(
            result.getRecords(),
            result.getTotal(),
            Math.toIntExact(result.getCurrent()),
            Math.toIntExact(result.getSize())
        );
    }

    @Override
    @Cacheable(value = CACHE_NAME, key = "'bo:' + #userId", condition = "#userId != null")
    public UserBO getBO(Integer userId) {
        if (userId == null) {
            return null;
        }
        return userConvert.toBO(userMapper.selectById(userId));
    }

    @Override
    public UserBO getByUsername(String username) {
        if (!StringUtils.hasText(username)) {
            return null;
        }
        return userConvert.toBO(userMapper.selectOne(new LambdaQueryWrapper<UserDO>()
            .eq(UserDO::getUsername, username)
            .last("LIMIT 1")));
    }

    @Override
    public UserBO getByEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return null;
        }
        return userConvert.toBO(userMapper.selectOne(new LambdaQueryWrapper<UserDO>()
            .eq(UserDO::getEmail, email)
            .last("LIMIT 1")));
    }

    @Override
    public UserBO getByTel(String tel) {
        if (!StringUtils.hasText(tel)) {
            return null;
        }
        return userConvert.toBO(userMapper.selectOne(new LambdaQueryWrapper<UserDO>()
            .eq(UserDO::getTel, tel)
            .last("LIMIT 1")));
    }

    @Override
    @Transactional
    public UserBO create(UserBO user) {
        if (user == null || !StringUtils.hasText(user.getUsername()) || !StringUtils.hasText(user.getPassword())) {
            throw new IllegalArgumentException("用户信息不完整");
        }
        if (getByUsername(user.getUsername()) != null) {
            throw new IllegalArgumentException("用户名已存在");
        }
        if (StringUtils.hasText(user.getEmail()) && getByEmail(user.getEmail()) != null) {
            throw new IllegalArgumentException("邮箱已注册");
        }
        if (StringUtils.hasText(user.getTel()) && getByTel(user.getTel()) != null) {
            throw new IllegalArgumentException("手机号已注册");
        }

        UserDO userDO = userConvert.toDO(user);
        userDO.setUserId(null);
        if (!StringUtils.hasText(userDO.getState())) {
            userDO.setState(UserBO.STATE_ENABLED);
        }
        if (!StringUtils.hasText(userDO.getIsAdmin())) {
            userDO.setIsAdmin(UserBO.ADMIN_NO);
        }
        if (userDO.getAuthRoleId() == null) {
            // sys_user.authRoleId 存的是后台权限角色，不是对话 persona。
            userDO.setAuthRoleId(DEFAULT_AUTH_ROLE_ID);
        }
        if (userMapper.insert(userDO) <= 0) {
            throw new IllegalStateException("创建用户失败");
        }

        UserBO result = userConvert.toBO(userMapper.selectById(userDO.getUserId()));
        if (result == null) {
            throw new IllegalStateException("创建用户失败");
        }
        return result;
    }

    @Override
    @Transactional
    @CacheEvict(value = CACHE_NAME, key = "'bo:' + #user.userId", condition = "#user != null && #user.userId != null")
    public void update(UserBO user) {
        if (user == null || user.getUserId() == null) {
            throw new IllegalArgumentException("用户信息不完整");
        }

        UserDO existing = userMapper.selectById(user.getUserId());
        if (existing == null) {
            throw new ResourceNotFoundException("用户不存在");
        }
        if (StringUtils.hasText(user.getUsername()) && !user.getUsername().equals(existing.getUsername())) {
            UserBO usernameOwner = getByUsername(user.getUsername());
            if (usernameOwner != null && !usernameOwner.getUserId().equals(user.getUserId())) {
                throw new IllegalArgumentException("用户名已存在");
            }
        }
        if (StringUtils.hasText(user.getEmail()) && !user.getEmail().equals(existing.getEmail())) {
            UserBO emailOwner = getByEmail(user.getEmail());
            if (emailOwner != null && !emailOwner.getUserId().equals(user.getUserId())) {
                throw new IllegalArgumentException("邮箱已注册");
            }
        }
        if (StringUtils.hasText(user.getTel()) && !user.getTel().equals(existing.getTel())) {
            UserBO telOwner = getByTel(user.getTel());
            if (telOwner != null && !telOwner.getUserId().equals(user.getUserId())) {
                throw new IllegalArgumentException("手机号已注册");
            }
        }

        userConvert.updateDO(user, existing);
        if (userMapper.updateById(existing) <= 0) {
            throw new IllegalStateException("更新用户失败");
        }
    }

    @Override
    public String generateCaptcha(String account) {
        if (!StringUtils.hasText(account)) {
            throw new IllegalArgumentException("账号不能为空");
        }
        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        if (userMapper.insertCode(account, code) <= 0) {
            throw new IllegalStateException("生成验证码失败");
        }
        return code;
    }

    @Override
    public boolean checkCaptcha(String account, String code) {
        if (!StringUtils.hasText(account) || !StringUtils.hasText(code)) {
            return false;
        }
        Integer count = userMapper.countValidCode(account, code);
        return count != null && count > 0;
    }
}
