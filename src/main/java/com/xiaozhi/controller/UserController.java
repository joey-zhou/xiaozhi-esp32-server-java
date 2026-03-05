package com.xiaozhi.controller;

import com.github.pagehelper.PageInfo;
import com.xiaozhi.common.web.PageFilter;
import com.xiaozhi.common.web.ResultMessage;
import com.xiaozhi.dto.param.*;
import com.xiaozhi.dto.response.LoginResponseDTO;
import com.xiaozhi.entity.SysAuthRole;
import com.xiaozhi.entity.SysPermission;
import com.xiaozhi.entity.SysUser;
import com.xiaozhi.entity.SysUserAuth;
import com.xiaozhi.security.AuthenticationService;
import com.xiaozhi.security.annotation.AnonymousAccess;
import com.xiaozhi.security.jwt.JwtTokenProvider;
import com.xiaozhi.security.service.JwtAuthenticationService;
import com.xiaozhi.service.*;
import com.xiaozhi.utils.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户信息
 *
 * @author: Joey
 *
 */
@RestController
@RequestMapping("/api/user")
@Tag(name = "用户管理", description = "用户相关操作")
public class UserController extends BaseController {

    @Resource
    private SysUserService userService;

    @Resource
    private JwtAuthenticationService jwtAuthenticationService;

    @Resource
    private JwtTokenProvider jwtTokenProvider;

    @Resource
    private AuthenticationService passwordService;

    @Resource
    private WxLoginService wxLoginService;

    @Resource
    private SysUserAuthService userAuthService;

    @Resource
    private SysAuthRoleService authRoleService;

    @Resource
    private SysPermissionService permissionService;

    @Resource
    private SmsUtils smsUtils;

    @Resource
    private EmailUtils emailUtils;

    @Resource
    private CaptchaUtils captchaUtils;

    /**
     * 检查 Token 是否有效，用于前端页面刷新时验证登录状态
     */
    @GetMapping("/check-token")
    @Operation(summary = "检查 Token 有效性", description = "验证当前 Token 是否有效，有效则返回用户信息")
    public ResultMessage checkToken() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResultMessage.error(401, "未登录");
            }
            
            Object principal = authentication.getPrincipal();
            if (!(principal instanceof UserDetails)) {
                return ResultMessage.error(401, "认证信息无效");
            }
            
            String username = ((UserDetails) principal).getUsername();
            SysUser user = userService.selectUserByUsername(username);

            if (user == null) {
                return ResultMessage.error(401, "用户不存在");
            }

            // 获取角色和权限
            SysAuthRole role = authRoleService.selectById(user.getRoleId());
            List<SysPermission> permissions = permissionService.selectByUserId(user.getUserId());
            List<SysPermission> permissionTree = permissionService.buildPermissionTree(permissions);

            // 返回用户信息
            LoginResponseDTO response = LoginResponseDTO.builder()
                .userId(user.getUserId())
                .user(DtoConverter.toUserDTO(user))
                .role(DtoConverter.toRoleDTO(role))
                .permissions(DtoConverter.toPermissionDTOList(permissionTree))
                .build();

            return ResultMessage.success(response);
        } catch (Exception e) {
            logger.error("检查 Token 失败", e);
            return ResultMessage.error(401, "Token 无效或已过期");
        }
    }

    /**
     * 刷新 Token，延长登录有效期
     */
    @PostMapping("/refresh-token")
    @Operation(summary = "刷新 Token", description = "刷新 Token 有效期，返回新的 Token")
    public ResultMessage refreshToken() {
        try {
            // 获取当前登录用户
            Integer userId = AuthUtils.getCurrentUserId();
            if (userId == null) {
                return ResultMessage.error(401, "未登录");
            }
            
            SysUser user = userService.selectUserByUserId(userId);

            if (user == null) {
                return ResultMessage.error(401, "用户不存在");
            }

            // 刷新 Token
            String token = SecurityContextHolder.getContext().getAuthentication().getCredentials().toString();
            String newToken = jwtTokenProvider.refreshToken(token);

            // 获取角色和权限
            SysAuthRole role = authRoleService.selectById(user.getRoleId());
            List<SysPermission> permissions = permissionService.selectByUserId(user.getUserId());
            List<SysPermission> permissionTree = permissionService.buildPermissionTree(permissions);

            // 返回新 Token 和用户信息
            LoginResponseDTO response = LoginResponseDTO.builder()
                .token(newToken)
                .refreshToken(newToken)
                .expiresIn(jwtTokenProvider.getExpirationTime(newToken).intValue())
                .userId(user.getUserId())
                .user(DtoConverter.toUserDTO(user))
                .role(DtoConverter.toRoleDTO(role))
                .permissions(DtoConverter.toPermissionDTOList(permissionTree))
                .build();

            return ResultMessage.success(response);
        } catch (Exception e) {
            logger.error("刷新 Token 失败", e);
            return ResultMessage.error(401, "Token 刷新失败，请重新登录");
        }
    }

    /**
     * 用户名密码登录
     */
    @AnonymousAccess
    @PostMapping("/login")
    @Operation(summary = "用户名密码登录", description = "使用用户名/邮箱/手机号和密码进行登录，返回 token、用户信息、角色和权限")
    public ResultMessage login(@Valid @RequestBody LoginParam param, HttpServletRequest request) {
        try {
            // 使用 Spring Security 认证
            String token = jwtAuthenticationService.login(param.getUsername(), param.getPassword());
            
            // 查询用户信息
            SysUser user = userService.selectUserByUsername(param.getUsername());
            if (user == null) {
                user = userService.selectUserByEmail(param.getUsername());
            }
            if (user == null) {
                user = userService.selectUserByTel(param.getUsername());
            }

            if (user == null) {
                return ResultMessage.error("用户不存在");
            }

            // 记录登录时间和 IP
            user.setLoginTime(new java.util.Date());
            user.setLoginIp(CmsUtils.getClientIp(request));
            userService.update(user);

            // 获取角色和权限
            SysAuthRole role = authRoleService.selectById(user.getRoleId());
            List<SysPermission> permissions = permissionService.selectByUserId(user.getUserId());
            List<SysPermission> permissionTree = permissionService.buildPermissionTree(permissions);

            // 转换为 DTO
            LoginResponseDTO response = LoginResponseDTO.builder()
                .token(token)
                .refreshToken(token)
                .expiresIn(jwtTokenProvider.getExpirationTime(token).intValue())
                .userId(user.getUserId())
                .user(DtoConverter.toUserDTO(user))
                .role(DtoConverter.toRoleDTO(role))
                .permissions(DtoConverter.toPermissionDTOList(permissionTree))
                .build();

            return ResultMessage.success(response);
        } catch (Exception e) {
            logger.error("登录失败", e);
            String msg = e.getMessage();
            if (msg != null && msg.contains("用户不存在")) {
                return ResultMessage.error("用户不存在");
            }
            if (msg != null && msg.contains("密码错误")) {
                return ResultMessage.error("密码错误");
            }
            return ResultMessage.error("操作失败");
        }
    }

    /**
     * 手机号验证码登录 - 自动注册模式
     */
    @AnonymousAccess
    @PostMapping("/tel-login")
    @Operation(summary = "手机号验证码登录", description = "使用手机号和验证码登录，未注册自动注册")
    public ResultMessage telLogin(@Valid @RequestBody TelLoginParam param, HttpServletRequest request) {
        try {
            // 验证验证码
            SysUser codeUser = new SysUser();
            codeUser.setTel(param.getTel());
            codeUser.setCode(param.getCode());
            int row = userService.queryCaptcha(codeUser);
            if (row < 1) {
                return ResultMessage.error("验证码错误或已过期");
            }

            // 查询用户，不存在则自动注册
            SysUser user = userService.selectUserByTel(param.getTel());
            if (user == null) {
                user = autoRegisterTelUser(param.getTel());
            }

            // 记录登录时间和 IP
            user.setLoginTime(new java.util.Date());
            user.setLoginIp(CmsUtils.getClientIp(request));
            userService.update(user);

            // 生成 JWT Token
            org.springframework.security.core.authority.SimpleGrantedAuthority authority =
                new org.springframework.security.core.authority.SimpleGrantedAuthority(
                    "ROLE_" + (user.getRoleId() != null ? user.getRoleId() : 2)
                );
            UserDetails userDetails = new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                java.util.Collections.singletonList(authority)
            );
            String token = jwtTokenProvider.generateToken(userDetails, user.getUserId());

            // 获取角色和权限
            SysAuthRole role = authRoleService.selectById(user.getRoleId());
            List<SysPermission> permissions = permissionService.selectByUserId(user.getUserId());
            List<SysPermission> permissionTree = permissionService.buildPermissionTree(permissions);

            // 转换为 DTO
            LoginResponseDTO response = LoginResponseDTO.builder()
                .token(token)
                .refreshToken(token)
                .expiresIn(jwtTokenProvider.getExpirationTime(token).intValue())
                .userId(user.getUserId())
                .user(DtoConverter.toUserDTO(user))
                .role(DtoConverter.toRoleDTO(role))
                .permissions(DtoConverter.toPermissionDTOList(permissionTree))
                .build();

            return ResultMessage.success(response);
        } catch (Exception e) {
            logger.error("手机号登录失败", e);
            return ResultMessage.error("登录失败，请稍后重试");
        }
    }

    /**
     * 自动注册手机号用户
     *
     * @param tel 手机号
     * @return 新创建的用户
     */
    private SysUser autoRegisterTelUser(String tel) {
        SysUser user = new SysUser();
        // 用户名使用手机号后 4 位
        String suffix = tel.length() >= 4 ? tel.substring(tel.length() - 4) : tel;
        user.setUsername("tel_" + suffix + "_" + System.currentTimeMillis() % 1000);
        user.setPassword(passwordService.encryptPassword(java.util.UUID.randomUUID().toString()));
        user.setName("用户" + suffix);
        user.setTel(tel);
        user.setRoleId(2);  // 默认普通用户角色
        user.setIsAdmin("0");
        user.setState("1");

        userService.add(user);
        return user;
    }

    /**
     * 微信登录 - 自动注册模式
     *
     * @param requestBody 包含微信 code 和可选的邀请人 ID
     * @return 登录结果
     */
    @AnonymousAccess
    @PostMapping("/wx-login")
    @ResponseBody
    public ResultMessage wxLogin(@RequestBody Map<String, Object> requestBody, HttpServletRequest request) {
        try {
            String code = (String) requestBody.get("code");
            Integer inviterId = (Integer) requestBody.get("inviterId");

            if (!StringUtils.hasText(code)) {
                return ResultMessage.error("微信登录 code 不能为空");
            }

            // 1. 调用微信 API 获取 openid 和 session_key
            Map<String, String> wxLoginInfo = wxLoginService.getWxLoginInfo(code);
            String openid = wxLoginInfo.get("openid");
            String sessionKey = wxLoginInfo.get("session_key");
            String unionId = wxLoginInfo.get("unionid");

            if (!StringUtils.hasText(openid)) {
                return ResultMessage.error("获取微信 openid 失败");
            }

            // 2. 查询或创建用户
            SysUserAuth userAuth = userAuthService.getByOpenIdAndPlatform(openid, "wechat");
            SysUser user;
            boolean isNewUser = false;

            if (userAuth == null) {
                // 新用户 - 自动注册
                user = autoRegisterWechatUser(openid, unionId, sessionKey, wxLoginInfo, inviterId);
                isNewUser = true;

                // 创建认证记录
                userAuth = new SysUserAuth();
                userAuth.setUserId(user.getUserId());
                userAuth.setOpenId(openid);
                userAuth.setUnionId(unionId);
                userAuth.setPlatform("wechat");
                userAuth.setProfile(new com.google.gson.Gson().toJson(wxLoginInfo));
                userAuthService.save(userAuth);
            } else {
                // 老用户 - 直接登录
                user = userService.selectUserByUserId(userAuth.getUserId());
            }

            // 更新最后登录时间和 IP
            user.setLoginTime(new java.util.Date());
            user.setLoginIp(CmsUtils.getClientIp(request));
            userService.update(user);

            // 3. 生成 JWT Token
            org.springframework.security.core.authority.SimpleGrantedAuthority authority =
                new org.springframework.security.core.authority.SimpleGrantedAuthority(
                    "ROLE_" + (user.getRoleId() != null ? user.getRoleId() : 2)
                );
            UserDetails userDetails = new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                java.util.Collections.singletonList(authority)
            );
            String token = jwtTokenProvider.generateToken(userDetails, user.getUserId());

            // 4. 获取用户角色和权限
            SysAuthRole role = authRoleService.selectById(user.getRoleId());
            List<SysPermission> permissions = permissionService.selectByUserId(user.getUserId());
            List<SysPermission> permissionTree = permissionService.buildPermissionTree(permissions);

            // 5. 返回结果
            Map<String, Object> result = new HashMap<>();
            result.put("token", token);
            result.put("refreshToken", token);
            result.put("expiresIn", jwtTokenProvider.getExpirationTime(token));
            result.put("userId", user.getUserId());
            result.put("isNewUser", isNewUser);
            result.put("user", user);
            result.put("role", role);
            result.put("permissions", permissionTree);

            return ResultMessage.success(result);
        } catch (Exception e) {
            logger.error("微信登录失败：{}", e.getMessage(), e);
            return ResultMessage.error("微信登录失败：" + e.getMessage());
        }
    }

    /**
     * 自动注册微信用户
     *
     * @param openid 微信 openid
     * @param unionId 微信 unionid
     * @param sessionKey 微信 session_key
     * @param wxInfo 微信返回的完整信息
     * @param inviterId 邀请人 ID
     * @return 新创建的用户
     */
    private SysUser autoRegisterWechatUser(String openid, String unionId,
                                            String sessionKey, Map<String, String> wxInfo,
                                            Integer inviterId) {
        // 1. 创建用户
        SysUser user = new SysUser();
        user.setUsername("wx_" + openid.substring(0, Math.min(10, openid.length())));
        user.setPassword(passwordService.encryptPassword(java.util.UUID.randomUUID().toString()));
        user.setName("微信用户" + System.currentTimeMillis() % 10000);
        user.setRoleId(2);  // 默认普通用户角色
        user.setIsAdmin("0");
        user.setState("1");
        user.setAvatar(null);  // 默认头像

        userService.add(user);

        // 2. 处理邀请关系 (如果有)
        if (inviterId != null) {
            // TODO: 创建邀请记录
            // invitationService.createInvitation(inviterId, user.getUserId());
        }

        return user;
    }

    /**
     * 用户注册
     */
    @AnonymousAccess
    @PostMapping("")
    @Operation(summary = "用户注册", description = "新用户注册，需提供验证码")
    public ResultMessage create(@Valid @RequestBody RegisterParam param) {
        try {
            // 验证验证码
            SysUser codeUser = new SysUser();
            codeUser.setEmail(param.getEmail() != null ? param.getEmail() : param.getTel());
            codeUser.setTel(param.getTel());
            codeUser.setCode(param.getCode());
            int row = userService.queryCaptcha(codeUser);
            if (row < 1) {
                return ResultMessage.error("无效验证码");
            }

            // 检查用户名是否已存在（防止并发注册导致重复）
            if (StringUtils.hasText(param.getUsername())) {
                SysUser existingUser = userService.selectUserByUsername(param.getUsername());
                if (!ObjectUtils.isEmpty(existingUser)) {
                    return ResultMessage.error("用户名已存在");
                }
            }

            // 检查邮箱是否已存在
            if (StringUtils.hasText(param.getEmail())) {
                SysUser existingUser = userService.selectUserByEmail(param.getEmail());
                if (!ObjectUtils.isEmpty(existingUser)) {
                    return ResultMessage.error("邮箱已注册");
                }
            }

            // 检查手机号是否已存在
            if (StringUtils.hasText(param.getTel())) {
                SysUser existingUser = userService.selectUserByTel(param.getTel());
                if (!ObjectUtils.isEmpty(existingUser)) {
                    return ResultMessage.error("手机号已注册");
                }
            }

            // 创建用户
            SysUser user = new SysUser();
            user.setUsername(param.getUsername());
            user.setName(param.getName());
            user.setEmail(param.getEmail());
            user.setTel(param.getTel());
            user.setPassword(passwordService.encryptPassword(param.getPassword()));

            if (userService.add(user) > 0) {
                return ResultMessage.success(DtoConverter.toUserDTO(user));
            }
            return ResultMessage.error("注册失败");
        } catch (Exception e) {
            logger.error("注册失败", e);
            return ResultMessage.error("注册失败");
        }
    }

    /**
     * 查询用户列表
     *
     * @param user 查询条件
     * @return 用户列表
     */
    @GetMapping("")
    @ResponseBody
    @Operation(summary = "根据条件查询用户信息列表", description = "返回用户信息列表")
    public ResultMessage queryUsers(SysUser user, HttpServletRequest request) {
        try {
            PageFilter pageFilter = initPageFilter(request);
            List<SysUser> users = userService.queryUsers(user, pageFilter);
            ResultMessage result = ResultMessage.success();
            result.put("data", new PageInfo<>(users));
            return result;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return ResultMessage.error();
        }
    }

    /**
     * 用户信息修改
     */
    @PutMapping("/{userId}")
    @Operation(summary = "修改用户信息", description = "更新用户个人信息")
    public ResultMessage update(@PathVariable Integer userId, @Valid @RequestBody UserUpdateParam param) {
        try {
            SysUser user = userService.selectUserByUserId(userId);
            if (ObjectUtils.isEmpty(user)) {
                return ResultMessage.error("无此用户，更新失败");
            }

            // 检查邮箱
            if (StringUtils.hasText(param.getEmail())) {
                SysUser existingUser = userService.selectUserByEmail(param.getEmail());
                if (!ObjectUtils.isEmpty(existingUser) && !existingUser.getUserId().equals(user.getUserId())) {
                    return ResultMessage.error("邮箱已被其他用户绑定，更新失败");
                }
                user.setEmail(param.getEmail());
            }

            // 检查手机号
            if (StringUtils.hasText(param.getTel())) {
                SysUser existingUser = userService.selectUserByTel(param.getTel());
                if (!ObjectUtils.isEmpty(existingUser) && !existingUser.getUserId().equals(user.getUserId())) {
                    return ResultMessage.error("手机号已被其他用户绑定，更新失败");
                }
                user.setTel(param.getTel());
            }

            // 更新其他字段
            if (StringUtils.hasText(param.getPassword())) {
                user.setPassword(passwordService.encryptPassword(param.getPassword()));
            }
            if (StringUtils.hasText(param.getAvatar())) {
                user.setAvatar(param.getAvatar());
            }
            if (StringUtils.hasText(param.getName())) {
                user.setName(param.getName());
            }

            if (userService.update(user) > 0) {
                SysUser updatedUser = userService.selectUserByUserId(userId);
                return ResultMessage.success(DtoConverter.toUserDTO(updatedUser));
            }
            return ResultMessage.error("更新失败");
        } catch (Exception e) {
            logger.error("更新用户信息失败", e);
            return ResultMessage.error("更新失败");
        }
    }

    /**
     * 重置密码（忘记密码场景）
     *
     * @param param 重置密码参数（邮箱 + 验证码 + 新密码）
     * @return 重置结果
     */
    @AnonymousAccess
    @PostMapping("/resetPassword")
    @Operation(summary = "重置密码", description = "通过邮箱验证码重置密码，无需登录")
    public ResultMessage resetPassword(@Valid @RequestBody ResetPasswordParam param) {
        try {
            // 1. 验证验证码
            SysUser codeUser = new SysUser();
            codeUser.setEmail(param.getEmail());
            codeUser.setCode(param.getCode());
            int row = userService.queryCaptcha(codeUser);
            if (row < 1) {
                return ResultMessage.error("验证码错误或已过期");
            }

            // 2. 根据邮箱查找用户
            SysUser user = userService.selectUserByEmail(param.getEmail());
            if (ObjectUtils.isEmpty(user)) {
                return ResultMessage.error("该邮箱未注册");
            }

            // 3. 更新密码
            user.setPassword(passwordService.encryptPassword(param.getPassword()));
            if (userService.update(user) > 0) {
                return ResultMessage.success("密码重置成功");
            }
            return ResultMessage.error("密码重置失败");
        } catch (Exception e) {
            logger.error("重置密码失败", e);
            return ResultMessage.error("密码重置失败");
        }
    }

    /**
     * 邮箱验证码发送
     */
    @AnonymousAccess
    @PostMapping("/sendEmailCaptcha")
    @Operation(summary = "发送邮箱验证码", description = "向指定邮箱发送验证码，用于注册或找回密码")
    public ResultMessage sendEmailCaptcha(@Valid @RequestBody SendCaptchaParam param) {
        try {
            // 找回密码模式检查邮箱是否已注册
            if ("forget".equals(param.getType())) {
                SysUser user = userService.selectUserByEmail(param.getEmail());
                if (ObjectUtils.isEmpty(user)) {
                    return ResultMessage.error("该邮箱未注册");
                }
            }

            // 生成并发送验证码
            SysUser code = userService.generateCode(new SysUser().setEmail(param.getEmail()));
            CaptchaUtils.CaptchaResult result = captchaUtils.sendEmailCaptcha(param.getEmail(), code.getCode());

            if (result.isSuccess()) {
                return ResultMessage.success();
            } else {
                return ResultMessage.error(result.getMessage());
            }
        } catch (Exception e) {
            logger.error("发送验证码邮件失败", e);
            return ResultMessage.error("发送失败，请稍后重试");
        }
    }

    /**
     * 短信验证码发送
     */
    @AnonymousAccess
    @PostMapping("/sendSmsCaptcha")
    @Operation(summary = "发送短信验证码", description = "向指定手机号发送验证码，用于注册、登录或找回密码")
    public ResultMessage sendSmsCaptcha(@Valid @RequestBody SendCaptchaParam param) {
        try {
            // 找回密码模式检查手机号是否已注册
            if ("forget".equals(param.getType())) {
                SysUser user = userService.selectUserByTel(param.getTel());
                if (ObjectUtils.isEmpty(user)) {
                    return ResultMessage.error("该手机号未注册");
                }
            }

            // 生成并发送验证码
            SysUser codeUser = new SysUser().setEmail(param.getTel());
            SysUser code = userService.generateCode(codeUser);
            CaptchaUtils.CaptchaResult result = captchaUtils.sendSmsCaptcha(param.getTel(), code.getCode());

            if (result.isSuccess()) {
                return ResultMessage.success();
            } else {
                return ResultMessage.error(result.getMessage());
            }
        } catch (Exception e) {
            logger.error("发送短信验证码失败", e);
            return ResultMessage.error("短信发送失败，请联系管理员");
        }
    }

    /**
     * 验证验证码是否有效
     *
     * @param code 验证码
     * @param email 邮箱
     * @param tel 手机号
     * @return 验证结果
     */
    @AnonymousAccess
    @GetMapping("/checkCaptcha")
    @ResponseBody
    @Operation(summary = "验证验证码是否有效", description = "返回验证结果")
    @Deprecated
    public ResultMessage checkCaptcha(
        @Parameter(description = "验证码") String code,
        @Parameter(description = "手机号") String tel,
        @Parameter(description = "邮箱地址") String email) {
        try {
            SysUser user = new SysUser();
            user.setCode(code);
            user.setEmail(email);
            user.setTel(tel);
            int row = userService.queryCaptcha(user);
            if (row < 1) {
                return ResultMessage.error("无效验证码");
            }
            return ResultMessage.success();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return ResultMessage.error("操作失败，请联系管理员");
        }
    }

    /**
     * 检查用户名和手机号是否已存在
     *
     * @param user 用户名
     * @return 检查结果
     */
    @AnonymousAccess
    @GetMapping("/checkUser")
    @ResponseBody
    @Operation(summary = "检查用户名和手机号是否已存在", description = "返回检查结果")
    public ResultMessage checkUser(SysUser user) {
        try {
            String userName = user.getUsername();
            String userTel = user.getTel();
            String userEmail = user.getEmail();
            user = userService.selectUserByTel(userTel);
            if (!ObjectUtils.isEmpty(user)) {
                return ResultMessage.error("手机已注册");
            }
            user = userService.selectUserByEmail(userEmail);
            if (!ObjectUtils.isEmpty(user)) {
                return ResultMessage.error("邮箱已注册");
            }
            user = userService.selectUserByUsername(userName);
            if (!ObjectUtils.isEmpty(user)) {
                return ResultMessage.error("用户名已存在");
            }
            return ResultMessage.success();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return ResultMessage.error("操作失败，请联系管理员");
        }
    }
}
