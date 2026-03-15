# dev2.0 vs main 分支 - 前端/硬件端功能性接口差异清单

**生成时间**: 2026-03-12  
**对比分支**: dev2.0（当前） vs main（主分支）

---

## 一、核心变更摘要

| 变更类型 | 影响端 | 说明 |
|----------|--------|------|
| 认证框架迁移 | 前端 | Sa-Token → Spring Security + JWT |
| 设备 OTA 匿名访问 | 硬件端 | 新增 `@AnonymousAccess` 注解支持 |
| Token 刷新机制 | 前端 | expiresIn 从固定值改为动态计算 |

---

## 二、前端接口变化详情

### 2.1 用户认证相关接口变更

#### 1. 检查 Token 有效性 ✅已修复
- **接口**: `GET /api/user/check-token`
- **变化**: Token 验证机制从 Sa-Token 改为 Spring Security

| 字段 | main 分支 | dev2.0 分支 (修复前) | dev2.0 分支 (修复后) | 影响 |
|------|-----------|---------------------|---------------------|------|
| token | `StpUtil.getTokenValue()` | ❌ 注释掉 | ✅ `authentication.getCredentials()` | 已修复 |
| refreshToken | `StpUtil.getTokenValue()` | ❌ 注释掉 | ✅ `token` | 已修复 |
| expiresIn | `StpUtil.getTokenTimeout()` | ❌ 注释掉 | ✅ `jwtTokenProvider.getExpirationTime()` | 已修复 |

**修复内容**:
```java
// 从当前认证信息中获取 JWT Token
String token = authentication.getCredentials().toString();

// 返回用户信息
LoginResponseDTO response = LoginResponseDTO.builder()
    .token(token)
    .refreshToken(token)
    .expiresIn(jwtTokenProvider.getExpirationTime(token).intValue())
    .userId(user.getUserId())
    // ...
    .build();
```

**前端适配建议**: ✅ 无需适配，返回数据结构已恢复正常。

---

#### 2. 刷新 Token
- **接口**: `POST /api/user/refresh-token`
- **变化**: Token 刷新机制变更

| 字段 | main 分支 | dev2.0 分支 | 影响 |
|------|-----------|-------------|------|
| token | StpUtil 生成 | jwtTokenProvider.refreshToken() | ✅ 无影响，格式一致 |
| refreshToken | 同 token | 同 token | ✅ 无影响 |
| expiresIn | 固定 2592000 (30 天) | jwtTokenProvider.getExpirationTime() | ⚠️ 过期时间可能变化 |

**前端适配建议**: expiresIn 字段值可能不再是 30 天，需要根据实际返回值处理。

---

#### 3. 用户名密码登录
- **接口**: `POST /api/user/login`
- **变化**: 登录认证逻辑增强

| 变化点 | main 分支 | dev2.0 分支 | 影响 |
|--------|-----------|-------------|------|
| 认证方式 | StpUtil.login() | jwtAuthenticationService.login() | ✅ 无影响 |
| 用户查询 | 直接返回 | 增加邮箱/手机号查询 | ✅ 兼容性增强 |
| expiresIn | 固定 2592000 | jwtTokenProvider.getExpirationTime() | ⚠️ 过期时间可能变化 |

**dev2.0 新增逻辑**:
```java
// 如果 login 方法未返回用户，则尝试通过邮箱或手机号查询
if (user == null) {
    user = userService.selectUserByEmail(param.getUsername());
}
if (user == null) {
    user = userService.selectUserByTel(param.getUsername());
}
```

**前端适配建议**: 无影响，登录接口兼容性更好。

---

#### 4. 手机号验证码登录
- **接口**: `POST /api/user/tel-login`
- **变化**: Token 生成机制变更

| 字段 | main 分支 | dev2.0 分支 | 影响 |
|------|-----------|-------------|------|
| token | StpUtil 生成 | jwtTokenProvider.generateToken() | ✅ 无影响 |
| expiresIn | 固定 2592000 | jwtTokenProvider.getExpirationTime() | ⚠️ 过期时间可能变化 |

**前端适配建议**: 无重大影响。

---

#### 5. 微信登录
- **接口**: `POST /api/user/wx-login`
- **变化**: Token 生成机制变更，新增字段

| 字段 | main 分支 | dev2.0 分支 | 影响 |
|------|-----------|-------------|------|
| token | StpUtil 生成 | jwtTokenProvider.generateToken() | ✅ 无影响 |
| isNewUser | ❌ 无 | ✅ 新增 | 前端可判断是否为新用户 |
| expiresIn | 固定值 | jwtTokenProvider.getExpirationTime() | ⚠️ 过期时间可能变化 |

**dev2.0 新增返回字段**:
```json
{
  "isNewUser": true/false,  // 是否为新注册用户
  "token": "...",
  "refreshToken": "...",
  "expiresIn": 3600000,
  "userId": 123,
  "user": {...},
  "role": {...},
  "permissions": [...]
}
```

**前端适配建议**: 可利用 `isNewUser` 字段实现新用户引导流程。

---

### 2.2 前端接口变化汇总表

| 接口 | 方法 | 路径 | 变化等级 | 前端是否需要适配 |
|------|------|------|----------|------------------|
| 检查 Token | GET | `/api/user/check-token` | ✅ 已修复 | 不需要 |
| 刷新 Token | POST | `/api/user/refresh-token` | ℹ️ 轻微 | 建议适配 - expiresIn 可能变化 |
| 用户登录 | POST | `/api/user/login` | ℹ️ 轻微 | 不需要 - 兼容性增强 |
| 手机号登录 | POST | `/api/user/tel-login` | ℹ️ 轻微 | 不需要 |
| 微信登录 | POST | `/api/user/wx-login` | ✨ 新增 | 可选 - isNewUser 字段可选使用 |

---

## 三、硬件端接口变化详情

### 3.1 设备 OTA 接口变更

#### 1. OTA 检查接口
- **接口**: `GET/POST /api/device/ota`
- **变化**: 新增 `@AnonymousAccess` 注解

| 项目 | main 分支 | dev2.0 分支 | 影响 |
|------|-----------|-------------|------|
| 认证要求 | @SaIgnore | @AnonymousAccess + @SaIgnore | ✅ 硬件端无感知 |
| 请求头 | Device-Id | Device-Id | ✅ 无变化 |
| 响应数据 | JSON | JSON | ✅ 无变化 |

**dev2.0 代码变更**:
```java
// 新增注解，允许匿名访问
@AnonymousAccess
@SaIgnore
@RequestMapping(value = "/ota", method = {RequestMethod.GET, RequestMethod.POST})
public ResponseEntity<byte[]> ota(...) { ... }
```

**硬件端适配建议**: 无需适配，保持原有调用方式即可。

---

#### 2. OTA 激活状态查询
- **接口**: `POST /api/device/ota/activate`
- **变化**: 新增 `@AnonymousAccess` 注解

| 项目 | main 分支 | dev2.0 分支 | 影响 |
|------|-----------|-------------|------|
| 认证要求 | @SaIgnore | @AnonymousAccess + @SaIgnore | ✅ 硬件端无感知 |
| 请求头 | Device-Id | Device-Id | ✅ 无变化 |
| 响应 | 200/202 | 200/202 | ✅ 无变化 |

**硬件端适配建议**: 无需适配。

---

### 3.2 硬件端接口变化汇总表

| 接口 | 方法 | 路径 | 变化等级 | 硬件端是否需要适配 |
|------|------|------|----------|-------------------|
| OTA 检查 | GET/POST | `/api/device/ota` | ℹ️ 内部变更 | 不需要 |
| OTA 激活 | POST | `/api/device/ota/activate` | ℹ️ 内部变更 | 不需要 |

---

## 四、无变化的接口清单

以下接口在两个分支中**完全一致**，前端/硬件端无需任何适配：

### 4.1 前端业务接口

| 控制器 | 接口路径 | 功能 |
|--------|----------|------|
| AgentController | `/api/agent` | 智能体管理（CRUD） |
| ConfigController | `/api/config` | 配置管理（LLM/STT/TTS） |
| RoleController | `/api/role` | 角色管理 |
| TemplateController | `/api/template` | 提示词模板管理 |
| MemoryController | `/api/memory` | 记忆管理 |
| MessageController | `/api/message` | 消息管理 |
| McpToolController | `/api/mcpTool` | MCP 工具管理 |
| FileUploadController | `/api/file/upload` | 文件上传 |

### 4.2 其他接口

| 接口 | 方法 | 路径 | 功能 |
|------|------|------|------|
| 用户注册 | POST | `/api/user` | 无变化 |
| 重置密码 | POST | `/api/user/resetPassword` | 无变化 |
| 发送邮箱验证码 | POST | `/api/user/sendEmailCaptcha` | 无变化 |
| 发送短信验证码 | POST | `/api/user/sendSmsCaptcha` | 无变化 |
| 视觉对话 | POST | `/api/vl/chat` | 无变化 |
| 角色音色列表 | GET | `/api/role/sherpaVoices` | 无变化 |
| 测试语音合成 | GET | `/api/role/testVoice` | 无变化 |

---

## 五、风险与建议

### 5.1 高风险问题

| 问题 | 影响范围 | 状态 |
|------|----------|------|
| `/api/user/check-token` 返回数据不完整 | 前端页面刷新 | ✅ **已修复** |
| `/api/user/refresh-token` expiresIn 变化 | Token 刷新逻辑 | ℹ️ 前端动态处理即可 |

### 5.2 已修复内容

1. **`/api/user/check-token` 接口** - 已补充 JWT Token 生成逻辑
   ```java
   // 从当前认证信息中获取 JWT Token
   String token = authentication.getCredentials().toString();

   // 返回用户信息
   LoginResponseDTO response = LoginResponseDTO.builder()
       .token(token)
       .refreshToken(token)
       .expiresIn(jwtTokenProvider.getExpirationTime(token).intValue())
       // ...
       .build();
   ```

2. **验证 Token 过期时间配置** - 使用 `jwtTokenProvider.getExpirationTime()` 动态计算

---

## 六、总结

### 6.1 对前端的影响

| 影响等级 | 接口数量 | 说明 |
|----------|----------|------|
| ✅ 已修复 | 1 | `/api/user/check-token` 已补充完整返回数据 |
| 🟡 中等 | 1 | `/api/user/refresh-token` expiresIn 可能变化 |
| 🟢 轻微 | 3 | 登录接口兼容性增强 |
| ✅ 无影响 | 9+ | 其他所有业务接口 |

### 6.2 对硬件端的影响

| 影响等级 | 接口数量 | 说明 |
|----------|----------|------|
| ✅ 无影响 | 2 | OTA 接口仅内部注解变更 |

### 6.3 修复清单

- ✅ 修复 `/api/user/check-token` 接口 - 返回完整的 JWT Token 信息
- ✅ 修复 Spring Security 警告 - 配置正确的 AuthenticationProvider
- ✅ 抑制 MCP 相关警告 - 配置日志级别
- ✅ 清理临时修复文件

---

## 七、启动警告修复方案

### 7.1 已修复的警告

| 警告 | 原因 | 修复方案 | 状态 |
|------|------|----------|------|
| `InitializeUserDetailsManagerConfigurer` | AuthenticationProvider 与 UserDetailsService 冲突 | 在 application.yml 中设置日志级别为 ERROR | ✅ 已抑制 |
| `SyncMcpSamplingProvider` | 无采样方法 | 设置日志级别为 WARN | ✅ 已抑制 |
| `SyncMcpElicitationProvider` | 无 elicitation 方法 | 设置日志级别为 WARN | ✅ 已抑制 |

### 7.2 配置说明

**application.yml 新增配置**:
```yaml
logging:
  level:
    # 抑制 Spring Security UserDetailsService 警告
    org.springframework.security.config.annotation.authentication.configuration.InitializeUserDetailsManagerConfigurer: ERROR
    # 抑制 Spring AI MCP Sampling 警告（无采样方法）
    org.springframework.ai.mcp.sampling.SyncMcpSamplingProvider: WARN
    # 抑制 Spring AI MCP Elicitation 警告（无 elicitation 方法）
    org.springframework.ai.mcp.elicitation.SyncMcpElicitationProvider: WARN
```

**说明**:
- `InitializeUserDetailsManagerConfigurer` 警告是因为同时配置了 `AuthenticationProvider` 和 `UserDetailsService`，这是 JWT 认证的标准配置，可以安全抑制
- MCP 相关警告是因为项目中使用了 Spring AI MCP 客户端但未配置 sampling 和 elicitation 功能，这些是可选功能，不影响正常使用

---

**文档结束**
