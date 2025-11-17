package com.xiaozhi.common.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaozhi.common.web.ResultMessage;
import com.xiaozhi.common.web.ResultStatus;
import com.xiaozhi.entity.SysUser;
import com.xiaozhi.service.SysUserService;
import com.xiaozhi.utils.CmsUtils;
import com.xiaozhi.utils.JwtUtil;
import jakarta.annotation.Resource;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

/**
 * 认证拦截器
 */
@Component
public class AuthenticationInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationInterceptor.class);

    @Resource
    private SysUserService userService;

    @Resource
    private JwtUtil jwtUtil;

    @Resource
    private ObjectMapper objectMapper;

    // 公共路径，不需要登录即可访问
    private static final List<String> PUBLIC_PATHS = Arrays.asList(
            "/api/user/",
            "/api/device/ota",
            "/audio/",
            "/uploads/",
            "/ws/");

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();
        
        // 检查是否是公共路径或有@UnLogin注解
        if (isPublicPath(path) || hasUnLoginAnnotation(handler)) {
            return true;
        }

        // 首先检查session中是否已有用户信息
        HttpSession session = request.getSession(false);
        SysUser sessionUser = null;

        if (session != null) {
            sessionUser = (SysUser) session.getAttribute(SysUserService.USER_SESSIONKEY);
            if (sessionUser != null) {
                // 如果session中已有用户，直接设置到请求属性中并放行
                request.setAttribute(CmsUtils.USER_ATTRIBUTE_KEY, sessionUser);
                CmsUtils.setUser(request, sessionUser);
                return true;
            }
        }

        // 尝试从Cookie中获取用户名
        if (tryAuthenticateWithCookies(request, response)) {
            return true;
        }
        
        // 尝试从微信登录信息中获取用户
        if (tryAuthenticateWithWechat(request, response)) {
            return true;
        }
        
        // 尝试从token中获取用户
        if (tryAuthenticateWithToken(request, response)) {
            return true;
        }

        // 处理未授权的请求
        handleUnauthorized(request, response);
        return false;
    }

    /**
     * 尝试使用Cookie进行认证
     */
    private boolean tryAuthenticateWithCookies(HttpServletRequest request, HttpServletResponse response) {
        // 检查是否有username cookie
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("username".equals(cookie.getName())) {
                    String username = cookie.getValue();
                    if (StringUtils.isNotBlank(username)) {
                        SysUser user = userService.selectUserByUsername(username);
                        if (user != null) {
                            // 将用户存储在会话和请求属性中
                            HttpSession session = request.getSession(true);
                            session.setAttribute(SysUserService.USER_SESSIONKEY, user);
                            request.setAttribute(CmsUtils.USER_ATTRIBUTE_KEY, user);
                            CmsUtils.setUser(request, user);
                            return true;
                        }
                    }
                    break;
                }
            }
        }
        return false;
    }

    /**
     * 尝试从请求头中获取token并进行认证
     */
    private boolean tryAuthenticateWithToken(HttpServletRequest request, HttpServletResponse response) {
        // 从请求头获取token
        String authHeader = request.getHeader("Authorization");
        String token = null;
        if (StringUtils.isNotBlank(authHeader) && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7); // 去掉"Bearer "前缀
        }
        
        // 如果没有Authorization头，尝试从自定义头获取
        if (StringUtils.isBlank(token)) {
            token = request.getHeader("X-WX-Token");
        }

        if (StringUtils.isNotBlank(token)) {
            try {
                // 验证token
                if (jwtUtil.validateToken(token)) {
                    // 从token中获取用户ID
                    Integer userId = jwtUtil.getUserIdFromToken(token);
                    
                    // 从session中查找用户，避免重复查询数据库
                    HttpSession session = request.getSession(true);
                    SysUser user = (SysUser) session.getAttribute(SysUserService.USER_SESSIONKEY);

                    // 如果session中没有用户或者用户ID不匹配，才查询数据库
                    if (user == null || !userId.equals(user.getUserId())) {
                        user = userService.selectUserByUserId(userId);
                        
                        if (user != null) {
                            // 将用户存储在会话中，避免下次请求再查询数据库
                            session.setAttribute(SysUserService.USER_SESSIONKEY, user);
                        } else {
                            // 用户不存在，返回false
                            return false;
                        }
                    }
                    
                    // 将用户信息设置到请求属性中
                    request.setAttribute(CmsUtils.USER_ATTRIBUTE_KEY, user);
                    CmsUtils.setUser(request, user);
                    
                    // 如果token中包含微信信息，也存入session
                    try {
                        String wxOpenId = (String) jwtUtil.getClaimFromToken(token, "wx_openid");
                        String wxSessionKey = (String) jwtUtil.getClaimFromToken(token, "wx_session_key");
                        
                        if (StringUtils.isNotBlank(wxOpenId)) {
                            session.setAttribute("wx_openid", wxOpenId);
                        }
                        
                        if (StringUtils.isNotBlank(wxSessionKey)) {
                            session.setAttribute("wx_session_key", wxSessionKey);
                        }
                    } catch (Exception e) {
                        // 忽略微信信息获取失败的情况
                        logger.debug("从token获取微信信息失败", e);
                    }
                    
                    return true;
                }
            } catch (Exception e) {
                logger.error("Token验证失败", e);
            }
        }
        
        return false;
    }
    
    /**
     * 尝试使用微信登录信息进行认证
     */
    private boolean tryAuthenticateWithWechat(HttpServletRequest request, HttpServletResponse response) {
        // 从请求头或Cookie中获取微信登录凭证
        String wxOpenId = getWechatOpenId(request);
        String wxSessionKey = getWechatSessionKey(request);
        
        if (StringUtils.isNotBlank(wxOpenId)) {
            // 检查session中是否已有与此openid关联的用户
            HttpSession session = request.getSession(true);
            SysUser sessionUser = (SysUser) session.getAttribute(SysUserService.USER_SESSIONKEY);
            
            // 如果session中已有用户，且有相同的openid，直接使用
            if (sessionUser != null && wxOpenId.equals(sessionUser.getWxOpenId())) {
                request.setAttribute(CmsUtils.USER_ATTRIBUTE_KEY, sessionUser);
                CmsUtils.setUser(request, sessionUser);
                return true;
            }
            
            // 否则查询数据库
            SysUser user = userService.selectUserByWxOpenId(wxOpenId);
            
            if (user != null) {
                // 将用户存储在会话和请求属性中
                session.setAttribute(SysUserService.USER_SESSIONKEY, user);
                request.setAttribute(CmsUtils.USER_ATTRIBUTE_KEY, user);
                CmsUtils.setUser(request, user);
                
                // 将微信会话信息也存入session
                session.setAttribute("wx_session_key", wxSessionKey);
                session.setAttribute("wx_openid", wxOpenId);
                
                return true;
            }
        }
        return false;
    }
    
    /**
     * 从请求中获取微信OpenID
     */
    private String getWechatOpenId(HttpServletRequest request) {
        // 首先尝试从请求头获取
        String openId = request.getHeader("X-WX-OPENID");
        
        // 如果请求头中没有，尝试从Cookie中获取
        if (StringUtils.isBlank(openId) && request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("wx_openid".equals(cookie.getName())) {
                    openId = cookie.getValue();
                    break;
                }
            }
        }
        
        // 如果Cookie中没有，尝试从请求参数中获取
        if (StringUtils.isBlank(openId)) {
            openId = request.getParameter("wxOpenId");
        }
        
        // 最后尝试从session中获取
        if (StringUtils.isBlank(openId)) {
            HttpSession session = request.getSession(false);
            if (session != null) {
                Object sessionOpenId = session.getAttribute("wx_openid");
                if (sessionOpenId != null) {
                    openId = sessionOpenId.toString();
                }
            }
        }
        
        return openId;
    }
    
    /**
     * 从请求中获取微信SessionKey
     */
    private String getWechatSessionKey(HttpServletRequest request) {
        // 首先尝试从请求头获取
        String sessionKey = request.getHeader("X-WX-SESSION-KEY");
        
        // 如果请求头中没有，尝试从Cookie中获取
        if (StringUtils.isBlank(sessionKey) && request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("wx_session_key".equals(cookie.getName())) {
                    sessionKey = cookie.getValue();
                    break;
                }
            }
        }
        
        // 如果Cookie中没有，尝试从请求参数中获取
        if (StringUtils.isBlank(sessionKey)) {
            sessionKey = request.getParameter("wxSessionKey");
        }
        
        // 最后尝试从session中获取
        if (StringUtils.isBlank(sessionKey)) {
            HttpSession session = request.getSession(false);
            if (session != null) {
                Object sessionKeyObj = session.getAttribute("wx_session_key");
                if (sessionKeyObj != null) {
                    sessionKey = sessionKeyObj.toString();
                }
            }
        }
        
        return sessionKey;
    }

    /**
     * 处理未授权的请求
     */
    private void handleUnauthorized(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // 检查是否是AJAX请求
        String ajaxTag = request.getHeader("Request-By");
        String head = request.getHeader("X-Requested-With");

        if ((ajaxTag != null && ajaxTag.trim().equalsIgnoreCase("Ext"))
                || (head != null && !head.equalsIgnoreCase("XMLHttpRequest"))) {
            response.addHeader("_timeout", "true");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        } else {
            // 返回JSON格式的错误信息
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");

            ResultMessage result = ResultMessage.error(ResultStatus.UNAUTHORIZED, "用户未登录或登录已过期");
            try {
                objectMapper.writeValue(response.getOutputStream(), result);
            } catch (Exception e) {
                logger.error("写入响应失败", e);
                throw e;
            }
        }
    }

    /**
     * 检查是否是公共路径
     */
    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    /**
     * 检查处理器是否有@UnLogin注解
     */
    private boolean hasUnLoginAnnotation(Object handler) {
        if (!(handler instanceof HandlerMethod)) {
            return false;
        }

        HandlerMethod handlerMethod = (HandlerMethod) handler;
        Method method = handlerMethod.getMethod();
        Class<?> controllerClass = handlerMethod.getBeanType();

        // 检查方法上是否有@UnLogin注解
        UnLogin methodAnnotation = method.getAnnotation(UnLogin.class);
        if (methodAnnotation != null && methodAnnotation.value()) {
            return true;
        }

        // 检查类上是否有@UnLogin注解
        UnLogin classAnnotation = controllerClass.getAnnotation(UnLogin.class);
        if (classAnnotation != null && classAnnotation.value()) {
            return true;
        }

        return false;
    }
}