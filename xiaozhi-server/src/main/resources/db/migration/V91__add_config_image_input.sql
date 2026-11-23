-- 模型能否直接看图，前端按模型清单带出
SET @has_column = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_config' AND COLUMN_NAME = 'imageInput'
);
SET @ddl = IF(@has_column = 0,
    'ALTER TABLE `sys_config` ADD COLUMN `imageInput` tinyint(1) NOT NULL DEFAULT 0 COMMENT ''模型能否直接看图'' AFTER `contextLength`',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 存量按清单回填：视觉类型，或模型带 IMAGE2TEXT 标记
UPDATE `sys_config` SET `imageInput` = 1
WHERE `configType` = 'llm'
  AND (`modelType` = 'vision' OR `configName` IN (
    'Align-DS-V', 'ERNIE-4.5-Turbo-VL', 'InternVL2-8B', 'InternVL2.5-26B',
    'InternVL2.5-78B', 'InternVL3-38B', 'InternVL3-78B', 'Pro/moonshotai/Kimi-K2.5',
    'Pro/moonshotai/Kimi-K2.6', 'Qwen/Qwen3-VL-235B-A22B-Instruct', 'Qwen/Qwen3-VL-30B-A3B-Instruct', 'Qwen/Qwen3-VL-30B-A3B-Thinking',
    'Qwen/Qwen3-VL-32B-Instruct', 'Qwen/Qwen3-VL-32B-Thinking', 'Qwen/Qwen3-VL-8B-Instruct', 'Qwen2-Audio-7B-Instruct',
    'Qwen2-VL-72B', 'Qwen2.5-VL-32B-Instruct', 'chat-latest', 'claude-fable-5',
    'claude-fable-5-1', 'claude-fable-5.1', 'claude-haiku-4-5-20251001', 'claude-haiku-4.5',
    'claude-haiku-5-5', 'claude-mythos-5', 'claude-mythos-5-1', 'claude-opus-4-1-20250805',
    'claude-opus-4-1-20250805-thinking', 'claude-opus-4-20250514', 'claude-opus-4-5-20251101', 'claude-opus-4-6',
    'claude-opus-4-7', 'claude-opus-4-8', 'claude-opus-4.5', 'claude-opus-4.6',
    'claude-opus-4.7', 'claude-opus-4.8', 'claude-opus-5', 'claude-opus-5-5',
    'claude-opus-5-fast', 'claude-sonnet-4-20250514', 'claude-sonnet-4-20250514-thinking', 'claude-sonnet-4-5-20250929',
    'claude-sonnet-4-6', 'claude-sonnet-4.5', 'claude-sonnet-4.6', 'claude-sonnet-5',
    'claude-sonnet-5-5', 'deepseek-flash', 'deepseek-v4-flash-0731', 'deepseek-v4.1-flash',
    'doubao-seed-2-0-lite-260428', 'doubao-seed-2-0-mini-260428', 'doubao-seed-2-1-lite-260915', 'doubao-seed-2-1-pro-260628',
    'doubao-seed-2-1-pro-260915', 'doubao-seed-2-1-turbo-260628', 'doubao-seed-character-260628', 'doubao-seed-evolving',
    'gemini-2.0-flash', 'gemini-2.5-flash', 'gemini-2.5-flash-lite', 'gemini-2.5-flash-lite-preview-09-2025',
    'gemini-2.5-pro', 'gemini-3-5-flash', 'gemini-3-flash-preview', 'gemini-3.1-flash-lite',
    'gemini-3.1-pro', 'gemini-3.1-pro-preview', 'gemini-3.5-flash', 'gemini-3.5-flash-lite',
    'gemini-3.6-flash', 'gemini-3.7-flash', 'gemini-3.8-flash', 'glm-4.1v-thinking-flash',
    'glm-4.1v-thinking-flashx', 'glm-4.6v', 'glm-4.6v-flash', 'glm-4v-flash',
    'glm-5.3-flash', 'glm-5.3-flashx', 'glm-5v-turbo', 'google/paligemma',
    'gpt-4.1', 'gpt-4.1-mini', 'gpt-4.1-nano', 'gpt-4o',
    'gpt-4o-mini', 'gpt-5', 'gpt-5-codex', 'gpt-5-mini',
    'gpt-5-nano', 'gpt-5-pro', 'gpt-5.1', 'gpt-5.2',
    'gpt-5.2-codex', 'gpt-5.2-pro', 'gpt-5.4', 'gpt-5.4-mini',
    'gpt-5.4-nano', 'gpt-5.4-pro', 'gpt-5.5', 'gpt-5.5-pro',
    'gpt-5.6', 'gpt-5.6-cyber', 'gpt-5.6-luna', 'gpt-5.6-sol',
    'gpt-5.6-terra', 'gpt-6-astra', 'grok-2-image-1212', 'grok-4-fast-non-reasoning',
    'grok-4-fast-reasoning', 'grok-4.3', 'grok-4.5', 'grok-4.6',
    'kimi-k2.5', 'kimi-k2.6', 'kimi-k2.7-code', 'kimi-k3',
    'kimi/kimi-k2.6', 'kimi/kimi-k2.7-code', 'meta/llama-3.2-11b-vision-instruct', 'meta/llama-3.2-90b-vision-instruct',
    'mimo-v2.5', 'mistral-medium-3-5-26-04', 'mistral-small-4-0-26-03', 'o3',
    'o4-mini', 'ocr-4-0', 'ocr-4-1', 'openai/gpt-4o',
    'openai/gpt-4o-mini', 'qwen-3-8-27b', 'qwen-3-8-max', 'qwen/qwen3.8-27b',
    'qwen3-5-35b-a3b', 'qwen3-omni-flash', 'qwen3-vl-plus', 'qwen3.5-122b-a10b',
    'qwen3.5-27b', 'qwen3.5-35b-a3b', 'qwen3.5-397b-a17b', 'qwen3.5-flash',
    'qwen3.5-flash-2026-02-23', 'qwen3.5-omni-flash', 'qwen3.5-omni-flash-2026-03-15', 'qwen3.5-omni-plus',
    'qwen3.5-omni-plus-2026-03-15', 'qwen3.5-plus', 'qwen3.5-plus-2026-02-15', 'qwen3.5-plus-2026-04-20',
    'qwen3.6-27b', 'qwen3.6-35b-a3b', 'qwen3.6-flash', 'qwen3.6-flash-2026-04-16',
    'qwen3.6-plus', 'qwen3.6-plus-2026-04-02', 'qwen3.7-flash', 'qwen3.7-flash-2026-07-15',
    'qwen3.7-max-2026-06-08', 'qwen3.7-plus', 'qwen3.7-plus-2026-05-26', 'qwen3.8-27b',
    'qwen3.8-flash', 'qwen3.8-max', 'qwen3.8-max-0902', 'qwen3.8-omni-flash',
    'step-1o-turbo-vision', 'step-3.7-flash'
  ));
