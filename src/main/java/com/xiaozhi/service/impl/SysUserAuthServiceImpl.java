package com.xiaozhi.service.impl;

import com.xiaozhi.dao.SysUserAuthMapper;
import com.xiaozhi.entity.SysUserAuth;
import com.xiaozhi.service.SysUserAuthService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/**
 * 用户第三方认证服务实现
 *
 * @author Joey
 */
@Service
public class SysUserAuthServiceImpl implements SysUserAuthService {

    @Resource
    private SysUserAuthMapper userAuthMapper;

    @Override
    public SysUserAuth getByOpenIdAndPlatform(String openId, String platform) {
        return userAuthMapper.selectByOpenIdAndPlatform(openId, platform);
    }

    @Override
    public SysUserAuth getByUserIdAndPlatform(Integer userId, String platform) {
        return userAuthMapper.selectByUserIdAndPlatform(userId, platform);
    }

    @Override
    public boolean save(SysUserAuth userAuth) {
        return userAuthMapper.insert(userAuth) > 0;
    }

    @Override
    public boolean update(SysUserAuth userAuth) {
        return userAuthMapper.update(userAuth) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return userAuthMapper.deleteById(id) > 0;
    }
}
