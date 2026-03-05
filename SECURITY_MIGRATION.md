# Sa-Token 迁移到 Spring Security 文档

## 迁移概述

本次迁移将项目的权限认证框架从 Sa-Token 全面替换为 Spring Security + JWT。

**迁移完成时间**: 2026 年 3 月 4 日

## 主要变更

### 1. 新增文件

#### 注解类
- `com/xiaozhi/security/annotation/AnonymousAccess.java` - 匿名访问注解

#### 配置类
- `com/xiaozhi/security/config/SecurityConfig.java` - Spring Security 配置
- `com/xiaozhi/security/AnonymousAccessManager.java` - 匿名访问路径管理器

#### JWT 相关
- `com/xiaozhi/security/jwt/JwtTokenProvider.java` - JWT 工具类
- `com/xiaozhi/security/filter/JwtAuthenticationFilter.java` - JWT 认证过滤器

#### 服务类
- `com/xiaozhi/security/CustomUserDetailsService.java` - 用户详情服务
- `com/xiaozhi/security/service/AuthenticationService.java` - 认证服务

#### DTO 类
- `com/xiaozhi/security/dto/LoginResponse.java` - 登录响应 DTO

#### 工具类
- `com/xiaozhi/utils/SpringUtils.java` - Spring 工具类

### 2. 修改的文件

#### Controller
- `UserController.java` - 移除 Sa-Token，使用 Spring Security
- `DeviceController.java` - @SaIgnore → @AnonymousAccess
- `VLChatController.java` - @SaIgnore → @AnonymousAccess

#### 工具类
- `AuthUtils.java` - 使用 Spring Security 替代 Sa-Token

### 3. 需要移除的文件

#### 配置类
- `com/xiaozhi/common/config/SaTokenConfig.java` - 删除此文件

### 4. pom.xml 变更

#### 移除 Sa-Token 依赖
```xml
<!-- 移除以下依赖 -->
<dependency>
    <groupId>cn.dev33</groupId>
    <artifactId>sa-token-redis-jackson</artifactId>
    <version>1.39.0</version>
</dependency>
<dependency>
    <groupId>cn.dev33</groupId>
    <artifactId>sa-token-jwt</artifactId>
    <version>1.39.0</version>
</dependency>
```

### 5. 注解映射

| Sa-Token | Spring Security |
|----------|----------------|
| @SaIgnore | @AnonymousAccess |
| StpUtil.login() | JwtTokenProvider.generateToken() |
| StpUtil.logout() | SecurityContextHolder.clearContext() |
| StpUtil.isLogin() | SecurityContextHolder.getContext().getAuthentication() != null |
| StpUtil.getLoginId() | AuthUtils.getCurrentUserId() |
| StpUtil.getTokenValue() | 从请求头 Authorization: Bearer 获取 |

### 6. 配置说明

#### application.yml 配置项
```yaml
jwt:
  secret: XiaoZhiESP32ServerSecretKey2026  # JWT 签名密钥
  expiration: 2592000000  # Token 过期时间（毫秒），默认 30 天
```

#### 匿名访问接口
以下接口无需登录即可访问：
- `/api/user/login` - 用户登录
- `/api/user/register` - 用户注册
- `/api/user/tel-login` - 手机号登录
- `/api/user/wx-login` - 微信登录
- `/api/user/resetPassword` - 重置密码
- `/api/user/sendEmailCaptcha` - 发送邮箱验证码
- `/api/user/sendSmsCaptcha` - 发送短信验证码
- `/api/user/checkCaptcha` - 验证验证码
- `/api/user/checkUser` - 检查用户是否存在
- `/api/user/check-token` - 检查 Token 有效性
- `/api/device/ota/**` - OTA 相关接口
- `/api/vl/chat` - 视觉对话
- `/api/chat/**` - 聊天接口
- `/ws/**` - WebSocket 接口

### 7. 认证流程

#### 登录流程
1. 客户端发送登录请求（用户名/密码）
2. 服务端验证 credentials
3. 验证通过，生成 JWT Token
4. 返回 Token 给客户端

#### 认证流程
1. 客户端在请求头携带 Token：`Authorization: Bearer <token>`
2. JwtAuthenticationFilter 拦截请求
3. 解析并验证 Token
4. 验证通过，设置 Authentication 到 SecurityContextHolder
5. 后续业务逻辑可通过 SecurityContextHolder 获取用户信息

### 8. 使用示例

#### 获取当前登录用户
```java
// 方式 1：使用 AuthUtils
Integer userId = AuthUtils.getCurrentUserId();
SysUser user = AuthUtils.getCurrentUser();

// 方式 2：使用 SecurityContextHolder
Authentication auth = SecurityContextHolder.getContext().getAuthentication();
if (auth != null && auth.getPrincipal() instanceof UserDetails) {
    String username = ((UserDetails) auth.getPrincipal()).getUsername();
}
```

#### 标记匿名访问接口
```java
@AnonymousAccess
@PostMapping("/login")
public ResultMessage login(@RequestBody LoginParam param) {
    // ...
}
```

#### 需要认证的接口
```java
// 无需额外注解，默认所有接口都需要认证
@GetMapping("/profile")
public ResultMessage getProfile() {
    Integer userId = AuthUtils.getCurrentUserId();
    // ...
}
```

## 测试验证

### 1. 登录测试
```bash
curl -X POST http://localhost:8080/api/user/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"123456"}'
```

### 2. 认证接口测试
```bash
curl -X GET http://localhost:8080/api/user/profile \
  -H "Authorization: Bearer <token>"
```

### 3. 匿名接口测试
```bash
curl -X GET http://localhost:8080/api/user/checkUser?username=test
# 无需 Token 即可访问
```

## 注意事项

1. **Token 格式**: 使用 `Authorization: Bearer <token>` 格式传递 Token
2. **Token 过期**: Token 过期时间为 30 天，过期后需要重新登录
3. **CORS 配置**: 已配置允许所有来源，生产环境应指定具体域名
4. **密码加密**: 使用 BCrypt 加密，与 Sa-Token 的 MD5 不同
5. **权限检查**: 原有权限逻辑保持不变，通过角色 ID 进行权限控制

## 回滚方案

如需回滚到 Sa-Token 版本：
1. 恢复 `SaTokenConfig.java`
2. 恢复 `pom.xml` 中的 Sa-Token 依赖
3. 恢复 Controller 中的 `@SaIgnore` 注解
4. 恢复 `AuthUtils.java` 中的 Sa-Token 调用
