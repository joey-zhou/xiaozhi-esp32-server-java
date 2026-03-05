package com.xiaozhi.service.impl;

import com.xiaozhi.entity.SysUserAuth;
import com.xiaozhi.repository.SysUserAuthRepository;
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
    private SysUserAuthRepository userAuthRepository;

    @Override
    public SysUserAuth getByOpenIdAndPlatform(String openId, String platform) {
        return userAuthRepository.findByOpenIdAndPlatform(openId, platform);
    }

    @Override
    public SysUserAuth getByUserIdAndPlatform(Integer userId, String platform) {
        return userAuthRepository.findByUserIdAndPlatform(userId, platform);
    }

    @Override
    public boolean save(SysUserAuth userAuth) {
        userAuthRepository.save(userAuth);
        return true;
    }

    @Override
    public boolean update(SysUserAuth userAuth) {
        userAuthRepository.save(userAuth);
        return true;
    }

    @Override
    public boolean deleteById(Long id) {
        userAuthRepository.deleteById(id);
        return true;
    }
}
