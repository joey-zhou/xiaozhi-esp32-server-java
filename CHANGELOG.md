# 变更日志
## [3.0.0] - 2025-11-01

### 💥 重大变更
- **feat: 前端架构全面升级到 Vue3** 🎉
  - 完整迁移到 Vue 3.5.22 + Composition API
  - 使用 Vite 7 作为构建工具，提升开发体验和构建速度
  - 采用 TypeScript 5.9 增强类型安全
  - 状态管理升级到 Pinia 3
  - 路由升级到 Vue Router 4
  - 采用 Composables 模式重构代码，提高可复用性

- **feat: 后端架构全面升级与重构** 🚀
  - 引入 JWT 认证机制，增强安全性
  - 新增统一结果封装 (ResultMessage/ResultStatus)
  - 新增事件驱动架构 (ChatSessionOpenEvent、ChatAbortEvent 等)
  - 新增完整的权限管理系统 (RBAC)
  - Controller 层全面重构，代码结构更清晰

### 新增功能

#### 前端
- feat: 升级 Node.js 运行时到 v22
- feat: 引入现代化开发工具链
  - 使用 oxlint 和 ESLint 9 进行代码检查
  - 集成 Vue DevTools 8 用于调试
  - 采用 Prettier 3.6 统一代码风格
- feat: UI 组件库升级到 Ant Design Vue 4.2.6
- feat: 新增 @vueuse/core 工具库，提供丰富的组合式 API
- feat: 新增全局加载组件和错误边界
- feat: 新增浮动聊天组件，优化交互体验

#### 后端核心功能
- feat: 新增 JWT 认证系统 (JwtUtil)
  - 支持 Token 生成和刷新
  - 支持微信登录 Token
  - 支持自定义 claims
- feat: 新增微信登录服务 (WxLoginService)
- feat: 新增权限管理系统
  - 角色权限映射 (SysAuthRole, SysPermission, SysRolePermission)
  - 完整的 RBAC 权限控制
- feat: 新增验证码工具 (CaptchaUtils)
- feat: 新增邮件工具 (EmailUtils)
- feat: 新增短信服务 (SmsUtils)
- feat: 新增文件哈希工具 (FileHashUtil)
- feat: 新增音频增强工具 (AudioEnhancer)

#### AI & LLM
- feat: 新增 OpenAI LLM 服务 (OpenAiLlmService)
  - 支持流式响应
  - 支持深度思考模式
  - 支持 Function Calling
  - 新增 Token 回调机制
- feat: 新增 MCP (Model Context Protocol) 支持
  - MCP Session 管理
  - MCP 设备服务集成
- feat: 增强对话服务 (DialogueService)
  - 优化会话管理
  - 改进消息处理流程
  - 支持事件驱动
- feat: VAD 服务重大重构
  - 优化语音活动检测
  - 改进 Silero VAD 模型
  - 新增高级参数配置

#### 依赖更新
- update: 阿里云 SDK 全面升级
  - nls-sdk-transcriber: 2.2.1 → 2.2.18
  - nls-sdk-tts: 2.2.17 → 2.2.18
  - dashscope-sdk-java: 2.20.2 → 2.20.6
  - 新增阿里云短信服务 SDK 2.0.24
- update: Spring Boot 依赖更新
  - 新增 spring-boot-starter-data-redis (缓存增强)
  - spring-ai-starter-mcp-client 集成
- update: commons-io: 2.11.0 → 2.18.0
- update: okhttp: 5.0.0-alpha.14 → 4.9.3 (提升稳定性)
- update: 新增 okio 3.13.0

### 优化与改进

#### 前端优化
- perf: Vite 开发服务器性能大幅提升
- perf: 生产构建体积优化和加载速度提升
- perf: 优化路由守卫和权限检查
- update: Docker 镜像更新到 node:22-alpine
- update: 依赖包全面更新到最新稳定版本
- update: 优化开发环境配置和热更新机制
- dx: 更好的 TypeScript 类型推导和提示
- dx: 更快的热模块替换 (HMR)

#### 后端优化
- refactor: 全局异常处理增强 (GlobalExceptionHandler)
  - 新增资源未找到异常 (ResourceNotFoundException)
  - 新增未授权异常 (UnauthorizedException)
  - 统一异常响应格式
- refactor: 认证拦截器重构 (AuthenticationInterceptor)
  - 支持 JWT 认证
  - 优化权限验证逻辑
- refactor: 会话管理重构 (SessionManager)
  - 改进会话生命周期管理
  - 优化并发处理
- refactor: 消息处理器重构 (MessageHandler)
  - 优化消息流转
  - 改进错误处理
- refactor: WebSocket 处理器优化 (WebSocketHandler)
  - 增强连接管理
  - 改进异常处理
- refactor: 对话记忆系统优化
  - DatabaseChatMemory 重构
  - MessageWindowConversation 改进
  - Conversation 接口优化
- refactor: LLM 工具调用优化
  - ToolsGlobalRegistry 改进
  - XiaoZhiToolCallingManager 重构
  - 新增 NewChatFunction
- refactor: STT 服务优化
  - 所有 STT 提供商代码优化
  - 改进错误处理和日志
- refactor: 实体类优化
  - SysConfig, SysDevice, SysMessage, SysUser 改进
- refactor: Mapper XML 优化
  - 所有 Mapper 文件重构
  - SQL 优化
- refactor: Service 层全面重构
  - 新增事务配置 (TransactionConfig)
  - 优化业务逻辑
  - 改进数据访问层

### Docker 更新
- update: docker-compose.yml 配置优化
  - 改进服务依赖关系
  - 优化健康检查
  - 增强网络配置
- update: Dockerfile-node 升级到 Node 22

---

## [2.8.17] - 2025-07-16
### 新增
- feat: 新增 Swagger
- update: 模型增加辨识度标签
- update: 删除全局聊天多余缩小按钮
- update: 优化展示样式，可以切换浏览器标签页样式
- update: 实体采用 Lombok 方法
### 修复
- fix: 修复地址错误问题
- fix: 修复添加设备时验证码未生效问题
- fix: 修复 init SQL 脚本初始化缺少字段问题
- fix: 修复 issues #119 #120
### 样式优化
- style: 更新全局聊天缩放动画，更接近苹果效果
- 优化: 聊天样式
### 删除
- delete: 删除无用 log
### 重构
- refactor(stt): 优化 VoskSttService 类的代码结构
- refactor: 去掉多余 log

# 变更日志
## [2.8.16] - 2025-07-02
### 其他变更
- refactor:vad重构，去除agc
- refactor:重构音频发送逻辑，按照实际帧位置发送

# 变更日志
## [2.8.15] - 2025-07-01

### 修复
- fix:修复tag更新错误问题
- fix:修复设备在聆听时，修改角色配置导致缓存更新时多次查询数据库的问题
- fix:修复init初始化确实头像字段

### 其他变更
- refactor:优化token缓存，减少冗余代码
- update:阿里巴巴sdk日志级别改为warn

## [2.8.0] - 2025-06-15

### 新功能
- feat:增加logback输入 close #37
- feat:新增橘色设备量展示

### 修复
- fix(stt.aliyun): do not reuse recognizer
- fix(stt.aliyun): support long speech recognition
- fix: memory leak. Should clean up dialogue info after session closed

### 其他变更
- chore: update version to 2.8.0 [skip ci]
- update:角色返回增加modelName
- docs: update changelog for v2.7.68 [skip ci]
- chore: update version to 2.7.68 [skip ci]
- docs: update changelog for v2.7.67 [skip ci]
- chore: update version to 2.7.67 [skip ci]
- docs: update changelog for v2.7.66 [skip ci]
- chore: update version to 2.7.66 [skip ci]
- refactor(stt): simplify SttServiceFactory

## [2.7.68] - 2025-06-14

### 修复
- fix(stt.aliyun): do not reuse recognizer
- fix(stt.aliyun): support long speech recognition
- fix: memory leak. Should clean up dialogue info after session closed

### 其他变更
- chore: update version to 2.7.68 [skip ci]
- docs: update changelog for v2.7.67 [skip ci]
- chore: update version to 2.7.67 [skip ci]
- docs: update changelog for v2.7.66 [skip ci]
- chore: update version to 2.7.66 [skip ci]
- refactor(stt): simplify SttServiceFactory

## [2.7.67] - 2025-06-14

### 修复
- fix: memory leak. Should clean up dialogue info after session closed

### 其他变更
- chore: update version to 2.7.67 [skip ci]
- docs: update changelog for v2.7.66 [skip ci]
- chore: update version to 2.7.66 [skip ci]

## [2.7.64] - 2025-06-12

### 修复
- Merge pull request #98 from vritser/main
- fix(audio): merge audio files

### 其他变更
- chore: update version to 2.7.64 [skip ci]
- docs: update changelog for v2.7.63 [skip ci]
- chore: update version to 2.7.63 [skip ci]

## [2.7.60] - 2025-06-11

### 新功能
- Merge pull request #96 from vritser/main
- feat(tts): support minimax t2a

### 修复
- fix:修复阿里语音合成多余参数，删除
- fix(tts): tts service factory

### 其他变更
- chore: update version to 2.7.60 [skip ci]
- docs: update changelog for v2.7.59 [skip ci]
- chore: update version to 2.7.59 [skip ci]
- refactor(tts): add default implements
- docs: update changelog for v2.7.58 [skip ci]
- chore: update version to 2.7.58 [skip ci]

## [2.7.59] - 2025-06-11

### 新功能
- Merge pull request #96 from vritser/main
- feat(tts): support minimax t2a

### 修复
- fix(tts): tts service factory

### 其他变更
- chore: update version to 2.7.59 [skip ci]
- refactor(tts): add default implements
- docs: update changelog for v2.7.58 [skip ci]
- chore: update version to 2.7.58 [skip ci]

