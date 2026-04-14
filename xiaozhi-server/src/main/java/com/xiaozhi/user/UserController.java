package com.xiaozhi.user;

import com.xiaozhi.server.web.BaseController;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaIgnore;
import cn.dev33.satoken.stp.StpUtil;
import com.xiaozhi.common.annotation.AuditLog;
import com.xiaozhi.common.annotation.CheckOwner;
import com.xiaozhi.common.exception.OperationFailedException;
import com.xiaozhi.common.exception.ResourceNotFoundException;
import com.google.gson.Gson;
import com.xiaozhi.common.model.bo.UserBO;
import com.xiaozhi.common.model.req.UserCheckReq;
import com.xiaozhi.common.model.req.UserLoginReq;
import com.xiaozhi.common.model.req.UserPageReq;
import com.xiaozhi.common.model.req.UserRegisterReq;
import com.xiaozhi.common.model.req.UserResetPasswordReq;
import com.xiaozhi.common.model.req.UserSendCaptchaReq;
import com.xiaozhi.common.model.req.UserTelLoginReq;
import com.xiaozhi.common.model.req.UserUpdateReq;
import com.xiaozhi.common.model.req.UserWechatLoginReq;
import com.xiaozhi.common.model.resp.LoginResp;
import com.xiaozhi.common.web.ApiResponse;
import com.xiaozhi.security.AuthenticationService;
import com.xiaozhi.user.service.UserService;
import com.xiaozhi.user.service.WxLoginService;
import com.xiaozhi.common.model.bo.UserAuthBO;
import com.xiaozhi.userauth.service.UserAuthService;
import com.xiaozhi.utils.CaptchaUtils;
import com.xiaozhi.utils.RequestContextUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/user")
@Tag(name = "用户管理", description = "用户相关操作")
public class UserController extends BaseController {

    @Resource
    private UserAppService userAppService;

    @Resource
    private UserService userService;

    @Resource
    private AuthenticationService authenticationService;

    @Resource
    private WxLoginService wxLoginService;

    @Resource
    private UserAuthService userAuthService;

    @Resource
    private CaptchaUtils captchaUtils;

    @GetMapping("/check-token")
    @Operation(summary = "检查Token有效性", description = "验证当前Token是否有效，有效则返回用户信息")
    public ApiResponse<?> checkToken() {
        if (!StpUtil.isLogin()) {
            return ApiResponse.unauthorized("Token无效或已过期");
        }
        Integer userId = StpUtil.getLoginIdAsInt();
        LoginResp response = userAppService.buildLoginResp(userId, StpUtil.getTokenValue(), false);
        if (response == null) {
            return ApiResponse.unauthorized("用户不存在");
        }
        return ApiResponse.success(response);
    }

    @PostMapping("/refresh-token")
    @Operation(summary = "刷新Token", description = "刷新Token有效期，返回新的Token")
    public ApiResponse<?> refreshToken() {
        if (!StpUtil.isLogin()) {
            return ApiResponse.unauthorized("用户不存在");
        }
        Integer userId = StpUtil.getLoginIdAsInt();
        if (userService.getBO(userId) == null) {
            return ApiResponse.unauthorized("用户不存在");
        }

        int expireSeconds = userAppService.getTokenExpireSeconds();
        StpUtil.logout();
        StpUtil.login(userId, expireSeconds);
        LoginResp response = userAppService.buildLoginResp(userId, StpUtil.getTokenValue(), false);
        return response == null
            ? ApiResponse.unauthorized("Token刷新失败，请重新登录")
            : ApiResponse.success(response);
    }

    @SaIgnore
    @PostMapping("/login")
    @AuditLog(module = "用户管理", operation = "用户登录")
    @Operation(summary = "用户名密码登录", description = "使用用户名/邮箱/手机号和密码进行登录")
    public ApiResponse<?> login(@Valid @RequestBody UserLoginReq req, HttpServletRequest request) {
        UserBO user = userAppService.login(req.getUsername(), req.getPassword());
        userAppService.recordLoginInfo(user, RequestContextUtils.getClientIp(request));

        int expireSeconds = userAppService.getTokenExpireSeconds();
        StpUtil.login(user.getUserId(), expireSeconds);
        return ApiResponse.success(requireLoginResp(user.getUserId(), false));
    }

    @SaIgnore
    @PostMapping("/tel-login")
    @AuditLog(module = "用户管理", operation = "手机号登录")
    @Operation(summary = "手机号验证码登录", description = "使用手机号和验证码登录，未注册自动注册")
    public ApiResponse<?> telLogin(@Valid @RequestBody UserTelLoginReq req, HttpServletRequest request) {
        if (!userService.checkCaptcha(req.getTel(), req.getCode())) {
            throw new IllegalArgumentException("验证码错误或已过期");
        }

        UserBO user = userService.getByTel(req.getTel());
        if (user == null) {
            String suffix = req.getTel().length() >= 4 ? req.getTel().substring(req.getTel().length() - 4) : req.getTel();
            UserBO createUser = new UserBO();
            createUser.setUsername("tel_" + suffix + "_" + System.currentTimeMillis() % 1000);
            createUser.setPassword(authenticationService.encryptPassword(UUID.randomUUID().toString()));
            createUser.setName("用户" + suffix);
            createUser.setTel(req.getTel());
            user = userAppService.createUserWithDefaults(createUser);
        }

        userAppService.recordLoginInfo(user, RequestContextUtils.getClientIp(request));

        int expireSeconds = userAppService.getTokenExpireSeconds();
        StpUtil.login(user.getUserId(), expireSeconds);
        return ApiResponse.success(requireLoginResp(user.getUserId(), false));
    }

    @SaIgnore
    @PostMapping("/wx-login")
    @ResponseBody
    @AuditLog(module = "用户管理", operation = "微信登录")
    @Operation(summary = "微信登录", description = "使用微信 code 登录，未注册自动注册")
    public ApiResponse<?> wxLogin(@Valid @RequestBody UserWechatLoginReq req, HttpServletRequest request) {
        Map<String, String> wxLoginInfo = wxLoginService.getWxLoginInfo(req.getCode());
        String openId = wxLoginInfo.get("openid");
        String unionId = wxLoginInfo.get("unionid");
        if (!StringUtils.hasText(openId)) {
            throw new IllegalStateException("获取微信openid失败");
        }

        UserAuthBO userAuth = userAuthService.getByOpenIdAndPlatform(openId, "wechat");
        UserBO user;
        boolean isNewUser = false;
        if (userAuth == null) {
            UserBO createUser = new UserBO();
            createUser.setUsername("wx_" + openId.substring(0, Math.min(10, openId.length())));
            createUser.setPassword(authenticationService.encryptPassword(UUID.randomUUID().toString()));
            createUser.setName("微信用户" + System.currentTimeMillis() % 10000);
            user = userAppService.createUserWithDefaults(createUser);
            isNewUser = true;

            userAuth = new UserAuthBO();
            userAuth.setUserId(user.getUserId());
            userAuth.setOpenId(openId);
            userAuth.setUnionId(unionId);
            userAuth.setPlatform("wechat");
            userAuth.setProfile(new Gson().toJson(wxLoginInfo));
            userAuthService.create(userAuth);
        } else {
            user = userService.getBO(userAuth.getUserId());
            if (user == null) {
                throw new ResourceNotFoundException("用户不存在");
            }
        }

        userAppService.recordLoginInfo(user, RequestContextUtils.getClientIp(request));

        int expireSeconds = userAppService.getTokenExpireSeconds();
        StpUtil.login(user.getUserId(), expireSeconds);
        return ApiResponse.success(requireLoginResp(user.getUserId(), isNewUser));
    }

    @SaIgnore
    @PostMapping("")
    @AuditLog(module = "用户管理", operation = "用户注册")
    @Operation(summary = "用户注册", description = "新用户注册")
    public ApiResponse<?> create(@Valid @RequestBody UserRegisterReq req) {
        return ApiResponse.success(userAppService.register(req));
    }

    @GetMapping("")
    @ResponseBody
    @SaCheckPermission("system:user:api:list")
    @Operation(summary = "根据条件查询用户信息列表", description = "返回用户信息列表")
    public ApiResponse<?> queryUsers(@Valid UserPageReq req) {
        return ApiResponse.success(userAppService.page(req));
    }

    @PutMapping("/{userId}")
    @SaCheckPermission("system:setting:account:api:update")
    @CheckOwner(resource = "user", id = "#userId")
    @AuditLog(module = "用户管理", operation = "更新用户信息")
    @Operation(summary = "修改用户信息", description = "更新用户个人信息")
    public ApiResponse<?> update(@PathVariable Integer userId, @Valid @RequestBody UserUpdateReq req) {
        return ApiResponse.success(userAppService.update(userId, req));
    }

    @SaIgnore
    @PostMapping("/resetPassword")
    @AuditLog(module = "用户管理", operation = "重置密码")
    @Operation(summary = "重置密码", description = "通过邮箱验证码重置密码")
    public ApiResponse<?> resetPassword(@Valid @RequestBody UserResetPasswordReq req) {
        userAppService.resetPassword(req);
        return ApiResponse.success("密码重置成功");
    }

    @SaIgnore
    @PostMapping("/sendEmailCaptcha")
    @Operation(summary = "发送邮箱验证码", description = "向指定邮箱发送验证码")
    public ApiResponse<?> sendEmailCaptcha(@Valid @RequestBody UserSendCaptchaReq req) {
        if ("forget".equals(req.getType()) && userService.getByEmail(req.getEmail()) == null) {
            throw new IllegalArgumentException("该邮箱未注册");
        }

        String code = userService.generateCaptcha(req.getEmail());
        CaptchaUtils.CaptchaResult result = captchaUtils.sendEmailCaptcha(req.getEmail(), code);
        if (!result.isSuccess()) {
            throw new OperationFailedException(result.getMessage());
        }
        return ApiResponse.success();
    }

    @SaIgnore
    @PostMapping("/sendSmsCaptcha")
    @Operation(summary = "发送短信验证码", description = "向指定手机号发送验证码")
    public ApiResponse<?> sendSmsCaptcha(@Valid @RequestBody UserSendCaptchaReq req) {
        if ("forget".equals(req.getType()) && userService.getByTel(req.getTel()) == null) {
            throw new IllegalArgumentException("该手机号未注册");
        }

        String code = userService.generateCaptcha(req.getTel());
        CaptchaUtils.CaptchaResult result = captchaUtils.sendSmsCaptcha(req.getTel(), code);
        if (!result.isSuccess()) {
            throw new OperationFailedException(result.getMessage());
        }
        return ApiResponse.success();
    }

    @SaIgnore
    @GetMapping("/checkUser")
    @ResponseBody
    @Operation(summary = "检查用户名和手机号是否已存在", description = "返回检查结果")
    public ApiResponse<?> checkUser(@Valid UserCheckReq req) {
        if (StringUtils.hasText(req.getTel()) && userService.getByTel(req.getTel()) != null) {
            throw new IllegalStateException("手机已注册");
        }
        if (StringUtils.hasText(req.getEmail()) && userService.getByEmail(req.getEmail()) != null) {
            throw new IllegalStateException("邮箱已注册");
        }
        if (StringUtils.hasText(req.getUsername()) && userService.getByUsername(req.getUsername()) != null) {
            throw new IllegalStateException("用户名已存在");
        }
        return ApiResponse.success();
    }

    private LoginResp requireLoginResp(Integer userId, boolean isNewUser) {
        LoginResp response = userAppService.buildLoginResp(userId, StpUtil.getTokenValue(), isNewUser);
        if (response == null) {
            throw new IllegalStateException("登录成功但用户信息加载失败");
        }
        return response;
    }
}
