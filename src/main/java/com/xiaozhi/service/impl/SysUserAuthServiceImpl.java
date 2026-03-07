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
        return userAuthRepository.selectByOpenIdAndPlatform(openId, platform);
    }

    @Override
    public SysUserAuth getByUserIdAndPlatform(Integer userId, String platform) {
        return userAuthRepository.selectByUserIdAndPlatform(userId, platform);
    }

    @Override
    public boolean save(SysUserAuth userAuth) {
        return userAuthRepository.insert(userAuth) > 0;
    }

    @Override
    public boolean update(SysUserAuth userAuth) {
        return userAuthRepository.update(userAuth) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return userAuthRepository.deleteAuthById(id) > 0;
    }
}
