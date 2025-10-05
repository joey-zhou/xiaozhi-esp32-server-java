package com.xiaozhi.controller;

import com.github.pagehelper.PageInfo;
import com.xiaozhi.common.exception.UserPasswordNotMatchException;
import com.xiaozhi.common.exception.UsernameNotFoundException;
import com.xiaozhi.common.web.AjaxResult;
import com.xiaozhi.common.web.PageFilter;
import com.xiaozhi.common.web.SessionProvider;
import com.xiaozhi.entity.SysUser;
import com.xiaozhi.security.AuthenticationService;
import com.xiaozhi.service.SysDeviceService;
import com.xiaozhi.service.SysUserService;
import com.xiaozhi.utils.CmsUtils;
import com.xiaozhi.utils.ImageUtils;
import io.github.biezhi.ome.OhMyEmail;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import static io.github.biezhi.ome.OhMyEmail.SMTP_QQ;

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
    private SysDeviceService deviceService;

    @Resource
    private AuthenticationService authenticationService;

    @Resource
    private SessionProvider sessionProvider;

    @Value("${email.smtp.username}")
    private String emailUsername;

    @Value("${email.smtp.password}")
    private String emailPassword;

    /**
     * @param loginRequest 包含用户名和密码的请求体
     * @return 登录结果
     * @throws UsernameNotFoundException
     * @throws UserPasswordNotMatchException
     */
    @PostMapping("/login")
    @ResponseBody
    @Operation(summary = "用户登录", description = "返回登录结果")
    public AjaxResult login(@RequestBody Map<String, Object> loginRequest, HttpServletRequest request) {
        try {
            String username = (String) loginRequest.get("username");
            String password = (String) loginRequest.get("password");

            userService.login(username, password);
            SysUser user = userService.query(username);

            // 保存用户到会话
            HttpSession session = request.getSession();
            session.setAttribute(SysUserService.USER_SESSIONKEY, user);
            
            // 保存用户
            CmsUtils.setUser(request, user);

            return AjaxResult.success(user);
        } catch (UsernameNotFoundException e) {
            return AjaxResult.error("用户不存在");
        } catch (UserPasswordNotMatchException e) {
            return AjaxResult.error("密码错误");
        } catch (Exception e) {
            logger.info(e.getMessage(), e);
            return AjaxResult.error("操作失败");
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
    public AjaxResult add(@RequestBody Map<String, Object> loginRequest, HttpServletRequest request) {
        try {
            String username = (String) loginRequest.get("username");
            String email = (String) loginRequest.get("email");
            String password = (String) loginRequest.get("password");
            String code = (String) loginRequest.get("code");
            String name = (String) loginRequest.get("name");
            String tel = (String) loginRequest.get("tel");
            
            int row = userService.queryCaptcha(code, email);
            if (1 > row)
                return AjaxResult.error("无效验证码");
                
            SysUser user = new SysUser();
            user.setUsername(username);
            user.setEmail(email);
            user.setName(name);
            user.setTel(tel);
            String newPassword = authenticationService.encryptPassword(password);
            user.setPassword(newPassword);
            
            if (0 < userService.add(user)) {
                return AjaxResult.success(user);
            }
            return AjaxResult.error();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return AjaxResult.error();
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
    public AjaxResult query(@Parameter(description = "用户名") String username) {
        try {
            SysUser user = userService.query(username);
            AjaxResult result = AjaxResult.success();
            result.put("data", user);
            return result;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return AjaxResult.error();
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
    public AjaxResult queryUsers(SysUser user, HttpServletRequest request) {
        try {
            PageFilter pageFilter = initPageFilter(request);
            List<SysUser> users = userService.queryUsers(user, pageFilter);
            AjaxResult result = AjaxResult.success();
            result.put("data", new PageInfo<>(users));
            return result;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return AjaxResult.error();
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
    public AjaxResult update(@RequestBody Map<String, Object> loginRequest) {
        try {
            String username = (String) loginRequest.get("username");
            String email = (String) loginRequest.get("email");
            String password = (String) loginRequest.get("password");
            String name = (String) loginRequest.get("name");
            String avatar = (String) loginRequest.get("avatar");
            
            SysUser userQuery = new SysUser();
            if (StringUtils.hasText(username)) {
                userQuery = userService.selectUserByUsername(username);
            } else if (StringUtils.hasText(email)) {
                userQuery = userService.selectUserByEmail(email);
            }
            
            if (ObjectUtils.isEmpty(userQuery)) {
                return AjaxResult.error("无此用户，操作失败");
            }
            
            if (StringUtils.hasText(password)) {
                String newPassword = authenticationService.encryptPassword(password);
                userQuery.setPassword(newPassword);
            }
            
            if (!StringUtils.hasText(avatar) && StringUtils.hasText(name)) {
                userQuery.setAvatar(ImageUtils.GenerateImg(name));
            }

            if (0 < userService.update(userQuery)) {
                return AjaxResult.success();
            }
            return AjaxResult.error();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return AjaxResult.error();
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
    public AjaxResult sendEmailCaptcha(
        @RequestBody(required = false) Map<String, Object> requestBody,
        HttpServletRequest request) {
        try {
            String email = (String) requestBody.get("email");
            String type = (String) requestBody.get("type");

            // 验证邮箱格式
            if (!isValidEmail(email)) {
                return AjaxResult.error("邮箱格式不正确");
            }

            SysUser user = userService.selectUserByEmail(email);
            if ("forget".equals(type) && ObjectUtils.isEmpty(user)) {
                return AjaxResult.error("该邮箱未注册");
            }

            SysUser code = userService.generateCode(new SysUser().setEmail(email));
            String emailContent = "尊敬的用户您好!您的验证码为:<h3>" + code.getCode() + "</h3>如不是您操作,请忽略此邮件.(有效期10分钟)";

            // 需要配置自己的第三方邮箱认证信息，这里用的QQ邮箱认证信息，需自己申请
            if (!StringUtils.hasText(emailUsername) || !StringUtils.hasText(emailPassword)) {
                return AjaxResult.error("未配置第三方邮箱认证信息,请联系管理员");
            }

            // 配置邮件发送
            OhMyEmail.config(SMTP_QQ(false), emailUsername, emailPassword);

            // 发送邮件
            OhMyEmail.subject("小智ESP32-智能物联网管理平台")
                    .from("小智物联网管理平台")
                    .to(email)
                    .html(emailContent)
                    .send();

            return AjaxResult.success();
        } catch (Exception e) {
            // 根据异常类型返回不同的错误信息
            String errorMsg = "发送失败";
            if (e.getMessage() != null) {
                if (e.getMessage().contains("non-existent account") ||
                        e.getMessage().contains("550") ||
                        e.getMessage().contains("recipient")) {
                    errorMsg = "邮箱地址不存在或无效";
                } else if (e.getMessage().contains("Authentication failed")) {
                    errorMsg = "邮箱服务认证失败，请联系管理员";
                } else if (e.getMessage().contains("timed out")) {
                    errorMsg = "邮件发送超时，请稍后重试";
                }
            }

            return AjaxResult.error(errorMsg);
        }
    }

    /**
     * 简单验证邮箱格式
     * 
     * @param email 邮箱地址
     * @return 是否有效
     */
    private boolean isValidEmail(String email) {
        if (email == null || email.isEmpty()) {
            return false;
        }
        // 简单的邮箱格式验证，包含@符号且@后面有.
        return email.matches("^[^@]+@[^@]+\\.[^@]+$");
    }

    /**
     * 验证验证码是否有效
     *
     * @param code 验证码
     * @param email 邮箱
     * @return 验证结果
     */
    @GetMapping("/checkCaptcha")
    @ResponseBody
    @Operation(summary = "验证验证码是否有效", description = "返回验证结果")
    public AjaxResult checkCaptcha(
        @Parameter(description = "验证码") String code, 
        @Parameter(description = "邮箱地址") String email) {
        try {
            int row = userService.queryCaptcha(code, email);
            if (1 > row)
                return AjaxResult.error("无效验证码");
            return AjaxResult.success();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return AjaxResult.error("操作失败,请联系管理员");
        }
    }

    /**
     * 检查用户名和邮箱是否已存在
     *
     * @param username 用户名
     * @param email 邮箱
     * @return 检查结果
     */
    @GetMapping("/checkUser")
    @ResponseBody
    @Operation(summary = "检查用户名和邮箱是否已存在", description = "返回检查结果")
    public AjaxResult checkUser(
        @Parameter(description = "用户名") String username, 
        @Parameter(description = "邮箱地址") String email) {
        try {
            SysUser userName = userService.selectUserByUsername(username);
            SysUser userEmail = userService.selectUserByEmail(email);
            if (!ObjectUtils.isEmpty(userName)) {
                return AjaxResult.error("用户名已存在");
            } else if (!ObjectUtils.isEmpty(userEmail)) {
                return AjaxResult.error("邮箱已注册");
            }
            return AjaxResult.success();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return AjaxResult.error("操作失败,请联系管理员");
        }
    }
}