package com.xiaozhi.user;

import com.xiaozhi.server.web.BaseController;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaIgnore;
import cn.dev33.satoken.stp.StpUtil;
import com.xiaozhi.common.annotation.AuditLog;
import com.xiaozhi.common.annotation.CheckOwner;
import com.xiaozhi.common.exception.OperationFailedException;
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
import com.xiaozhi.common.model.PageResult;
import com.xiaozhi.common.model.resp.UserResp;
import com.xiaozhi.common.web.ApiResponse;
import com.xiaozhi.common.web.TrustedProxyPolicy;
import com.xiaozhi.user.service.UserService;
import com.xiaozhi.user.service.WxLoginService;
import com.xiaozhi.utils.CaptchaUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/user")
@Tag(name = "用户管理", description = "用户相关操作")
public class UserController extends BaseController {

    @Resource
    private UserAppService userAppService;

    @Resource
    private UserService userService;

    @Resource
    private WxLoginService wxLoginService;

    @Resource
    private CaptchaUtils captchaUtils;

    @Resource
    private TrustedProxyPolicy trustedProxyPolicy;

    @GetMapping("/check-token")
    @Operation(summary = "检查Token有效性", description = "验证当前Token是否有效，有效则返回用户信息")
    public ApiResponse<LoginResp> checkToken() {
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
    public ApiResponse<LoginResp> refreshToken() {
        if (!StpUtil.isLogin()) {
            return ApiResponse.unauthorized("用户不存在");
        }
        Integer userId = StpUtil.getLoginIdAsInt();
        UserBO user = userService.getBO(userId);
        if (user == null) {
            return ApiResponse.unauthorized("用户不存在");
        }
        // 换发 Token 等于重新授予一个完整有效期的访问权，与登录同级，禁用账号不能靠刷新续命
        userService.requireEnabled(user);

        int expireSeconds = userAppService.getTokenExpireSeconds();
        StpUtil.logout();
        StpUtil.login(userId, expireSeconds);
        LoginResp response = userAppService.buildLoginResp(userId, StpUtil.getTokenValue(), false);
        return response == null
            ? ApiResponse.unauthorized("Token刷新失败，请重新登录")
            : ApiResponse.success(response);
    }

    @PostMapping("/logout")
    @AuditLog(module = "用户管理", operation = "退出登录")
    @Operation(summary = "退出登录", description = "注销当前会话的 Token，注销后该 Token 立即失效")
    public ApiResponse<Void> logout() {
        StpUtil.logout();
        return ApiResponse.success("退出成功");
    }

    @SaIgnore
    @PostMapping("/login")
    @AuditLog(module = "用户管理", operation = "用户登录")
    @Operation(summary = "用户名密码登录", description = "使用用户名/邮箱/手机号和密码进行登录")
    public ApiResponse<LoginResp> login(@Valid @RequestBody UserLoginReq req, HttpServletRequest request) {
        UserBO user = userAppService.login(req.getUsername(), req.getPassword());
        userAppService.recordLoginInfo(user, trustedProxyPolicy.resolveClientIp(request));

        int expireSeconds = userAppService.getTokenExpireSeconds();
        StpUtil.login(user.getUserId(), expireSeconds);
        return ApiResponse.success(requireLoginResp(user.getUserId(), false));
    }

    @SaIgnore
    @PostMapping("/tel-login")
    @AuditLog(module = "用户管理", operation = "手机号登录")
    @Operation(summary = "手机号验证码登录", description = "使用手机号和验证码登录，未注册自动注册")
    public ApiResponse<LoginResp> telLogin(@Valid @RequestBody UserTelLoginReq req, HttpServletRequest request) {
        if (!userService.consumeCaptcha(req.getTel(), req.getCode())) {
            throw new IllegalArgumentException("验证码错误或已过期");
        }

        UserBO user = userAppService.loginByTel(req.getTel());

        userAppService.recordLoginInfo(user, trustedProxyPolicy.resolveClientIp(request));

        int expireSeconds = userAppService.getTokenExpireSeconds();
        StpUtil.login(user.getUserId(), expireSeconds);
        return ApiResponse.success(requireLoginResp(user.getUserId(), false));
    }

    @SaIgnore
    @PostMapping("/wx-login")
    @ResponseBody
    @AuditLog(module = "用户管理", operation = "微信登录")
    @Operation(summary = "微信登录", description = "使用微信 code 登录，未注册自动注册")
    public ApiResponse<LoginResp> wxLogin(@Valid @RequestBody UserWechatLoginReq req, HttpServletRequest request) {
        Map<String, String> wxLoginInfo = wxLoginService.getWxLoginInfo(req.getCode());
        String openId = wxLoginInfo.get("openid");
        String unionId = wxLoginInfo.get("unionid");
        if (!StringUtils.hasText(openId)) {
            throw new IllegalStateException("获取微信openid失败");
        }

        UserAppService.WechatLogin result =
                userAppService.loginByWechat(openId, unionId, new Gson().toJson(wxLoginInfo));
        UserBO user = result.user();

        userAppService.recordLoginInfo(user, trustedProxyPolicy.resolveClientIp(request));

        int expireSeconds = userAppService.getTokenExpireSeconds();
        StpUtil.login(user.getUserId(), expireSeconds);
        return ApiResponse.success(requireLoginResp(user.getUserId(), result.newUser()));
    }

    @SaIgnore
    @PostMapping("")
    @AuditLog(module = "用户管理", operation = "用户注册")
    @Operation(summary = "用户注册", description = "新用户注册")
    public ApiResponse<UserResp> create(@Valid @RequestBody UserRegisterReq req) {
        return ApiResponse.success(userAppService.register(req));
    }

    @GetMapping("")
    @ResponseBody
    @SaCheckPermission("system:user:api:list")
    @Operation(summary = "根据条件查询用户信息列表", description = "返回用户信息列表")
    public ApiResponse<PageResult<UserResp>> queryUsers(@Valid UserPageReq req) {
        return ApiResponse.success(userAppService.page(req));
    }

    @PutMapping("/{userId}")
    @SaCheckPermission("system:setting:account:api:update")
    @CheckOwner(resource = "user", id = "#userId")
    @AuditLog(module = "用户管理", operation = "更新用户信息")
    @Operation(summary = "修改用户信息", description = "更新用户个人信息")
    public ApiResponse<UserResp> update(@PathVariable Integer userId, @Valid @RequestBody UserUpdateReq req) {
        return ApiResponse.success(userAppService.update(userId, req));
    }

    @PutMapping("/{userId}/state")
    @SaCheckPermission("system:user:api:update")
    @AuditLog(module = "用户管理", operation = "启用禁用账号")
    @Operation(summary = "启用/禁用账号", description = "state 取 1-正常、0-禁用；禁用后该账号已签发的 Token 立即失效")
    public ApiResponse<UserResp> updateState(@PathVariable Integer userId, @RequestParam String state) {
        if (UserBO.STATE_DISABLED.equals(state) && StpUtil.getLoginIdAsInt() == userId.intValue()) {
            throw new IllegalArgumentException("不能禁用当前登录账号");
        }
        UserResp updated = userAppService.updateState(userId, state);
        if (UserBO.STATE_DISABLED.equals(state)) {
            StpUtil.logout(userId);
        }
        return ApiResponse.success(updated);
    }

    @SaIgnore
    @PostMapping("/resetPassword")
    @AuditLog(module = "用户管理", operation = "重置密码")
    @Operation(summary = "重置密码", description = "通过邮箱验证码重置密码")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody UserResetPasswordReq req) {
        userAppService.resetPassword(req);
        return ApiResponse.success("密码重置成功");
    }

    @SaIgnore
    @PostMapping("/sendEmailCaptcha")
    @Operation(summary = "发送邮箱验证码", description = "向指定邮箱发送验证码")
    public ApiResponse<Void> sendEmailCaptcha(@Valid @RequestBody UserSendCaptchaReq req) {
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
    public ApiResponse<Void> sendSmsCaptcha(@Valid @RequestBody UserSendCaptchaReq req) {
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
    public ApiResponse<Void> checkUser(@Valid UserCheckReq req) {
        // 三个字段共用一句提示：分字段报「手机已注册」「邮箱已注册」「用户名已存在」等于逐字段确认注册状态，
        // 这个端点匿名可调，会被用来枚举平台已注册的手机号/邮箱/用户名
        boolean taken = (StringUtils.hasText(req.getTel()) && userService.getByTel(req.getTel()) != null)
                || (StringUtils.hasText(req.getEmail()) && userService.getByEmail(req.getEmail()) != null)
                || (StringUtils.hasText(req.getUsername()) && userService.getByUsername(req.getUsername()) != null);
        if (taken) {
            throw new IllegalStateException("该手机号、邮箱或用户名已被注册");
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
