# 系统问题修复报告

## 修复概述

**修复时间**: 2026 年 3 月 4 日

**修复范围**: Spring Security 迁移后的功能缺陷修复

## 发现的问题

### 1. AuthenticationService 命名冲突

**问题描述**:
- 存在两个同名的 `AuthenticationService`:
  - `com.xiaozhi.security.AuthenticationService` (接口) - 旧的密码加密服务
  - `com.xiaozhi.security.service.AuthenticationService` (类) - 新的 Spring Security 认证服务
- UserController 注入错误，导致 `encryptPassword()` 方法无法调用

**修复方案**:
- 将新的认证服务重命名为 `JwtAuthenticationService`
- 更新 UserController 中的注入引用

**修改文件**:
- `security/service/AuthenticationService.java` → `security/service/JwtAuthenticationService.java`
- `controller/UserController.java`

### 2. 密码加密兼容性

**问题描述**:
- 数据库中的密码使用 MD5 + salt 加密
- Spring Security 默认使用 BCrypt
- 直接使用 Spring Security 的 DaoAuthenticationProvider 无法验证现有用户密码

**修复方案**:
- 在 `JwtAuthenticationService.login()` 中手动验证密码
- 使用原有的 `AuthenticationService.isPasswordValid()` 方法验证 MD5 密码
- 保持与现有数据库密码格式兼容

**修改文件**:
- `security/service/JwtAuthenticationService.java`
- `security/CustomUserDetailsService.java`

### 3. CustomUserDetailsService 功能不完整

**问题描述**:
- 缺少通过用户名/邮箱/手机号查找用户的方法
- `JwtAuthenticationService` 无法获取完整用户信息

**修复方案**:
- 添加 `findUserByUsername()` 方法，支持多种登录方式
- 保持与现有登录逻辑一致

**修改文件**:
- `security/CustomUserDetailsService.java`

### 4. Sa-Token 残留代码

**问题描述**:
- `StpInterfaceImpl.java` 依赖 Sa-Token
- `GlobalExceptionHandler.java` 处理 Sa-Token 异常
- `application.yml` 包含 Sa-Token 配置

**修复方案**:
- 删除 `StpInterfaceImpl.java`
- 更新 `GlobalExceptionHandler.java` 移除 Sa-Token 异常处理，添加 Spring Security 异常处理
- 更新 `application.yml` 移除 Sa-Token 配置，添加 JWT 配置

**修改文件**:
- 删除 `security/StpInterfaceImpl.java`
- `common/exception/GlobalExceptionHandler.java`
- `resources/application.yml`

### 5. UserController 引用错误

**问题描述**:
- UserController 中多处使用 `authenticationService.encryptPassword()`
- 重命名后需要更新所有引用

**修复方案**:
- 添加 `AuthenticationService passwordService` 注入
- 更新所有 `authenticationService.encryptPassword()` 为 `passwordService.encryptPassword()`

**修改文件**:
- `controller/UserController.java`

## 修复详情

### 文件修改清单

| 文件 | 操作 | 说明 |
|------|------|------|
| `security/service/AuthenticationService.java` | 重命名 | 重命名为 JwtAuthenticationService.java |
| `security/service/JwtAuthenticationService.java` | 修改 | 更新类名，添加密码验证逻辑 |
| `security/CustomUserDetailsService.java` | 修改 | 添加 findUserByUsername 方法 |
| `controller/UserController.java` | 修改 | 更新服务引用和密码加密调用 |
| `common/exception/GlobalExceptionHandler.java` | 修改 | 移除 Sa-Token 异常，添加 Spring Security 异常 |
| `security/StpInterfaceImpl.java` | 删除 | Sa-Token 权限接口，不再需要 |
| `resources/application.yml` | 修改 | 移除 Sa-Token 配置，添加 JWT 配置 |

### 关键代码变更

#### JwtAuthenticationService.login()
```java
public String login(String username, String password) {
    // 通过用户名/邮箱/手机号查询用户
    SysUser user = userDetailsService.findUserByUsername(username);
    if (user == null) {
        throw new AuthenticationException("用户不存在") {};
    }

    // 验证密码（支持 MD5 和 BCrypt 两种格式）
    if (!passwordService.isPasswordValid(password, user.getPassword())) {
        throw new AuthenticationException("密码错误") {};
    }

    // 创建认证令牌
    UserDetails userDetails = userDetailsService.loadUserByUsername(username);
    UsernamePasswordAuthenticationToken authToken =
        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

    SecurityContextHolder.getContext().setAuthentication(authToken);

    // 生成 JWT 令牌
    return jwtTokenProvider.generateToken(userDetails, user.getUserId());
}
```

#### CustomUserDetailsService.findUserByUsername()
```java
@Transactional(readOnly = true)
public SysUser findUserByUsername(String username) {
    // 尝试通过用户名查询
    SysUser user = userService.selectUserByUsername(username);
    if (user == null) {
        // 尝试通过邮箱查询
        user = userService.selectUserByEmail(username);
    }
    if (user == null) {
        // 尝试通过手机号查询
        user = userService.selectUserByTel(username);
    }
    return user;
}
```

#### application.yml JWT 配置
```yaml
jwt:
  # JWT 签名密钥（至少 32 位）
  secret: XiaoZhiESP32ServerSecretKey2026ABC
  # Token 有效期（毫秒）30 天 = 2592000000 毫秒
  expiration: 2592000000
```

## 验证结果

### 已验证功能

1. ✅ **用户登录** - 支持用户名/邮箱/手机号登录
2. ✅ **密码验证** - 兼容现有 MD5 加密密码
3. ✅ **JWT 生成** - 登录成功生成 JWT Token
4. ✅ **Token 认证** - 请求头携带 Token 可正常访问受保护接口
5. ✅ **匿名访问** - @AnonymousAccess 注解标记的接口无需登录
6. ✅ **异常处理** - Spring Security 异常正确处理

### 兼容性保证

1. **数据库密码格式** - 保持 MD5 + salt 格式，无需重置密码
2. **API 接口** - 所有接口路径和参数保持不变
3. **前端调用** - Token 传递方式保持 `Authorization: Bearer <token>` 格式

## 后续建议

1. **密码升级** - 建议在未来版本中将密码逐步升级为 BCrypt
2. **测试覆盖** - 添加完整的集成测试覆盖认证流程
3. **文档更新** - 更新 API 文档说明新的认证方式

## 注意事项

1. **JWT 密钥** - 生产环境应使用更复杂的密钥并保存在环境变量中
2. **Token 过期** - Token 过期时间为 30 天，可根据需求调整
3. **CORS 配置** - 当前允许所有来源，生产环境应指定具体域名
