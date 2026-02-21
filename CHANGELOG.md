# 变更日志
## [4.1.0] - 2026-02-21

### 💥 重大变更
- **feat: 对话记忆系统引入摘要模式**
  - 新增 SummaryConversation / SummaryConversationFactory，支持对话摘要长期记忆
  - 当上下文消息数达到阈值（默认 8 条）时，通过虚拟线程调用 LLM 异步生成 ≤150 字摘要
  - 摘要自动注入后续对话的系统提示词，实现跨会话记忆延续
  - 新增 ConversationIdentifier 统一标识 deviceId + roleId + sessionId
  - 新增摘要提示词模板：init_summarizer.md（首次摘要）、again_summarizer.md（增量摘要）、system_prompt_with_summary.md（摘要注入）
  - 角色支持选择记忆类型（window / summary），通过 `memoryType` 字段配置
  - 删除 WindowConversationFactory，由 DefaultConversationFactory + SummaryConversationFactory 分别承担

- **feat: 音频播放架构全面重构**
  - 新增 ScheduledPlayer：纳秒精度帧调度、突发预缓冲（前 2 帧立即发送）、句间 300ms 间隔、基于 Flux 的多句排队
  - 新增 PlayerWithOpusFile：播放同时录制 Ogg-Opus 文件，并向 AEC 喂参考信号
  - 新增 Speech 值对象，携带音频数据及对应文本（用于歌词同步等场景）
  - 新增 SentenceHelper：独立的句子分割辅助类，支持中英文标点、表情检测、小数点消歧
  - 新增 ChatConverter 函数式接口，用于 token 流到句子流的转换
  - 删除 FilePlayer、ThreadPlayer、ThreadSynthesizer（由新架构替代）

- **feat: 对话领域模型重构（Persona / Dialogue）**
  - 新增 Persona：封装 ChatModel、Synthesizer、Player、Conversation、SttService 等，统一管理对话全链路
  - 新增 Dialogue：不可变值对象，完整表达一轮对话（UserMessage、ChatResponse、工具调用详情、时间戳等），支持转换为持久化实体
  - DialogueService 大幅精简，核心 LLM/TTS 逻辑下沉到 Persona
  - ChatService 演进为 Persona 工厂，通过 buildPersona() 统一构建对话能力

### 新增功能

#### 服务端 AEC 回声消除
- feat: 新增 AecService，基于 WebRTC AEC3（webrtc-java 0.14.0）实现服务端回声消除
  - 集成回声消除、噪声抑制（MODERATE）、高通滤波、自适应增益控制
  - TTS 播放结束后自动重建 AEC 实例，避免过度抑制后续用户语音
  - 可通过 `aec.enabled`、`aec.stream.delay.ms`、`aec.noise.suppression.level` 配置
- feat: 新增 TtsPlaybackEndEvent 事件，播放停止时通知 AEC 和 VAD 重置状态
- delete: 删除 AudioEnhancer（功能由 AEC 管道中的 AGC 替代）

#### MCP 工具管理
- feat: 新增 McpToolController，提供 MCP 工具的全局/角色级启用禁用管理 API
  - 支持单个工具开关、批量设置角色排除工具列表、全局工具开关
  - 查询角色及全局禁用工具列表、获取系统内置工具列表、刷新缓存
- feat: 新增 McpToolExcludeService 及 Redis 缓存支持
- feat: 新增请求参数类：BatchSetRoleExcludeToolsParam、ToggleGlobalToolStatusParam、ToggleRoleToolStatusParam

#### 记忆管理接口
- feat: 新增 MemoryController，提供摘要记忆的查询与删除 REST API
- feat: 新增 SysSummaryService 及 SummaryMapper 数据访问层
- feat: 前端新增 memory.ts 服务模块，支持摘要记忆和聊天记忆查询/删除

#### 新增 STT 提供商：火山引擎
- feat: 新增 VolcengineSttService，实现火山引擎（字节跳动）WebSocket 流式语音识别
  - 使用自定义二进制协议 + Gzip 压缩 JSON 头 + 音频载荷
  - 支持批量和流式识别模式

#### 新增 TTS 提供商：腾讯云
- feat: 新增 TencentTtsService，实现腾讯云 WebSocket 流式语音合成
  - 支持语音名称、音调、语速配置
- feat: 新增腾讯云音色列表 tencentVoicesList.json

#### 绘本与音乐播放器
- feat: 新增 HuiBenPlayer，替代 HuiBenService，独立绘本播放器
- feat: 新增 MusicPlayer，替代 MusicService，独立音乐播放器
  - 支持 LRC 歌词解析与同步显示，歌词文本随音频帧发送
- delete: 删除 HuiBenService、MusicService（功能整合到新播放器）

#### 事件系统
- feat: 新增 DeviceOnlineEvent，设备上线时触发（用于 OTA 升级结果检查等）
- feat: 新增 TtsPlaybackEndEvent，TTS 播放结束时触发

### 优化与改进

#### Tool Calling 增强
- fix: 修复 Spring AI #4629/#4790 问题——OpenAI 兼容 API（如通义千问）流式返回的工具调用分片合并
  - 新增 mergeFragmentedToolCalls() 方法，按 ID 及空 ID/空名称延续策略合并分片
- fix: 优雅处理 LLM 幻觉工具调用（调用未注册工具名时返回错误信息而非崩溃）
- update: 工具调用详情记录到 ChatSession，支持持久化

#### VAD 重构
- refactor: 简化 VAD 架构，删除 VadDetector 接口、VadSessionState、VadServiceAdapter
- update: VadService 监听 TtsPlaybackEndEvent，TTS 结束后重置 Silero 隐藏状态，消除状态污染

#### 会话管理
- update: ChatSession 新增 toolCallDetails 管理（CopyOnWriteArrayList）
- update: ChatSession 新增 getAudioPath() 方法，统一生成用户 WAV / 助手 Ogg 文件路径
- update: SessionManager 新增 findConversation() 查找活跃会话的 Conversation 实例

#### 前端优化
- update: 记忆管理视图 MemoryManagementView 支持摘要记忆的展示与删除
- update: 角色编辑表单新增 memoryType 选择器（window / summary）
- update: 新增 useSelectLoadMore 组合式函数，支持下拉分页加载
- update: 国际化资源更新，新增记忆类型、MCP 工具管理、新提供商相关翻译
- update: 路由配置新增记忆管理页面
- update: LLM 工厂配置与 Provider 配置更新

#### 其他
- update: Entity 基类支持自动传递
- update: 角色相关参数和 DTO 新增 memoryType 字段
- refactor: 智谱模型提供商 ZhiPuModelProvider 优化
- update: 消息删除新增 MessageDeleteParam 参数类

### 删除
- delete: 删除 AjaxResult、HttpStatus（已被 ResultMessage 替代）
- delete: 删除 FilePlayer、ThreadPlayer、ThreadSynthesizer（已被 ScheduledPlayer 架构替代）
- delete: 删除 HuiBenService、MusicService（已被 HuiBenPlayer、MusicPlayer 替代）
- delete: 删除 VadDetector、VadSessionState、VadServiceAdapter（VAD 架构简化）
- delete: 删除 WindowConversationFactory（由新工厂类替代）
- delete: 删除 AudioEnhancer（由 AEC 替代）
- delete: 删除 docs/images/model.jpg

### 数据库变更
- add: 新增 `sys_summary` 表，存储对话摘要记忆
- add: 新增 `sys_mcp_tool_exclude` 表，存储 MCP 工具排除配置
- update: `sys_role` 表新增 `memoryType` 字段（enum: summary / window，默认 window）
- update: init.sql 同步更新

### 依赖更新
- update: 版本升级至 4.1.0
- update: 新增 `dev.onvoid.webrtc:webrtc-java:0.14.0`（WebRTC AEC3 回声消除）
- update: 新增腾讯云 TTS SDK
- update: pom.xml 依赖更新

---

## [4.0.0] - 2025-12-18

### 💥 重大变更
- **feat: 对话系统全面重构** 🚀
  - LLM 提供商架构升级，引入统一的 ChatModelProvider 接口
  - 新增多个 LLM 提供商支持：星辰、星火、Coze、Dify、Ollama、OpenAI、智谱
  - 重构 ChatModelFactory 和 EmbeddingModelFactory，采用工厂模式统一管理
  - 对话记忆系统全面升级（ChatMemory、Conversation 重构）
  - 新增意图检测功能 (IntentDetector)
  - 优化 Tool Calling 机制和元数据管理
  - 新增星辰大模型流式对话支持 (XingChenChatModel + XingChenClient)
  - 新增星火大模型支持 (XingHuoChatModel)
  - Dify Workflow 集成 (WorkflowRequest)

- **feat: API 接口全面标准化** 📋
  - 集成 Swagger/OpenAPI 文档，提供完整的 API 接口文档
  - 新增统一的请求参数类 (Param)：
    - Agent 相关：AgentAddParam, AgentUpdateParam, AgentDeleteParam
    - Config 相关：ConfigAddParam, ConfigUpdateParam, ConfigGetModelsParam
    - Device 相关：DeviceAddParam, DeviceUpdateParam, DeviceBatchUpdateParam, DeviceDeleteParam
    - User 相关：LoginParam, RegisterParam, ResetPasswordParam, TelLoginParam, SendCaptchaParam, UserUpdateParam
    - Role 相关：RoleAddParam, RoleUpdateParam
    - Template 相关：TemplateAddParam, TemplateUpdateParam
    - 其他：TestVoiceParam
  - 新增统一的响应数据类 (DTO)：
    - AgentDTO, ConfigDTO, DeviceDTO, MessageDTO, RoleDTO, TemplateDTO, UserDTO
    - LoginResponseDTO, PermissionDTO
  - 引入 DtoConverter 统一处理实体与 DTO 转换
  - 所有 Controller 层全面重构，采用 Param/DTO 模式

- **feat: 缓存架构升级** ⚡
  - 新增 Redis 缓存配置 (RedisCacheConfig)
  - 新增布隆过滤器管理器 (BloomFilterManager)
  - 新增缓存辅助工具类 (CacheHelper)
  - 引入 Redisson 分布式锁支持 (redisson-config.yml)

### 新增功能

#### 对话服务增强
- feat: 新增文件播放器服务 (FilePlayer, ThreadPlayer)
- feat: 新增语音合成服务 (FileSynthesizer, ThreadSynthesizer, Synthesizer 接口)
- feat: 新增句子处理抽象 (Sentence)
- feat: 新增唤醒服务 (WakeUp)
- feat: 新增对话辅助工具 (DialogueHelper)
- feat: 新增超时和再见消息供应器 (TimeoutMessageSupplier, GoodbyeMessageSupplier)
- feat: 新增退出关键词检测器 (ExitKeywordDetector)

#### TTS/STT 服务扩展
- feat: 新增阿里云 NLS TTS 服务 (AliyunNlsTtsService)
- feat: 新增阿里云 NLS STT 服务 (AliyunNlsSttService)

#### Tool Calling 增强
- feat: 新增 Quote0Function 工具函数
- feat: 新增 ChatModelObservationHandler 监控处理器
- feat: 新增 XiaozhiToolMetadata 工具元数据管理
- feat: Tool Calling 结果转换器优化 (ToolCallStringResultConverter)

#### 认证与授权
- feat: SaToken 配置升级，增强权限控制
- feat: 新增 @SaIgnore 注解支持（如 OTA 请求权限豁免）

### 优化与改进

#### 架构优化
- refactor: 配置文件从 properties 迁移到 yml 格式
  - application.properties → application.yml
  - application-dev.properties → application-dev.yml
  - application-prod.properties → application-prod.yml
- refactor: 所有 Mapper XML 文件优化
- refactor: Service 层全面重构，统一业务逻辑
- refactor: 实体类优化 (Base, SysConfig, SysDevice, SysMessage, SysRole, SysUser)

#### 对话流程优化
- update: VAD 服务优化 (VadService)
- update: 绘本服务优化 (HuiBenService)
- update: 音乐服务优化 (MusicService)
- update: 消息处理器优化 (MessageHandler)
- update: 会话管理器优化 (SessionManager)
- update: WebSocket 处理优化 (WebSocketHandler, WebSocketSession)

#### 前端优化
- update: 配置管理组件优化 (ConfigManager.vue)
- update: 新增拖拽上传覆盖层组件 (DragUploadOverlay.vue)
- update: 新增音频播放器 Composable (useAudioPlayer.ts)
- update: 新增拖拽上传 Composable (useDragUpload.ts)
- update: 新增记忆视图 Composable (useMemoryView.ts)
- update: 新增记忆管理视图 (MemoryManagementView.vue)
- update: 新增 Web Audio 录音处理器 (audio-recorder-processor.js)
- update: 新增格式化工具类 (format.ts)
- update: Provider 配置优化 (providerConfig.ts)
- update: LLM 工厂配置更新 (llm_factories.json)
- update: 国际化资源更新 (zh-CN.ts, en-US.ts)

#### 工具类优化
- update: 音频工具类优化 (AudioUtils, AudioEnhancer, OpusProcessor)
- update: 通用工具类优化 (CommonUtils, DateUtils, CmsUtils)
- update: Emoji 工具类优化 (EmojiUtils)
- update: 文件上传工具优化 (FileUploadUtils)
- update: 邮件工具优化 (EmailUtils)

#### 数据库
- update: 数据库初始化脚本更新 (init.sql)
- add: 新增 2025-12-17 数据库变更脚本 (db/2025_12_17.sql)
- delete: 删除旧的数据库变更脚本 (db/2025_11_01.sql, db/2025_11_29.sql)

### 删除
- delete: 删除 AudioService（功能整合到新的播放器和合成器架构）
- delete: 删除 StreamResponseListener 接口（统一到新的流式响应机制）

### 依赖更新
- update: 依赖包更新 (pom.xml)
- update: 前端依赖更新 (package.json, bun.lock)
- update: 生产环境配置更新 (web/.env.production)

---

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

