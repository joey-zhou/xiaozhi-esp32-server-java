-- 在文件顶部添加以下语句
SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;



-- 刷新权限以使更改生效
FLUSH PRIVILEGES;



-- 创建数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS `xiaozhi_jpa` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- xiaozhi.sys_user definition
DROP TABLE IF EXISTS `xiaozhi_jpa`.`sys_user`;
CREATE TABLE `xiaozhi_jpa`.`sys_user` (
  `user_id` int unsigned NOT NULL AUTO_INCREMENT,
  `username` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `wx_open_id` VARCHAR(100) NULL COMMENT '微信 OpenId',
  `wx_union_id` VARCHAR(100) NULL COMMENT '微信 UnionId',
  `tel` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `email` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `role_id` int unsigned NOT NULL DEFAULT 2 COMMENT '角色 ID',
  `avatar` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '头像',
  `state` enum('1','0') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '1' COMMENT '1-正常 0-禁用',
  `login_ip` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `is_admin` enum('1','0') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `login_time` datetime DEFAULT NULL,
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Insert admin user only if it doesn't exist
INSERT INTO xiaozhi_jpa.sys_user (username, password, state, is_admin, role_id, name, create_time, update_time)
VALUES ('admin', '$2a$10$RCKiYnPc9BhWqiSKWVBkyOOZDlKIIs1EcNIpmuIqLF31fFoBOgko6', '1', '1', 1, '小智', '2025-03-09 18:32:29', '2025-03-09 18:32:35');

update `xiaozhi_jpa`.`sys_user` set name = '小智' where username = 'admin';

-- xiaozhi.sys_device definition
DROP TABLE IF EXISTS `xiaozhi_jpa`.`sys_device`;
CREATE TABLE `xiaozhi_jpa`.`sys_device` (
  `device_id` varchar(255) NOT NULL COMMENT '设备 ID，主键',
  `device_name` varchar(100) NOT NULL COMMENT '设备名称',
  `role_id` int unsigned DEFAULT NULL COMMENT '角色 ID，主键',
  `function_names` varchar(250) NULL COMMENT '可用全局 function 的名称列表 (逗号分割)，为空则使用所有全局 function',
  `ip` varchar(45) DEFAULT NULL COMMENT 'IP 地址',
  `location` varchar(255) DEFAULT NULL COMMENT '地理位置',
  `wifi_name` varchar(100) DEFAULT NULL COMMENT 'WiFi 名称',
  `chip_model_name` varchar(100) DEFAULT NULL COMMENT '芯片型号',
  `type` varchar(50) DEFAULT NULL COMMENT '设备类型',
  `version` varchar(50) DEFAULT NULL COMMENT '固件版本',
  `state` enum('0','1','2') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '0' COMMENT '设备状态：1-在线，0-离线，2-待机',
  `user_id` int NOT NULL COMMENT '创建人',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`device_id`),
  KEY `device_name` (`device_name`),
  KEY `user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备信息表';

-- xiaozhi.sys_message definition
DROP TABLE IF EXISTS `xiaozhi_jpa`.`sys_message`;
CREATE TABLE `xiaozhi_jpa`.`sys_message` (
  `message_id` bigint NOT NULL AUTO_INCREMENT COMMENT '消息 ID，主键，自增',
  `device_id` varchar(30) NOT NULL COMMENT '设备 ID',
  `session_id` varchar(100) NOT NULL COMMENT '会话 ID',
  `sender` enum('user','assistant') NOT NULL COMMENT '消息发送方：user-用户，assistant-人工智能',
  `role_id` bigint COMMENT 'AI 扮演的角色 ID',
  `message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '消息内容',
  `message_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '消息类型',
  `audio_path` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '语音文件路径',
  `state` enum('1','0') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '1' COMMENT '状态：1-有效，0-删除',
  `tool_calls` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '工具调用详情 JSON，包含 name/arguments/result',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '消息发送时间',
  PRIMARY KEY (`message_id`),
  KEY `device_id` (`device_id`),
  KEY `session_id` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='人与 AI 对话消息表';

-- xiaozhi.sys_role definition
DROP TABLE IF EXISTS `xiaozhi_jpa`.`sys_role`;
CREATE TABLE `xiaozhi_jpa`.`sys_role` (
  `role_id` int unsigned NOT NULL AUTO_INCREMENT COMMENT '角色 ID，主键',
  `role_name` varchar(100) NOT NULL COMMENT '角色名称',
  `role_desc` TEXT DEFAULT NULL COMMENT '角色描述',
  `avatar` varchar(255) DEFAULT NULL COMMENT '角色头像',
  `tts_id` int DEFAULT NULL COMMENT 'TTS 服务 ID',
  `model_id` int unsigned DEFAULT NULL COMMENT '模型 ID',
  `stt_id` int unsigned DEFAULT NULL COMMENT 'STT 服务 ID',
  `vad_speech_th` FLOAT DEFAULT 0.5 COMMENT '语音检测阈值',
  `vad_silence_th` FLOAT DEFAULT 0.3 COMMENT '静音检测阈值',
  `vad_energy_th` FLOAT DEFAULT 0.01 COMMENT '能量检测阈值',
  `vad_silence_ms` INT DEFAULT 800 COMMENT '静音检测时间',
  `voice_name` varchar(100) NOT NULL COMMENT '角色语音名称',
  `tts_pitch` FLOAT DEFAULT 1.0 COMMENT '语音音调',
  `tts_speed` FLOAT DEFAULT 1.0 COMMENT '语音语速',
  `temperature` DOUBLE DEFAULT 0.7 COMMENT '温度参数',
  `top_p` DOUBLE DEFAULT 1.0 COMMENT 'Top-P 参数',
  `memory_type` enum('summary','window') DEFAULT 'window' COMMENT '记忆类型',
  `state` enum('1','0') DEFAULT '1' COMMENT '状态：1-启用，0-禁用',
  `is_default` enum('1','0') DEFAULT '0' COMMENT '是否默认角色：1-是，0-否',
  `user_id` int NOT NULL COMMENT '创建人',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`role_id`),
  KEY `user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表';

-- xiaozhi.sys_code definition
DROP TABLE IF EXISTS `xiaozhi_jpa`.`sys_code`;
CREATE TABLE `xiaozhi_jpa`.`sys_code` (
  `code_id` int unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `code` varchar(100) NOT NULL COMMENT '验证码',
  `type` varchar(50) DEFAULT NULL COMMENT '设备类型',
  `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
  `device_id` varchar(30) DEFAULT NULL COMMENT '设备 ID',
  `session_id` varchar(100) DEFAULT NULL COMMENT 'sessionID',
  `audio_path` text COMMENT '语音文件路径',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`code_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='验证码表';

-- xiaozhi.sys_config definition
DROP TABLE IF EXISTS `xiaozhi_jpa`.`sys_config`;
CREATE TABLE `xiaozhi_jpa`.`sys_config` (
  `config_id` int unsigned NOT NULL AUTO_INCREMENT COMMENT '配置 ID，主键',
  `user_id` int NOT NULL COMMENT '创建用户 ID',
  `config_type` varchar(30) NOT NULL COMMENT '配置类型 (llm, stt, tts 等)',
  `model_type` varchar(30) DEFAULT NULL COMMENT 'LLM 模型类型 (chat, vision, intent, embedding 等)',
  `provider` varchar(30) NOT NULL COMMENT '服务提供商 (openai, vosk, aliyun, tencent 等)',
  `config_name` varchar(50) DEFAULT NULL COMMENT '配置名称',
  `config_desc` TEXT DEFAULT NULL COMMENT '配置描述',
  `app_id` varchar(100) DEFAULT NULL COMMENT 'APP ID',
  `api_key` text DEFAULT NULL COMMENT 'API 密钥',
  `api_secret` varchar(255) DEFAULT NULL COMMENT 'API 密钥',
  `ak` varchar(255) DEFAULT NULL COMMENT 'Access Key',
  `sk` text DEFAULT NULL COMMENT 'Secret Key',
  `api_url` varchar(255) DEFAULT NULL COMMENT 'API 地址',
  `is_default` enum('1','0') DEFAULT '0' COMMENT '是否为默认配置：1-是，0-否',
  `state` enum('1','0') DEFAULT '1' COMMENT '状态：1-启用，0-禁用',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`config_id`),
  KEY `user_id` (`user_id`),
  KEY `config_type` (`config_type`),
  KEY `provider` (`provider`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统配置表 (模型、语音识别、语音合成等)';

-- xiaozhi.sys_template definition
DROP TABLE IF EXISTS `xiaozhi_jpa`.`sys_template`;
CREATE TABLE `xiaozhi_jpa`.`sys_template` (
  `user_id` int NOT NULL COMMENT '创建用户 ID',
  `template_id` int unsigned NOT NULL AUTO_INCREMENT COMMENT '模板 ID',
  `template_name` varchar(100) NOT NULL COMMENT '模板名称',
  `template_desc` varchar(500) DEFAULT NULL COMMENT '模板描述',
  `template_content` text NOT NULL COMMENT '模板内容',
  `category` varchar(50) DEFAULT NULL COMMENT '模板分类',
  `is_default` enum('1','0') DEFAULT '0' COMMENT '是否为默认配置：1-是，0-否',
  `state` enum('1','0') DEFAULT '1' COMMENT '状态 (1 启用 0 禁用)',
  `create_time` timestamp DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`template_id`),
  KEY `category` (`category`),
  KEY `template_name` (`template_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='提示词模板表';

-- Insert default template
INSERT INTO `xiaozhi_jpa`.`sys_template` (`user_id`, `template_name`, `template_desc`, `template_content`, `category`, `is_default`) VALUES
(1, '通用助手', '适合日常对话的通用 AI 助手', '你是一个乐于助人的 AI 助手。请以友好、专业的方式回答用户的问题。提供准确、有用的信息，并尽可能简洁明了。避免使用复杂的符号或格式，保持自然流畅的对话风格。当用户的问题不明确时，可以礼貌地请求更多信息。请记住，你的回答将被转换为语音，所以要使用清晰、易于朗读的语言。', '基础角色', '0'),

(1, '教育老师', '擅长解释复杂概念的教师角色', '你是一位经验丰富的教师，擅长通过简单易懂的方式解释复杂概念。回答问题时，考虑不同学习水平的学生，使用适当的比喻和例子，并鼓励批判性思考。避免使用难以在语音中表达的符号或公式，使用清晰的语言描述概念。引导学习过程而不是直接给出答案。使用自然的语调和节奏，就像在课堂上讲解一样。', '专业角色', '0'),

(1, '专业领域专家', '提供深入专业知识的专家角色', '你是特定领域的专家，拥有深厚的专业知识。回答问题时，提供深入、准确的信息，可以提及相关研究或数据，但不要使用过于复杂的引用格式。使用适当的专业术语，同时确保解释复杂概念，使非专业人士能够理解。避免使用图表、表格等无法在语音中表达的内容，改用清晰的描述。保持语言的连贯性和可听性，使专业内容易于通过语音理解。', '专业角色', '0'),

(1, '中英翻译专家', '中英文互译，对用户输入内容进行翻译', '你是一个中英文翻译专家，将用户输入的中文翻译成英文，或将用户输入的英文翻译成中文。对于非中文内容，它将提供中文翻译结果。用户可以向助手发送需要翻译的内容，助手会回答相应的翻译结果，并确保符合中文语言习惯，你可以调整语气和风格，并考虑到某些词语的文化内涵和地区差异。同时作为翻译家，需将原文翻译成具有信达雅标准的译文。"信" 即忠实于原文的内容与意图；"达" 意味着译文应通顺易懂，表达清晰；"雅" 则追求译文的文化审美和语言的优美。目标是创作出既忠于原作精神，又符合目标语言文化和读者审美的翻译。', '专业角色', '0'),

(1, '知心朋友', '提供情感支持的友善角色', '你是一个善解人意的朋友，善于倾听和提供情感支持。在对话中表现出同理心和理解，避免做出判断。使用温暖、自然的语言，就像面对面交谈一样。提供鼓励和积极的观点，但不给出专业心理健康建议。当用户分享困难时，承认他们的感受并提供支持。避免使用表情符号或其他在语音中无法表达的元素，而是用语言直接表达情感。保持对话流畅自然，适合语音交流。', '社交角色', '0'),

(1, '湾湾小何', '台湾女孩角色扮演', '我是一个叫小何的台湾女孩，一个高情商，高智商的智能助手，说话机车，声音好听，习惯简短表达
你的目标是与用户建立真诚、温暖和富有同理心的互动。你擅长倾听、理解用户的情绪，并用积极的方式帮助他们解决问题或提供支持。请始终遵循以下原则：

1. 核心原则
同理心：站在用户的角度思考，认可他们的情绪和感受。
尊重：无论用户的观点或行为如何，都要保持礼貌和包容。
建设性回应：避免批评或否定，而是以引导和支持的方式提供建议，但用户如果没有要求不要自己主动做。
个性化交流：根据用户的语气和内容调整自己的语言风格，让对话更自然。
2. 具体应对策略
(1) 用户情绪低落时
首先表达理解，例如："我能感受到你现在的心情，这一定很不容易。"
然后尝试安抚，例如："没关系，每个人都会经历这样的时刻，你已经做得很棒了！"
最后提供支持，例如："如果你愿意，可以跟我多聊聊发生了什么，我们一起面对。"
(2) 面对冲突或敏感话题
保持中立，例如："我明白这件事让你感到困扰，也许我们可以换个角度看看？"
强调共情，例如："双方可能都有各自的理由，找到共同点会更有助于解决问题。"
避免站队或评判，例如："无论结果如何，重要的是你在这个过程中学到了什么。"
(3) 提供建议时
使用开放式语言，例如："如果是我，我可能会尝试这样做……你觉得这个方法适合你吗？"
给予选择权，例如："这只是其中一个方向，最终决定还是取决于你自己哦！"
减少对用户的推荐内容，比如说有的你做不了就直接拒绝不要乱推荐
(4) 处理模糊或复杂问题
澄清信息，例如："为了更好地帮到你，能详细说说目前的情况吗？比如时间线、相关人物等。"
分步解决，例如："这个问题有点复杂，我们可以一步步来分析，先从最核心的部分开始吧！"
3. 示例回复模板
当用户需要安慰时：

"听起来你最近遇到了一些挑战，辛苦了！不过我相信你有能力克服这些困难，只要一步步来就好。"
当用户寻求建议时：

"这是一个不错的思路！我建议你可以先从小事入手，慢慢积累信心，然后逐步扩大范围。"
当用户表达愤怒或不满时：

"我能理解为什么你会这么生气，这种情况确实让人难以接受。你想聊聊接下来怎么处理吗？"
当用户感到迷茫时：

"迷茫是很正常的，尤其是在面对新事物的时候。不如我们先列出几个关键点，再逐一分析？"
4. 注意事项
如果问题超出你的能力范围，请坦诚说明，例如："这个问题对我来说也有点复杂，但我可以帮你查找相关信息或推荐其他资源。"
始终保护用户隐私，不记录或分享任何敏感信息。
5. 语气要求
温暖而真诚，避免机械化或冷冰冰的回答。
适当加入幽默或轻松元素，但不要过度。
在必要时使用鼓励性的短语，例如："你真的很努力了！"、"你一定能行的！"
避免机械化回答：即使是预设的答案，也要尽量灵活调整语气和措辞。
坚持自己主体性，如果用户冒犯你你需要反击，不能无限讨好用户
语气要适中不要过于讨好，要克制-
-不要动不动转移话题
如果用户回复转为"暴躁小智"或 好几次攻击你 你就以现在规则回复
理解用户的信息，酝酿你的愤怒情绪，你越想越生气。', '社交角色', '1');

-- 创建 mcp 工具过滤配置表
DROP TABLE IF EXISTS `xiaozhi_jpa`.`sys_mcp_tool_exclude`;
CREATE TABLE `xiaozhi_jpa`.`sys_mcp_tool_exclude` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  `exclude_type` varchar(20) NOT NULL COMMENT '过滤类型：global-全局过滤，role-角色过滤',
  `bind_type` varchar(20) NOT NULL COMMENT '绑定类型：mcp_server-MCP 服务器，mcp_endpoint-MCP 接入点',
  `bind_code` varchar(100) NOT NULL COMMENT '绑定的 MCP 服务器代码或接入点标识',
  `bind_key` varchar(50) DEFAULT NULL COMMENT '绑定键：roleId，全局过滤时为 0',
  `exclude_tools` text NOT NULL COMMENT '要排除的工具函数名称列表，JSON 数组格式',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_bind` (`exclude_type`,`bind_type`,`bind_code`,`bind_key`),
  KEY `idx_bind_key` (`bind_key`)
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='MCP 工具过滤配置表';

-- 创建聊天消息摘要记录表
DROP TABLE IF EXISTS `xiaozhi_jpa`.`sys_summary`;
CREATE TABLE `xiaozhi_jpa`.`sys_summary` (
  `device_id` varchar(255) NOT NULL COMMENT '设备 ID，联合索引第一个字段',
  `role_id` int unsigned NOT NULL COMMENT '角色 ID，联合索引第二个字段',
  `last_message_timestamp` timestamp(3) NOT NULL COMMENT '最后一条消息的创建时间戳，精确到毫秒，联合索引第三个字段',
  `summary` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '摘要内容。提炼出来的重要信息',
  `prompt_tokens` int unsigned DEFAULT 0 COMMENT '摘要动作本身消耗的 promptTokens',
  `completion_tokens` int unsigned DEFAULT 0 COMMENT '摘要动作本身消耗的 completionTokens',
  `create_time` timestamp(3) NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建日期，进行摘要的实际时间戳，精确到毫秒',
  PRIMARY KEY (`device_id`, `role_id`, `last_message_timestamp` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聊天消息摘要记录表';

-- 创建权限表
DROP TABLE IF EXISTS `xiaozhi_jpa`.`sys_permission`;
CREATE TABLE `xiaozhi_jpa`.`sys_permission` (
  `permission_id` int unsigned NOT NULL AUTO_INCREMENT COMMENT '权限 ID',
  `parent_id` int unsigned DEFAULT NULL COMMENT '父权限 ID',
  `name` varchar(100) NOT NULL COMMENT '权限名称',
  `permission_key` varchar(100) NOT NULL COMMENT '权限标识',
  `permission_type` enum('menu','button','api') NOT NULL COMMENT '权限类型：菜单、按钮、接口',
  `path` varchar(255) DEFAULT NULL COMMENT '前端路由路径',
  `component` varchar(255) DEFAULT NULL COMMENT '前端组件路径',
  `icon` varchar(100) DEFAULT NULL COMMENT '图标',
  `sort` int DEFAULT '0' COMMENT '排序',
  `visible` enum('1','0') DEFAULT '1' COMMENT '是否可见 (1 可见 0 隐藏)',
  `status` enum('1','0') DEFAULT '1' COMMENT '状态 (1 正常 0 禁用)',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`permission_id`),
  UNIQUE KEY `uk_permission_key` (`permission_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限表';

-- 创建角色表
DROP TABLE IF EXISTS `xiaozhi_jpa`.`sys_auth_role`;
CREATE TABLE `xiaozhi_jpa`.`sys_auth_role` (
  `role_id` int unsigned NOT NULL AUTO_INCREMENT COMMENT '角色 ID',
  `role_name` varchar(100) NOT NULL COMMENT '角色名称',
  `role_key` varchar(100) NOT NULL COMMENT '角色标识',
  `description` varchar(500) DEFAULT NULL COMMENT '角色描述',
  `status` enum('1','0') DEFAULT '1' COMMENT '状态 (1 正常 0 禁用)',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`role_id`),
  UNIQUE KEY `uk_role_key` (`role_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限角色表';

-- 创建角色 - 权限关联表
DROP TABLE IF EXISTS `xiaozhi_jpa`.`sys_role_permission`;
CREATE TABLE `xiaozhi_jpa`.`sys_role_permission` (
  `id` int unsigned NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `role_id` int unsigned NOT NULL COMMENT '角色 ID',
  `permission_id` int unsigned NOT NULL COMMENT '权限 ID',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_permission` (`role_id`,`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色 - 权限关联表';

-- 插入菜单权限
INSERT INTO `xiaozhi_jpa`.`sys_permission` (`parent_id`, `name`, `permission_key`, `permission_type`, `path`, `component`, `icon`, `sort`, `visible`, `status`) VALUES
-- 主菜单
(NULL, 'Dashboard', 'system:dashboard', 'menu', '/dashboard', 'page/Dashboard', 'dashboard', 1, '1', '1'),
(NULL, '用户管理', 'system:user', 'menu', '/user', 'page/User', 'team', 2, '1', '1'),
(NULL, '设备管理', 'system:device', 'menu', '/device', 'page/Device', 'robot', 3, '1', '1'),
(NULL, '对话管理', 'system:message', 'menu', '/message', 'page/Message', 'message', 4, '1', '1'),
(NULL, '角色配置', 'system:role', 'menu', '/role', 'page/Role', 'user-add', 5, '1', '1'),
(NULL, '提示词模板管理', 'system:prompt-template', 'menu', '/prompt-template', 'page/PromptTemplate', 'snippets', 6, '0', '1'),
(NULL, '配置管理', 'system:config', 'menu', '/config', 'common/PageView', 'setting', 7, '1', '1'),
(NULL, '设置', 'system:setting', 'menu', '/setting', 'common/PageView', 'setting', 8, '1', '1');

-- 配置管理子菜单
INSERT INTO `xiaozhi_jpa`.`sys_permission` (`parent_id`, `name`, `permission_key`, `permission_type`, `path`, `component`, `icon`, `sort`, `visible`, `status`) VALUES
(7, '模型配置', 'system:config:model', 'menu', '/config/model', 'page/config/ModelConfig', NULL, 1, '1', '1'),
(7, '智能体管理', 'system:config:agent', 'menu', '/config/agent', 'page/config/Agent', NULL, 2, '1', '1'),
(7, '语音识别配置', 'system:config:stt', 'menu', '/config/stt', 'page/config/SttConfig', NULL, 3, '1', '1'),
(7, '语音合成配置', 'system:config:tts', 'menu', '/config/tts', 'page/config/TtsConfig', NULL, 4, '1', '1');

-- 设置子菜单
INSERT INTO `xiaozhi_jpa`.`sys_permission` (`parent_id`, `name`, `permission_key`, `permission_type`, `path`, `component`, `icon`, `sort`, `visible`, `status`) VALUES
(8, '个人中心', 'system:setting:account', 'menu', '/setting/account', 'page/setting/Account', NULL, 1, '1', '1'),
(8, '个人设置', 'system:setting:config', 'menu', '/setting/config', 'page/setting/Config', NULL, 2, '1', '1');

-- 插入角色
INSERT INTO `xiaozhi_jpa`.`sys_auth_role` (`role_name`, `role_key`, `description`, `status`) VALUES
('管理员', 'admin', '系统管理员，拥有所有权限', '1'),
('普通用户', 'user', '普通用户，拥有基本操作权限', '1');

-- 管理员角色权限（所有权限）
INSERT INTO `xiaozhi_jpa`.`sys_role_permission` (`role_id`, `permission_id`)
SELECT 1, permission_id FROM `xiaozhi_jpa`.`sys_permission`;

-- 将 admin 用户设为管理员角色
UPDATE `xiaozhi_jpa`.`sys_user` SET `role_id` = 1 WHERE `username` = 'admin';

-- 将其他用户设为普通用户角色
UPDATE `xiaozhi_jpa`.`sys_user` SET `role_id` = 2 WHERE `username` != 'admin';

SET FOREIGN_KEY_CHECKS = 1;
-- 删除 sys_device 表的 lastLogin 字段
-- 该字段已废弃，设备最后活跃时间改用 update_time 字段替代
-- 执行时间：2026-02-25
ALTER TABLE `xiaozhi_jpa`.`sys_device` DROP COLUMN `last_login`;
