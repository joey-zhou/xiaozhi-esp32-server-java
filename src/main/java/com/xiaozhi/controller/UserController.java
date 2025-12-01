package com.xiaozhi.controller;

import com.github.pagehelper.PageInfo;
import com.xiaozhi.common.exception.UserPasswordNotMatchException;
import com.xiaozhi.common.exception.UsernameNotFoundException;
import com.xiaozhi.common.web.ResultMessage;
import com.xiaozhi.common.web.PageFilter;
import com.xiaozhi.entity.SysAuthRole;
import com.xiaozhi.entity.SysPermission;
import com.xiaozhi.entity.SysUser;
import com.xiaozhi.entity.SysUserAuth;
import com.xiaozhi.security.AuthenticationService;
import com.xiaozhi.service.SysPermissionService;
import com.xiaozhi.service.SysUserService;
import com.xiaozhi.service.SysUserAuthService;
import com.xiaozhi.service.WxLoginService;
import com.xiaozhi.service.SysAuthRoleService;
import cn.dev33.satoken.annotation.SaIgnore;
import cn.dev33.satoken.stp.StpUtil;
import com.xiaozhi.utils.CaptchaUtils;
import com.xiaozhi.utils.EmailUtils;
import com.xiaozhi.utils.SmsUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
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
    private AuthenticationService authenticationService;

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
     * @param loginRequest 包含用户名和密码的请求体
     * @return 登录结果
     * @throws UsernameNotFoundException
     * @throws UserPasswordNotMatchException
     */
    @SaIgnore
    @PostMapping("/login")
    @ResponseBody
    @Operation(summary = "用户登录", description = "返回登录结果")
    public ResultMessage login(@RequestBody Map<String, Object> loginRequest, HttpServletRequest request) {
        try {
            String username = (String) loginRequest.get("username");
            String password = (String) loginRequest.get("password");

            userService.login(username, password);
            SysUser user = userService.selectUserByUsername(username);

            // Sa-Token登录
            StpUtil.login(user.getUserId(), 2592000);  // 30天过期

            // 获取Token
            String token = StpUtil.getTokenValue();

            // 获取用户角色
            SysAuthRole role = authRoleService.selectById(user.getRoleId());

            // 获取用户权限
            List<SysPermission> permissions = permissionService.selectByUserId(user.getUserId());

            // 构建权限树
            List<SysPermission> permissionTree = permissionService.buildPermissionTree(permissions);

            Map<String, Object> result = new HashMap<>();
            result.put("token", token);
            result.put("refreshToken", token); // Sa-Token 使用相同token作为refresh token
            result.put("expiresIn", 2592000);
            result.put("userId", user.getUserId());
            result.put("user", user);
            result.put("role", role);
            result.put("permissions", permissionTree);

            return ResultMessage.success(result);
        } catch (UsernameNotFoundException e) {
            return ResultMessage.error("用户不存在");
        } catch (UserPasswordNotMatchException e) {
            return ResultMessage.error("密码错误");
        } catch (Exception e) {
            logger.info(e.getMessage(), e);
            return ResultMessage.error("操作失败");
        }
    }

    /**
     * 手机号验证码登录
     *
     * @param loginRequest 包含手机号和验证码的请求体
     * @return 登录结果
     */
    @SaIgnore
    @PostMapping("/tel-login")
    @ResponseBody
    @Operation(summary = "手机号验证码登录", description = "返回登录结果")
    public ResultMessage telLogin(@RequestBody Map<String, Object> loginRequest, HttpServletRequest request) {
        try {
            String tel = (String) loginRequest.get("tel");
            String code = (String) loginRequest.get("code");

            // 验证手机号格式
            if (!captchaUtils.isValidPhoneNumber(tel)) {
                return ResultMessage.error("手机号格式不正确");
            }

            if (!StringUtils.hasText(code)) {
                return ResultMessage.error("验证码不能为空");
            }

            // 验证验证码是否正确
            SysUser codeUser = new SysUser();
            codeUser.setEmail(tel);  // 注意：这里复用了email字段存储手机号
            codeUser.setCode(code);
            int row = userService.queryCaptcha(codeUser);
            if (row < 1) {
                return ResultMessage.error("验证码错误或已过期");
            }

            // 根据手机号查询用户
            SysUser user = userService.selectUserByTel(tel);

            // 如果用户不存在，返回状态码201，提示需要注册
            if (user == null) {
                return new ResultMessage(201, "该手机号未注册，请先注册", null);
            }

            // Sa-Token登录
            StpUtil.login(user.getUserId(), 2592000);  // 30天过期

            // 获取Token
            String token = StpUtil.getTokenValue();

            // 获取用户角色
            SysAuthRole role = authRoleService.selectById(user.getRoleId());

            // 获取用户权限
            List<SysPermission> permissions = permissionService.selectByUserId(user.getUserId());

            // 构建权限树
            List<SysPermission> permissionTree = permissionService.buildPermissionTree(permissions);

            Map<String, Object> result = new HashMap<>();
            result.put("token", token);
            result.put("refreshToken", token); // Sa-Token 使用相同token作为refresh token
            result.put("expiresIn", 2592000);
            result.put("userId", user.getUserId());
            result.put("user", user);
            result.put("role", role);
            result.put("permissions", permissionTree);

            return ResultMessage.success(result);
        } catch (Exception e) {
            logger.error("手机号登录失败: {}", e.getMessage(), e);
            return ResultMessage.error("登录失败，请稍后重试");
        }
    }

    /**
     * 微信登录 - 自动注册模式
     *
     * @param requestBody 包含微信code和可选的邀请人ID
     * @return 登录结果
     */
    @SaIgnore
    @PostMapping("/wx-login")
    @ResponseBody
    public ResultMessage wxLogin(@RequestBody Map<String, Object> requestBody, HttpServletRequest request) {
        try {
            String code = (String) requestBody.get("code");
            Integer inviterId = (Integer) requestBody.get("inviterId");

            if (!StringUtils.hasText(code)) {
                return ResultMessage.error("微信登录code不能为空");
            }

            // 1. 调用微信API获取openid和session_key
            Map<String, String> wxLoginInfo = wxLoginService.getWxLoginInfo(code);
            String openid = wxLoginInfo.get("openid");
            String sessionKey = wxLoginInfo.get("session_key");
            String unionId = wxLoginInfo.get("unionid");

            if (!StringUtils.hasText(openid)) {
                return ResultMessage.error("获取微信openid失败");
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

                // 更新最后登录时间
                user.setLoginTime(new java.util.Date());
                userService.update(user);
            }

            // 3. Sa-Token登录
            StpUtil.login(user.getUserId(), 2592000);  // 30天过期

            // 4. 获取Token
            String token = StpUtil.getTokenValue();

            // 5. 获取用户角色和权限
            SysAuthRole role = authRoleService.selectById(user.getRoleId());
            List<SysPermission> permissions = permissionService.selectByUserId(user.getUserId());
            List<SysPermission> permissionTree = permissionService.buildPermissionTree(permissions);

            // 6. 返回结果
            Map<String, Object> result = new HashMap<>();
            result.put("token", token);
            result.put("refreshToken", token);
            result.put("expiresIn", 2592000);
            result.put("userId", user.getUserId());
            result.put("isNewUser", isNewUser);
            result.put("user", user);
            result.put("role", role);
            result.put("permissions", permissionTree);

            return ResultMessage.success(result);
        } catch (Exception e) {
            logger.error("微信登录失败: {}", e.getMessage(), e);
            return ResultMessage.error("微信登录失败: " + e.getMessage());
        }
    }

    /**
     * 自动注册微信用户
     *
     * @param openid 微信openid
     * @param unionId 微信unionid
     * @param sessionKey 微信session_key
     * @param wxInfo 微信返回的完整信息
     * @param inviterId 邀请人ID
     * @return 新创建的用户
     */
    private SysUser autoRegisterWechatUser(String openid, String unionId,
                                            String sessionKey, Map<String, String> wxInfo,
                                            Integer inviterId) {
        // 1. 创建用户
        SysUser user = new SysUser();
        user.setUsername("wx_" + openid.substring(0, Math.min(10, openid.length())));
        user.setPassword(authenticationService.encryptPassword(java.util.UUID.randomUUID().toString()));
        user.setName("微信用户" + System.currentTimeMillis() % 10000);
        user.setRoleId(2);  // 默认普通用户角色
        user.setIsAdmin("0");
        user.setState("1");
        user.setAvatar(null);  // 默认头像

        userService.add(user);

        // 2. 处理邀请关系(如果有)
        if (inviterId != null) {
            // TODO: 创建邀请记录
            // invitationService.createInvitation(inviterId, user.getUserId());
        }

        return user;
    }

    /**
     * 新增用户
     *
     * @param loginRequest 包含用户信息的请求体
     * @return 添加结果
     */
    @SaIgnore
    @PostMapping("/add")
    @ResponseBody
    @Operation(summary = "新增用户", description = "返回添加结果")
    public ResultMessage add(@RequestBody Map<String, Object> loginRequest, HttpServletRequest request) {
        try {
            String username = (String) loginRequest.get("username");
            String email = (String) loginRequest.get("email");
            String password = (String) loginRequest.get("password");
            String code = (String) loginRequest.get("code");
            String name = (String) loginRequest.get("name");
            String tel = (String) loginRequest.get("tel");
            SysUser user = new SysUser();
            user.setCode(code);
            user.setEmail(email);
            user.setTel(tel);
            int row = userService.queryCaptcha(user);
            if (1 > row)
                return ResultMessage.error("无效验证码");
                
            user.setUsername(username);
            user.setName(name);
            String newPassword = authenticationService.encryptPassword(password);
            user.setPassword(newPassword);
            
            if (0 < userService.add(user)) {
                return ResultMessage.success(user);
            }
            return ResultMessage.error();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return ResultMessage.error();
        }
    }

    /**
     * 用户信息查询
     * 
     * @param username 用户名
     * @return 用户信息
     */
    @GetMapping("/query")
    @ResponseBody
    @Operation(summary = "根据用户名查询用户信息", description = "返回用户信息")
    public ResultMessage query(@Parameter(description = "用户名") String username) {
        try {
            SysUser user = userService.query(username);
            ResultMessage result = ResultMessage.success();
            result.put("data", user);
            return result;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return ResultMessage.error();
        }
    }

    /**
     * 查询用户列表
     * 
     * @param user 查询条件
     * @return 用户列表
     */
    @GetMapping("/queryUsers")
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
     *
     * @param loginRequest 包含用户信息的请求体
     * @return 修改结果
     */
    @PostMapping("/update")
    @ResponseBody
    @Operation(summary = "修改用户信息", description = "返回修改结果")
    public ResultMessage update(@RequestBody Map<String, Object> loginRequest) {
        try {
            String username = (String) loginRequest.get("username");
            String email = (String) loginRequest.get("email");
            String tel = (String) loginRequest.get("tel");
            String password = (String) loginRequest.get("password");
            String name = (String) loginRequest.get("name");
            String avatar = (String) loginRequest.get("avatar");
            
            SysUser userQuery = new SysUser();

            if (StringUtils.hasText(username)) {
                userQuery = userService.selectUserByUsername(username);
                if (ObjectUtils.isEmpty(userQuery)) {
                    return ResultMessage.error("无此用户，更新失败");
                }
            }

            if (StringUtils.hasText(email)) {
                // 检查邮箱是否被其他用户使用
                SysUser existingUser = userService.selectUserByEmail(email);
                if (!ObjectUtils.isEmpty(existingUser) && !existingUser.getUserId().equals(userQuery.getUserId())) {
                    return ResultMessage.error("邮箱已被其他用户绑定，更新失败");
                }
                userQuery.setEmail(email);
            }

            if (StringUtils.hasText(tel)) {
                // 检查手机号是否被其他用户使用
                SysUser existingUser = userService.selectUserByTel(tel);
                if (!ObjectUtils.isEmpty(existingUser) && !existingUser.getUserId().equals(userQuery.getUserId())) {
                    return ResultMessage.error("手机号已被其他用户绑定，更新失败");
                }
                userQuery.setTel(tel);
            }

            if (StringUtils.hasText(password)) {
                String newPassword = authenticationService.encryptPassword(password);
                userQuery.setPassword(newPassword);
            }

            if (StringUtils.hasText(avatar)) {
                userQuery.setAvatar(avatar);
            }

            if (StringUtils.hasText(name)) {
                userQuery.setName(name);
            }
            
            // if (!StringUtils.hasText(avatar) && StringUtils.hasText(name)) {
            //     userQuery.setAvatar(ImageUtils.GenerateImg(name));
            // }

            if (0 < userService.update(userQuery)) {
                // 返回更新后的完整用户信息，供前端使用
                SysUser updatedUser = userService.selectUserByUsername(username);
                return ResultMessage.success(updatedUser);
            }
            return ResultMessage.error();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return ResultMessage.error();
        }
    }

    /**
     * 邮箱验证码发送
     *
     * @param requestBody 包含邮箱和类型的请求体
     * @return 发送结果
     */
    @SaIgnore
    @PostMapping("/sendEmailCaptcha")
    @ResponseBody
    @Operation(summary = "发送邮箱验证码", description = "返回发送结果")
    public ResultMessage sendEmailCaptcha(
        @RequestBody(required = false) Map<String, Object> requestBody,
        HttpServletRequest request) {
        try {
            String email = (String) requestBody.get("email");
            String type = (String) requestBody.get("type");

            // 验证邮箱格式
            if (!captchaUtils.isValidEmail(email)) {
                return ResultMessage.error("邮箱格式不正确");
            }

            // 如果是找回密码，检查邮箱是否已注册
            SysUser user = userService.selectUserByEmail(email);
            if ("forget".equals(type) && ObjectUtils.isEmpty(user)) {
                return ResultMessage.error("该邮箱未注册");
            }

            // 生成验证码
            SysUser code = userService.generateCode(new SysUser().setEmail(email));
            
            // 使用统一的验证码工具类发送邮件
            CaptchaUtils.CaptchaResult result = captchaUtils.sendEmailCaptcha(email, code.getCode());
            
            if (result.isSuccess()) {
                return ResultMessage.success();
            } else {
                return ResultMessage.error(result.getMessage());
            }
        } catch (Exception e) {
            logger.error("发送验证码邮件失败: " + e.getMessage(), e);
            return ResultMessage.error("发送失败，请稍后重试");
        }
    }
    
    /**
     * 短信验证码发送
     *
     * @param requestBody 包含手机号和类型的请求体
     * @return 发送结果
     */
    @SaIgnore
    @PostMapping("/sendSmsCaptcha")
    @ResponseBody
    @Operation(summary = "发送短信验证码", description = "返回发送结果")
    public ResultMessage sendSmsCaptcha(
        @RequestBody(required = false) Map<String, Object> requestBody,
        HttpServletRequest request) {
        try {
            String tel = (String) requestBody.get("tel");
            String type = (String) requestBody.get("type");

            // 验证手机号格式
            if (!captchaUtils.isValidPhoneNumber(tel)) {
                return ResultMessage.error("手机号格式不正确");
            }

            // 如果是找回密码，检查手机号是否已注册
            SysUser user = userService.selectUserByTel(tel);
            if ("forget".equals(type) && ObjectUtils.isEmpty(user)) {
                return ResultMessage.error("该手机号未注册");
            }

            // 生成验证码并保存到数据库
            SysUser codeUser = new SysUser().setEmail(tel);
            SysUser code = userService.generateCode(codeUser);
            
            // 使用统一的验证码工具类发送短信
            CaptchaUtils.CaptchaResult result = captchaUtils.sendSmsCaptcha(tel, code.getCode());
            
            if (result.isSuccess()) {
                return ResultMessage.success();
            } else {
                return ResultMessage.error(result.getMessage());
            }
        } catch (Exception e) {
            logger.error("发送短信验证码失败: {}", e.getMessage(), e);
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
    @SaIgnore
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
            if (1 > row)
                return ResultMessage.error("无效验证码");
            return ResultMessage.success();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return ResultMessage.error("操作失败,请联系管理员");
        }
    }

    /**
     * 检查用户名和手机号是否已存在
     *
     * @param user 用户名
     * @return 检查结果
     */
    @SaIgnore
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
            return ResultMessage.error("操作失败,请联系管理员");
        }
    }
}