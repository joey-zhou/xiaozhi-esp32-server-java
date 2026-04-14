package com.xiaozhi.user;

import com.xiaozhi.authrole.service.AuthRoleService;
import com.xiaozhi.common.exception.ResourceNotFoundException;
import com.xiaozhi.common.exception.UserPasswordNotMatchException;
import com.xiaozhi.common.exception.UsernameNotFoundException;
import com.xiaozhi.common.model.bo.TemplateBO;
import com.xiaozhi.common.model.bo.UserBO;
import com.xiaozhi.common.model.req.UserPageReq;
import com.xiaozhi.common.model.req.UserRegisterReq;
import com.xiaozhi.common.model.req.UserResetPasswordReq;
import com.xiaozhi.common.model.req.UserUpdateReq;
import com.xiaozhi.common.model.resp.AuthRoleResp;
import com.xiaozhi.common.model.resp.LoginResp;
import com.xiaozhi.common.model.resp.PageResp;
import com.xiaozhi.common.model.resp.PermissionTreeResp;
import com.xiaozhi.common.model.resp.UserResp;
import com.xiaozhi.device.domain.Device;
import com.xiaozhi.device.domain.repository.DeviceRepository;
import com.xiaozhi.device.service.DeviceService;
import com.xiaozhi.permission.service.PermissionService;
import com.xiaozhi.role.service.RoleService;
import com.xiaozhi.security.AuthenticationService;
import com.xiaozhi.template.domain.Template;
import com.xiaozhi.template.domain.repository.TemplateRepository;
import com.xiaozhi.template.service.TemplateService;
import com.xiaozhi.user.convert.UserConvert;
import com.xiaozhi.user.service.UserService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户领域应用服务。
 * <p>
 * 职责：编排 Controller → Domain Service 之间的流程，包括：
 * <ul>
 *   <li>Req/Resp ↔ BO 转换</li>
 *   <li>跨领域编排（注册时：复制角色模板、创建虚拟设备）</li>
 *   <li>认证协调（登录、密码加密、验证码校验）</li>
 *   <li>登录响应组装（Token、权限、角色信息）</li>
 * </ul>
 */
@Service
public class UserAppService {

    private static final Integer DEFAULT_AUTH_ROLE_ID = 2;
    private static final Integer ADMIN_TEMPLATE_OWNER_ID = 1;
    private static final int TOKEN_EXPIRE_SECONDS = 2592000;

    @Resource
    private UserService userService;

    @Resource
    private UserConvert userConvert;

    @Resource
    private RoleService roleService;

    @Resource
    private TemplateService templateService;

    @Resource
    private TemplateRepository templateRepository;

    @Resource
    private DeviceService deviceService;

    @Resource
    private DeviceRepository deviceRepository;

    @Resource
    private AuthenticationService authenticationService;

    @Resource
    private AuthRoleService authRoleService;

    @Resource
    private PermissionService permissionService;

    // ==================== 查询 ====================

    public PageResp<UserResp> page(UserPageReq req) {
        UserPageReq r = req == null ? new UserPageReq() : req;
        return userService.page(r.getPageNo(), r.getPageSize(),
            r.getName(), r.getEmail(), r.getTel(), r.getIsAdmin(), r.getAuthRoleId());
    }

    public UserResp get(Integer userId) {
        return userConvert.toResp(userService.getBO(userId));
    }

    // ==================== 注册 ====================

    @Transactional
    public UserResp register(UserRegisterReq req) {
        String account = StringUtils.hasText(req.getEmail()) ? req.getEmail() : req.getTel();
        if (!StringUtils.hasText(account)) {
            throw new IllegalArgumentException("邮箱或手机号至少填写一个");
        }
        if (!userService.checkCaptcha(account, req.getCode())) {
            throw new IllegalArgumentException("无效验证码");
        }

        UserBO user = userConvert.toBO(req);
        user.setPassword(authenticationService.encryptPassword(req.getPassword()));
        UserBO created = createUserWithDefaults(user);
        return userConvert.toResp(created);
    }

    /**
     * 创建用户并初始化默认资源（角色、模板、虚拟设备）。
     * 供注册、手机号登录自动注册、微信登录自动注册共用。
     */
    @Transactional
    public UserBO createUserWithDefaults(UserBO user) {
        UserBO created = userService.create(user);
        Integer userId = created.getUserId();

        Integer defaultRoleId = roleService.copyDefaultRole(ADMIN_TEMPLATE_OWNER_ID, userId);
        List<TemplateBO> templates = templateService.listBO(ADMIN_TEMPLATE_OWNER_ID, null, null);
        for (TemplateBO template : templates) {
            templateRepository.save(Template.newTemplate(userId, template));
        }

        Device virtualDevice = Device.newDevice(
                "user_chat_" + userId, "网页聊天", "web", userId, defaultRoleId);
        deviceRepository.save(virtualDevice);

        return created;
    }

    // ==================== 更新 ====================

    @Transactional
    public UserResp update(Integer userId, UserUpdateReq req) {
        UserBO existing = userService.getBO(userId);
        if (existing == null) {
            throw new ResourceNotFoundException("无此用户，更新失败");
        }
        userConvert.updateBO(req, existing);
        if (StringUtils.hasText(req.getPassword())) {
            existing.setPassword(authenticationService.encryptPassword(req.getPassword()));
        }
        existing.setUserId(userId);
        userService.update(existing);
        return userConvert.toResp(userService.getBO(userId));
    }

    // ==================== 密码重置 ====================

    @Transactional
    public void resetPassword(UserResetPasswordReq req) {
        if (!userService.checkCaptcha(req.getEmail(), req.getCode())) {
            throw new IllegalArgumentException("验证码错误或已过期");
        }
        UserBO user = userService.getByEmail(req.getEmail());
        if (user == null) {
            throw new IllegalArgumentException("该邮箱未注册");
        }

        UserBO updateUser = new UserBO();
        updateUser.setUserId(user.getUserId());
        updateUser.setPassword(authenticationService.encryptPassword(req.getPassword()));
        userService.update(updateUser);
    }

    // ==================== 登录 ====================

    public UserBO login(String username, String password) {
        UserBO user = userService.getByUsername(username);
        if (user == null) {
            user = userService.getByEmail(username);
        }
        if (user == null) {
            user = userService.getByTel(username);
        }
        if (user == null) {
            throw new UsernameNotFoundException();
        }
        if (!authenticationService.isPasswordValid(password, user.getPassword())) {
            throw new UserPasswordNotMatchException();
        }
        return user;
    }

    public void recordLoginInfo(UserBO user, String loginIp) {
        user.setLoginTime(LocalDateTime.now());
        user.setLoginIp(loginIp);
        userService.update(user);
    }

    public LoginResp buildLoginResp(Integer userId, String token, boolean isNewUser) {
        UserResp user = get(userId);
        if (user == null) {
            return null;
        }

        AuthRoleResp authRoleResp = authRoleService.get(user.getAuthRoleId());
        List<PermissionTreeResp> permissionResp = permissionService.listTreeByUserId(userId);

        return LoginResp.builder()
            .token(token)
            .refreshToken(token)
            .expiresIn(TOKEN_EXPIRE_SECONDS)
            .userId(userId)
            .isNewUser(isNewUser)
            .user(user)
            .authRole(authRoleResp)
            .permissions(permissionResp)
            .build();
    }

    public int getTokenExpireSeconds() {
        return TOKEN_EXPIRE_SECONDS;
    }
}
