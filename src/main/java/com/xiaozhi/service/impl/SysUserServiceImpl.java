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
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
    private static final String CACHE_NAME = "XiaoZhi:SysUser";
    private static final String dayOfMonthStart = DateUtils.dayOfMonthStart();
    private static final String dayOfMonthEnd = DateUtils.dayOfMonthEnd();

    @Resource
    private SysUserRepository sysUserRepository;

    @Resource
    private SysDeviceRepository sysDeviceRepository;



    @Resource
    private SysRoleRepository sysRoleRepository;

    @Resource
    private SysTemplateRepository sysTemplateRepository;

    @Resource
    private AuthenticationService authenticationService;


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
        SysUser user = sysUserRepository.findByUsername(username).orElse(null);
        // 如果用户名查不到，尝试用邮箱查询
        if (ObjectUtils.isEmpty(user)) {
            user = sysUserRepository.findByEmail(username).orElse(null);
        }
        // 如果邮箱也查不到，尝试用手机号查询
        if (ObjectUtils.isEmpty(user)) {
            user = sysUserRepository.findByTel(username).orElse(null);
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
        return sysUserRepository.findByUsername(username).orElse(null);
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
            Page<SysUser> page = sysUserRepository.findUsersWithStats(
                    user.getUsername(),
                    user.getEmail(),
                    user.getTel(),
                    user.getName(),
                    user.getIsAdmin(),
                    PageRequest.of(pageFilter.getStart() - 1, pageFilter.getLimit(), Sort.by(Sort.Direction.DESC, "createTime"))
            );
            return page.getContent();
        }
        return sysUserRepository.findUsersWithStats(
                user.getUsername(),
                user.getEmail(),
                user.getTel(),
                user.getName(),
                user.getIsAdmin(),
                PageRequest.of(0, 10)
        ).getContent();
    }

    @Override
    @Cacheable(value = CACHE_NAME, key = "#userId", unless = "#result == null")
    public SysUser selectUserByUserId(Integer userId) {
        return sysUserRepository.findById(userId).orElse(null);
    }

    @Override
    @Cacheable(value = CACHE_NAME, key = "'username:' + #username", unless = "#result == null")
    public SysUser selectUserByUsername(String username) {
        return sysUserRepository.findByUsername(username).orElse(null);
    }

    @Override
    @Cacheable(value = CACHE_NAME, key = "'wxOpenId:' + #wxOpenId", unless = "#result == null")
    public SysUser selectUserByWxOpenId(String wxOpenId) {
        return sysUserRepository.findByWxOpenId(wxOpenId).orElse(null);
    }

    @Override
    @Cacheable(value = CACHE_NAME, key = "'email:' + #email", unless = "#result == null")
    public SysUser selectUserByEmail(String email) {
        return sysUserRepository.findByEmail(email).orElse(null);
    }

    @Override
    @Cacheable(value = CACHE_NAME, key = "'tel:' + #tel", unless = "#result == null")
    public SysUser selectUserByTel(String tel) {
        return sysUserRepository.findByTel(tel).orElse(null);
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
        SysUser savedUser = sysUserRepository.save(user.setRoleId(2));
        if (savedUser != null && savedUser.getUserId() != null) {
            int adminUserId = 1;

            // 查询是否有默认角色
            SysRole queryRole = new SysRole();
            queryRole.setIsDefault("1");
            queryRole.setUserId(1);
            List<SysRole> adminRoles = sysRoleRepository.findAll();
            // 过滤出默认角色
            adminRoles = adminRoles.stream()
                    .filter(r -> "1".equals(r.getIsDefault()) && adminUserId == r.getUserId())
                    .toList();

            // 遍历获取默认 roleId，用于创建虚拟设备
            Integer defaultRoleId = null;
            Integer userId = savedUser.getUserId();
            for (SysRole role : adminRoles) {
                role.setUserId(userId);
                sysRoleRepository.save(role);
                // 记录默认角色 ID
                if (defaultRoleId == null && "1".equals(role.getIsDefault())) {
                    defaultRoleId = role.getRoleId();
                }
            }
            // 把管理员所有模板复制给用户一份
            SysTemplate template = new SysTemplate();
            template.setUserId(adminUserId);
            List<SysTemplate> queryTemplate = sysTemplateRepository.findAll();
            queryTemplate = queryTemplate.stream()
                    .filter(t -> adminUserId == t.getUserId())
                    .toList();
            for (SysTemplate temp : queryTemplate) {
                temp.setUserId(userId);
                temp.setTemplateId(null); // 清空 ID 以便保存为新记录
                sysTemplateRepository.save(temp);
            }

            // 自动创建一个默认虚拟设备，用于网页端对话
            // 生成设备 ID：user_ + 用户 ID
            String virtualDeviceId = "user_chat_" + userId;
            // 创建虚拟设备
            SysDevice virtualDevice = new SysDevice();
            virtualDevice.setDeviceId(virtualDeviceId);
            virtualDevice.setDeviceName("网页聊天");
            virtualDevice.setUserId(userId);
            virtualDevice.setType("web");
            virtualDevice.setState(SysDevice.DEVICE_STATE_OFFLINE);
            virtualDevice.setRoleId(defaultRoleId);
            sysDeviceRepository.save(virtualDevice);
        }
        return 1;
    }

    /**
     * 用户信息更改
     * 清除该用户的所有缓存
     *
     * @param user
     * @return
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
        sysUserRepository.save(user);
        return 1;
    }

    /**
     * 生成验证码
     *
     */
    @Override
    public SysUser generateCode(SysUser user) {
        // TODO: 验证码逻辑需要单独处理，这里暂时返回空
        SysUser result = new SysUser();
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
        return sysUserRepository.countValidCaptcha(user.getCode(), email);
    }

}
