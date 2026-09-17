package com.xiaozhi.user;

import com.xiaozhi.authrole.convert.AuthRoleConvert;
import com.xiaozhi.authrole.service.AuthRoleService;
import com.xiaozhi.common.exception.ResourceNotFoundException;
import com.xiaozhi.common.exception.UserPasswordNotMatchException;
import com.xiaozhi.common.exception.UsernameNotFoundException;
import com.xiaozhi.common.model.bo.UserAuthBO;
import com.xiaozhi.common.model.bo.UserBO;
import com.xiaozhi.common.model.req.UserPageReq;
import com.xiaozhi.common.model.req.UserRegisterReq;
import com.xiaozhi.common.model.req.UserResetPasswordReq;
import com.xiaozhi.common.model.req.UserUpdateReq;
import com.xiaozhi.common.model.resp.AuthRoleResp;
import com.xiaozhi.common.model.resp.LoginResp;
import com.xiaozhi.common.model.PageResult;
import com.xiaozhi.common.model.resp.PermissionTreeResp;
import com.xiaozhi.common.model.resp.UserResp;
import com.xiaozhi.common.port.DeviceWriter;
import com.xiaozhi.device.service.DeviceService;
import com.xiaozhi.permission.convert.PermissionConvert;
import com.xiaozhi.permission.service.PermissionService;
import com.xiaozhi.role.service.RoleService;
import com.xiaozhi.security.AuthenticationService;
import com.xiaozhi.template.service.TemplateService;
import com.xiaozhi.user.convert.UserConvert;
import com.xiaozhi.user.service.UserService;
import com.xiaozhi.userauth.service.UserAuthService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

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

    private static final Integer ADMIN_TEMPLATE_OWNER_ID = 1;
    private static final String PLATFORM_WECHAT = "wechat";
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
    private DeviceService deviceService;

    @Resource
    private DeviceWriter deviceWriter;

    @Resource
    private AuthenticationService authenticationService;

    @Resource
    private UserAuthService userAuthService;

    @Resource
    private AuthRoleService authRoleService;

    @Resource
    private PermissionService permissionService;

    @Resource
    private AuthRoleConvert authRoleConvert;

    @Resource
    private PermissionConvert permissionConvert;

    // ==================== 查询 ====================

    public PageResult<UserResp> page(UserPageReq req) {
        UserPageReq r = req == null ? new UserPageReq() : req;
        return userService.page(r.getPageNo(), r.getPageSize(),
            r.getName(), r.getEmail(), r.getTel(), r.getIsAdmin(), r.getAuthRoleId())
            .map(userConvert::toResp);
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
        if (!userService.consumeCaptcha(account, req.getCode())) {
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
        templateService.copyTemplates(ADMIN_TEMPLATE_OWNER_ID, userId);
        deviceWriter.register("user_chat_" + userId, "网页聊天", "web", userId, defaultRoleId);

        return created;
    }

    // ==================== 更新 ====================

    @Transactional
    public UserResp update(Integer userId, UserUpdateReq req) {
        UserBO existing = userService.getBO(userId);
        if (existing == null) {
            throw new ResourceNotFoundException("无此用户，更新失败");
        }
        // updateBO 忽略 password，改密码前拿到的仍是库里那份密文
        String storedPassword = existing.getPassword();
        userConvert.updateBO(req, existing);
        if (StringUtils.hasText(req.getPassword())) {
            // 改密码必须先证明知道原密码：登录态被短暂窃取时，这是账号被接管前的最后一道关
            if (!StringUtils.hasText(req.getOldPassword())
                    || !authenticationService.isPasswordValid(req.getOldPassword(), storedPassword)) {
                throw new UserPasswordNotMatchException();
            }
            existing.setPassword(authenticationService.encryptPassword(req.getPassword()));
        }
        existing.setUserId(userId);
        userService.update(existing);
        return userConvert.toResp(userService.getBO(userId));
    }

    // ==================== 密码重置 ====================

    @Transactional
    public void resetPassword(UserResetPasswordReq req) {
        if (!userService.consumeCaptcha(req.getEmail(), req.getCode())) {
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
        userService.requireEnabled(user);
        if (!authenticationService.isPasswordValid(password, user.getPassword())) {
            throw new UserPasswordNotMatchException();
        }
        return user;
    }

    /**
     * 手机号验证码登录，未注册则自动建号。
     * <p>
     * 建号与初始化默认资源（角色模板、虚拟设备）在同一个事务里，中途失败整体回滚，
     * 不会留下没有角色也没有虚拟设备的半截账号。验证码消费留在调用方，
     * 消费成功但建号失败时验证码已作废，避免同一个码被重试放大成多次建号尝试。
     */
    @Transactional
    public UserBO loginByTel(String tel) {
        UserBO user = userService.getByTel(tel);
        if (user != null) {
            userService.requireEnabled(user);
            return user;
        }

        String suffix = tel.length() >= 4 ? tel.substring(tel.length() - 4) : tel;
        UserBO createUser = new UserBO();
        createUser.setUsername("tel_" + suffix + "_" + System.currentTimeMillis() % 1000);
        createUser.setPassword(authenticationService.encryptPassword(UUID.randomUUID().toString()));
        createUser.setName("用户" + suffix);
        createUser.setTel(tel);
        return createUserWithDefaults(createUser);
    }

    /**
     * 微信登录，未注册则自动建号并落授权记录。
     * <p>
     * 建号与授权记录必须同事务：分成两段各自提交时，授权记录写失败会留下一个建好的用户，
     * 但没有任何 openId 指向它——同一个微信号下次登录只会又建一个新账号，
     * 之前那个连同它的角色、虚拟设备、聊天记录再也回不去，只能人工清库。
     *
     * @param profile 微信返回的原始资料 JSON，由调用方序列化后传入
     */
    @Transactional
    public WechatLogin loginByWechat(String openId, String unionId, String profile) {
        UserAuthBO userAuth = userAuthService.getByOpenIdAndPlatform(openId, PLATFORM_WECHAT);
        if (userAuth != null) {
            UserBO user = userService.getBO(userAuth.getUserId());
            if (user == null) {
                throw new ResourceNotFoundException("用户不存在");
            }
            userService.requireEnabled(user);
            return new WechatLogin(user, false);
        }

        UserBO createUser = new UserBO();
        createUser.setUsername("wx_" + openId.substring(0, Math.min(10, openId.length())));
        createUser.setPassword(authenticationService.encryptPassword(UUID.randomUUID().toString()));
        createUser.setName("微信用户" + System.currentTimeMillis() % 10000);
        UserBO created = createUserWithDefaults(createUser);

        UserAuthBO auth = new UserAuthBO();
        auth.setUserId(created.getUserId());
        auth.setOpenId(openId);
        auth.setUnionId(unionId);
        auth.setPlatform(PLATFORM_WECHAT);
        auth.setProfile(profile);
        userAuthService.create(auth);

        return new WechatLogin(created, true);
    }

    /** 微信登录结果，newUser 用于让前端区分首次注册与再次登录 */
    public record WechatLogin(UserBO user, boolean newUser) {
    }

    /**
     * 启用或禁用账号，state 取 UserBO.STATE_ENABLED / STATE_DISABLED
     */
    @Transactional
    public UserResp updateState(Integer userId, String state) {
        if (!UserBO.STATE_ENABLED.equals(state) && !UserBO.STATE_DISABLED.equals(state)) {
            throw new IllegalArgumentException("账号状态取值不合法");
        }
        UserBO existing = userService.getBO(userId);
        if (existing == null) {
            throw new ResourceNotFoundException("无此用户，更新失败");
        }

        UserBO update = new UserBO();
        update.setUserId(userId);
        update.setState(state);
        userService.update(update);
        return userConvert.toResp(userService.getBO(userId));
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

        AuthRoleResp authRoleResp = authRoleConvert.toResp(authRoleService.getBO(user.getAuthRoleId()));
        List<PermissionTreeResp> permissionResp = permissionService.listTreeByUserId(userId).stream()
            .map(permissionConvert::toTreeResp)
            .toList();

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
