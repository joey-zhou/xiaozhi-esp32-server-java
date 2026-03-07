package com.xiaozhi.service.impl;

import com.xiaozhi.common.exception.UserPasswordNotMatchException;
import com.xiaozhi.common.exception.UsernameNotFoundException;
import com.xiaozhi.common.web.PageFilter;
import com.xiaozhi.entity.SysDevice;
import com.xiaozhi.entity.SysRole;
import com.xiaozhi.entity.SysTemplate;
import com.xiaozhi.entity.SysUser;
import com.xiaozhi.repository.SysDeviceRepository;
import com.xiaozhi.repository.SysRoleRepository;
import com.xiaozhi.repository.SysTemplateRepository;
import com.xiaozhi.repository.SysUserRepository;
import com.xiaozhi.security.AuthenticationService;
import com.xiaozhi.service.SysUserService;
import com.xiaozhi.utils.DateUtils;
import com.xiaozhi.utils.EmailUtils;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 用户操作
 *
 * @author Joey
 */
@Service
public class SysUserServiceImpl extends BaseServiceImpl implements SysUserService {

    private static final Logger logger = LoggerFactory.getLogger(SysUserServiceImpl.class);
    private static final String CACHE_NAME = "XiaoZhi:SysUser";
    private static final String dayOfMonthStart = DateUtils.dayOfMonthStart();
    private static final String dayOfMonthEnd = DateUtils.dayOfMonthEnd();

    @Resource
    private SysUserRepository userRepository;

    @Resource
    private SysRoleRepository roleRepository;

    @Resource
    private SysTemplateRepository templateRepository;

    @Resource
    private SysDeviceRepository deviceRepository;

    @Resource
    private AuthenticationService authenticationService;

    @Resource
    private EmailUtils emailUtils;

    /**
     * 用户登录（支持用户名、邮箱、手机号登录）
     *
     * @param username 用户名/邮箱/手机号
     * @param password 密码
     * @return 用户登录信息
     * @throws UsernameNotFoundException
     * @throws UserPasswordNotMatchException
     */
    @Override
    public SysUser login(String username, String password)
            throws UsernameNotFoundException, UserPasswordNotMatchException {
        SysUser user = userRepository.selectUserByUsername(username);
        if (ObjectUtils.isEmpty(user)) {
            user = userRepository.selectUserByEmail(username);
        }
        if (ObjectUtils.isEmpty(user)) {
            user = userRepository.selectUserByTel(username);
        }
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
        return userRepository.query(username, dayOfMonthStart, dayOfMonthEnd);
    }

    /**
     * 用户列表查询
     *
     * @param user
     * @return 用户列表
     */
    @Override
    public List<SysUser> queryUsers(SysUser user, PageFilter pageFilter) {
        if (pageFilter != null) {
            return userRepository.findUsersWithStats(
                    null, null, null, null, null,
                    org.springframework.data.domain.PageRequest.of(pageFilter.getStart() - 1, pageFilter.getLimit())
            ).getContent();
        }
        return userRepository.findAll();
    }

    @Override
    @Cacheable(value = CACHE_NAME, key = "#userId", unless = "#result == null")
    public SysUser selectUserByUserId(Integer userId) {
        return userRepository.selectUserByUserId(userId);
    }

    @Override
    @Cacheable(value = CACHE_NAME, key = "'username:' + #username", unless = "#result == null")
    public SysUser selectUserByUsername(String username) {
        return userRepository.selectUserByUsername(username);
    }

    @Override
    @Cacheable(value = CACHE_NAME, key = "'wxOpenId:' + #wxOpenId", unless = "#result == null")
    public SysUser selectUserByWxOpenId(String wxOpenId) {
        return userRepository.selectUserByWxOpenId(wxOpenId);
    }

    @Override
    @Cacheable(value = CACHE_NAME, key = "'email:' + #email", unless = "#result == null")
    public SysUser selectUserByEmail(String email) {
        return userRepository.selectUserByEmail(email);
    }

    @Override
    @Cacheable(value = CACHE_NAME, key = "'tel:' + #tel", unless = "#result == null")
    public SysUser selectUserByTel(String tel) {
        return userRepository.selectUserByTel(tel);
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
        int rows = userRepository.add(user.setRoleId(2));
        if (rows > 0) {
            SysUser queryUser = userRepository.selectUserByUsername(user.getUsername());
            int adminUserId = 1;

            SysRole queryRole = new SysRole();
            queryRole.setIsDefault("1");
            queryRole.setUserId(1);
            List<SysRole> adminRoles = roleRepository.query(queryRole);
            Integer defaultRoleId = null;
            Integer userId = queryUser.getUserId();
            for (SysRole role : adminRoles) {
                role.setUserId(userId);
                roleRepository.add(role);
                if (defaultRoleId == null && "1".equals(role.getIsDefault())) {
                    defaultRoleId = role.getRoleId();
                }
            }
            SysTemplate template = new SysTemplate();
            template.setUserId(adminUserId);
            List<SysTemplate> queryTemplate = templateRepository.query(template);
            for (SysTemplate temp : queryTemplate) {
                temp.setUserId(queryUser.getUserId());
                templateRepository.add(temp);
            }

            String virtualDeviceId = "user_chat_" + userId;
            SysDevice virtualDevice = new SysDevice();
            virtualDevice.setDeviceId(virtualDeviceId);
            virtualDevice.setDeviceName("网页聊天");
            virtualDevice.setUserId(userId);
            virtualDevice.setType("web");
            virtualDevice.setState(SysDevice.DEVICE_STATE_OFFLINE);
            virtualDevice.setRoleId(defaultRoleId);
            deviceRepository.add(virtualDevice);
        }
        return rows;
    }

    /**
     * 用户信息更改
     */
    @Override
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = CACHE_NAME, key = "#user.userId", condition = "#user.userId != null"),
        @CacheEvict(value = CACHE_NAME, key = "'username:' + #user.username", condition = "#user.username != null"),
        @CacheEvict(value = CACHE_NAME, key = "'email:' + #user.email", condition = "#user.email != null"),
        @CacheEvict(value = CACHE_NAME, key = "'tel:' + #user.tel", condition = "#user.tel != null"),
        @CacheEvict(value = CACHE_NAME, key = "'wxOpenId:' + #user.wxOpenId", condition = "#user.wxOpenId != null")
    })
    public int update(SysUser user) {
        return userRepository.update(user);
    }

    /**
     * 生成验证码
     */
    @Override
    public SysUser generateCode(SysUser user) {
        SysUser result = new SysUser();
        userRepository.generateCode(user);
        result.setCode(user.getCode());
        return result;
    }

    /**
     * 查询验证码是否有效
     */
    @Override
    public int queryCaptcha(SysUser user) {
        String email = StringUtils.hasText(user.getEmail()) ? user.getEmail() : user.getTel();
        return userRepository.queryCaptcha(user.getCode(), email);
    }
}
