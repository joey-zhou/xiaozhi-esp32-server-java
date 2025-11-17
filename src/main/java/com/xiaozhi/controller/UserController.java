package com.xiaozhi.controller;

import com.github.pagehelper.PageInfo;
import com.xiaozhi.common.exception.UserPasswordNotMatchException;
import com.xiaozhi.common.exception.UsernameNotFoundException;
import com.xiaozhi.common.web.ResultMessage;
import com.xiaozhi.common.web.PageFilter;
import com.xiaozhi.entity.SysAuthRole;
import com.xiaozhi.entity.SysPermission;
import com.xiaozhi.entity.SysUser;
import com.xiaozhi.security.AuthenticationService;
import com.xiaozhi.service.SysPermissionService;
import com.xiaozhi.service.SysUserService;
import com.xiaozhi.service.WxLoginService;
import com.xiaozhi.service.SysAuthRoleService;
import com.xiaozhi.utils.JwtUtil;
import com.xiaozhi.utils.CmsUtils;
import com.xiaozhi.utils.EmailUtils;
import com.xiaozhi.utils.ImageUtils;
import com.xiaozhi.utils.SmsUtils;
import com.xiaozhi.utils.CaptchaUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
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
    private SysAuthRoleService authRoleService;

    @Resource
    private SysPermissionService permissionService;

    @Resource
    private SmsUtils smsUtils;
    
    @Resource
    private JwtUtil jwtUtil;

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
    @PostMapping("/login")
    @ResponseBody
    @Operation(summary = "用户登录", description = "返回登录结果")
    public ResultMessage login(@RequestBody Map<String, Object> loginRequest, HttpServletRequest request) {
        try {
            String username = (String) loginRequest.get("username");
            String password = (String) loginRequest.get("password");

            userService.login(username, password);
            SysUser user = userService.selectUserByUsername(username);

            // 获取用户角色
            SysAuthRole role = authRoleService.selectById(user.getRoleId());

            // 获取用户权限
            List<SysPermission> permissions = permissionService.selectByUserId(user.getUserId());

            // 构建权限树
            List<SysPermission> permissionTree = permissionService.buildPermissionTree(permissions);
    
            // 生成JWT Token
            String token = jwtUtil.generateToken(user.getUserId(), user.getUsername());
            String refreshToken = jwtUtil.generateRefreshToken(user.getUserId());

            String sessionId = request.getSession().getId();

            // 保存用户到会话
            HttpSession session = request.getSession();
            session.setAttribute(SysUserService.USER_SESSIONKEY, user);
            
            // 保存用户
            CmsUtils.setUser(request, user);

            Map<String, Object> result = new HashMap<>();
            result.put("user", user);
            result.put("role", role);
            result.put("permissions", permissionTree);
            result.put("token", token);
            result.put("sessionId", sessionId);
            result.put("refreshToken", refreshToken);

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

            // 获取用户角色
            SysAuthRole role = authRoleService.selectById(user.getRoleId());

            // 获取用户权限
            List<SysPermission> permissions = permissionService.selectByUserId(user.getUserId());

            // 构建权限树
            List<SysPermission> permissionTree = permissionService.buildPermissionTree(permissions);
    
            // 生成JWT Token
            String token = jwtUtil.generateToken(user.getUserId(), user.getUsername());
            String refreshToken = jwtUtil.generateRefreshToken(user.getUserId());

            String sessionId = request.getSession().getId();

            // 保存用户到会话
            HttpSession session = request.getSession();
            session.setAttribute(SysUserService.USER_SESSIONKEY, user);
            
            // 保存用户
            CmsUtils.setUser(request, user);

            Map<String, Object> result = new HashMap<>();
            result.put("user", user);
            result.put("role", role);
            result.put("permissions", permissionTree);
            result.put("token", token);
            result.put("sessionId", sessionId);
            result.put("refreshToken", refreshToken);

            return ResultMessage.success(result);
        } catch (Exception e) {
            logger.error("手机号登录失败: {}", e.getMessage(), e);
            return ResultMessage.error("登录失败，请稍后重试");
        }
    }

    /**
     * 微信登录
     *
     * @param requestBody 包含微信code的请求体
     * @return 登录结果
     */
    @PostMapping("/wx-login")
    @ResponseBody
    public ResultMessage wxLogin(@RequestBody Map<String, Object> requestBody, HttpServletRequest request) {
        try {
            String code = (String) requestBody.get("code");
            if (!StringUtils.hasText(code)) {
                return ResultMessage.error("微信登录code不能为空");
            }

            // 调用微信API获取openid和session_key
            // 这里需要实现一个微信登录服务，用于调用微信API
            Map<String, String> wxLoginInfo = wxLoginService.getWxLoginInfo(code);
            String openid = wxLoginInfo.get("openid");
            String sessionKey = wxLoginInfo.get("session_key");

            if (!StringUtils.hasText(openid)) {
                return ResultMessage.error("获取微信openid失败");
            }

            // 根据openid查询用户
            SysUser user = userService.selectUserByWxOpenId(openid);

            // 如果用户不存在，返回特定状态码，前端跳转到绑定页面
            if (user == null) {
                // 保存微信会话信息到session，以便后续绑定使用
                HttpSession session = request.getSession();
                session.setAttribute("wx_session_key", sessionKey);
                session.setAttribute("wx_openid", openid);

                Map<String, Object> result = new HashMap<>();
                result.put("openid", openid);
                result.put("sessionId", request.getSession().getId());

                // 返回状态码201，表示需要绑定账号
                return new ResultMessage(201, "需要绑定账号", result);
            }

            // 保存用户到会话
            HttpSession session = request.getSession();
            session.setAttribute(SysUserService.USER_SESSIONKEY, user);

            // 保存微信会话信息到session
            session.setAttribute("wx_session_key", sessionKey);
            session.setAttribute("wx_openid", openid);

            // 保存用户
            CmsUtils.setUser(request, user);

            // 获取用户角色和权限
            SysAuthRole role = authRoleService.selectById(user.getRoleId());
            List<SysPermission> permissions = permissionService.selectByUserId(user.getUserId());
            List<SysPermission> permissionTree = permissionService.buildPermissionTree(permissions);

            // 生成JWT Token
            String token = jwtUtil.generateToken(user.getUserId(), user.getUsername());
            String refreshToken = jwtUtil.generateRefreshToken(user.getUserId());

            String sessionId = request.getSession().getId();

            Map<String, Object> result = new HashMap<>();
            result.put("user", user);
            result.put("role", role);
            result.put("permissions", permissionTree);
            result.put("token", token);
            result.put("sessionId", sessionId);
            result.put("refreshToken", refreshToken);

            return ResultMessage.success(result);
        } catch (Exception e) {
            logger.error("微信登录失败: {}", e.getMessage(), e);
            return ResultMessage.error("微信登录失败: " + e.getMessage());
        }
    }

    /**
     * 新增用户
     * 
     * @param loginRequest 包含用户信息的请求体
     * @return 添加结果
     */
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