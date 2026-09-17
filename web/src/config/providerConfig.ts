/**
 * 系统中各类服务提供商配置
 * 统一管理各类服务的提供商信息，便于维护和扩展
 *
 * ConfigField 的 help 一律写 i18n key（config.help.*），label/placeholder 只有
 * 非品牌名的才写 key（config.field.*），渲染方按 config. 前缀决定是否翻译
 */

import type { ConfigField, ConfigTypeInfo } from '@/types/config'

/**
 * S3 兼容对象存储的通用字段。
 * MinIO / Cloudflare R2 / Backblaze B2 / 华为 OBS / Wasabi / DigitalOcean Spaces / 七牛 Kodo 等
 * 底层都走后端同一个 S3StorageService，仅 Endpoint 提示不同，故共用此函数生成字段。
 */
const s3CompatibleFields = (endpointPlaceholder: string, endpointHelpKey: string): ConfigField[] => [
  { name: 'apiUrl', label: 'Endpoint', required: true, inputType: 'text', span: 12, help: endpointHelpKey, placeholder: endpointPlaceholder },
  { name: 'ak', label: 'Access Key', required: true, inputType: 'password', span: 12, help: 'config.help.s3AccessKey', placeholder: 'access-key' },
  { name: 'sk', label: 'Secret Key', required: true, inputType: 'password', span: 12, help: 'config.help.s3SecretKey', placeholder: 'secret-key' },
  { name: 'configName', label: 'Bucket', required: true, inputType: 'text', span: 12, help: 'config.help.s3Bucket', placeholder: 'my-bucket' },
  { name: 'appId', label: 'Region', required: false, inputType: 'text', span: 12, help: 'config.help.s3Region', placeholder: 'us-east-1' },
]

// 配置类型信息映射
export const configTypeMap: Record<string, ConfigTypeInfo> = {
  llm: {
    label: 'config.llm',
    permissionPrefix: 'system:config',
    // 各类别对应的参数字段定义
    typeFields: {
      // OpenAI 系列
      'OpenAI': [
        {
          name: 'apiKey',
          label: 'API Key',
          required: true,
          inputType: 'password',
          placeholder: 'sk-...',
          span: 12,
          help: 'config.help.llmOpenAIApiKey'
        }
      ],
      // 阿里云系列
      'Tongyi-Qianwen': [
        {
          name: 'apiKey',
          label: 'API Key',
          required: true,
          inputType: 'password',
          placeholder: 'your-api-key',
          span: 12,
          help: 'config.help.llmTongyiQianwenApiKey'
        }
      ],
      // 讯飞星火
      'XunFei Spark': [
        {
          name: 'appId',
          label: 'App Id',
          required: true,
          inputType: 'text',
          placeholder: 'your-app-id',
          span: 12,
          help: 'config.help.llmXunFeiSparkAppId'
        },
        {
          name: 'apiKey',
          label: 'API Key',
          required: true,
          inputType: 'password',
          placeholder: 'your-api-key',
          span: 12,
          help: 'config.help.llmXunFeiSparkApiKey'
        },
        {
          name: 'apiSecret',
          label: 'API Secret',
          required: true,
          inputType: 'password',
          placeholder: 'your-api-secret',
          span: 12,
          help: 'config.help.llmXunFeiSparkApiSecret'
        }
      ],
      // 智谱AI
      'ZHIPU-AI': [
        {
          name: 'apiKey',
          label: 'API Key',
          required: true,
          inputType: 'password',
          placeholder: 'your-api-key',
          span: 12,
          help: 'config.help.llmZHIPUAIApiKey'
        }
      ],
      // DeepSeek
      'DeepSeek': [
        {
          name: 'apiKey',
          label: 'API Key',
          required: true,
          inputType: 'password',
          placeholder: 'your-api-key',
          span: 12,
          help: 'config.help.llmDeepSeekApiKey'
        }
      ],
      // 火山引擎
      'VolcEngine': [
        {
          name: 'apiKey',
          label: 'API Key',
          required: true,
          inputType: 'password',
          placeholder: 'your-api-key',
          span: 12,
          help: 'config.help.llmVolcEngineApiKey'
        },
        {
          name: 'apiUrl',
          label: 'API URL',
          required: true,
          inputType: 'text',
          placeholder: 'https://ark.cn-beijing.volces.com/api/v3',
          span: 12,
          suffix: '/chat/completions',
          help: 'config.help.llmVolcEngineApiUrl'
        }
      ],
      // MiniMax
      'MiniMax': [
        {
          name: 'apiKey',
          label: 'API Key',
          required: true,
          inputType: 'password',
          placeholder: 'your-api-key',
          span: 12,
          help: 'config.help.llmMiniMaxApiKey'
        }
      ],
      // 腾讯混元
      'Tencent Hunyuan': [
        {
          name: 'apiKey',
          label: 'API Key',
          required: true,
          inputType: 'password',
          placeholder: 'your-api-key',
          span: 12,
          help: 'config.help.llmTencentHunyuanApiKey'
        }
      ],
      // 百度文心
      'BaiChuan': [
        {
          name: 'apiKey',
          label: 'API Key',
          required: true,
          inputType: 'password',
          placeholder: 'your-api-key',
          span: 12,
          help: 'config.help.llmBaiChuanApiKey'
        },
        {
          name: 'apiUrl',
          label: 'API URL',
          required: true,
          inputType: 'text',
          placeholder: 'https://api.baichuan-ai.com/v1',
          span: 12,
          suffix: '/chat/completions',
          help: 'config.help.llmBaiChuanApiUrl'
        }
      ],
      // Moonshot (月之暗面)
      'Moonshot': [
        {
          name: 'apiKey',
          label: 'API Key',
          required: true,
          inputType: 'password',
          placeholder: 'your-api-key',
          span: 12,
          help: 'config.help.llmMoonshotApiKey'
        }
      ],
      // 硅基流动
      'SILICONFLOW': [
        {
          name: 'apiKey',
          label: 'API Key',
          required: true,
          inputType: 'password',
          placeholder: 'your-api-key',
          span: 12,
          help: 'config.help.llmSILICONFLOWApiKey'
        }
      ],
      // 百度文心一言
      'BaiduYiyan': [
        {
          name: 'apiKey',
          label: 'API Key',
          required: true,
          inputType: 'password',
          placeholder: 'your-api-key',
          span: 12,
          help: 'config.help.llmBaiduYiyanApiKey'
        },
        {
          name: 'apiSecret',
          label: 'Secret Key',
          required: true,
          inputType: 'password',
          placeholder: 'your-secret-key',
          span: 12,
          help: 'config.help.llmBaiduYiyanApiSecret'
        },
        {
          name: 'apiUrl',
          label: 'API URL',
          required: true,
          inputType: 'text',
          placeholder: 'https://aip.baidubce.com/rpc/2.0/ai_custom/v1',
          span: 12,
          suffix: '/wenxinworkshop/chat/completions',
          help: 'config.help.llmBaiduYiyanApiUrl'
        }
      ],
      // 其他本地服务
      'Ollama': [
        {
          name: 'apiUrl',
          label: 'API URL',
          required: true,
          inputType: 'text',
          placeholder: 'http://localhost:11434/v1',
          span: 12,
          suffix: '/chat/completions',
          help: 'config.help.llmOllamaApiUrl'
        }
      ],
      'LM-Studio': [
        {
          name: 'apiUrl',
          label: 'API URL',
          required: true,
          inputType: 'text',
          placeholder: 'http://localhost:1234/v1',
          span: 12,
          suffix: '/chat/completions',
          help: 'config.help.llmLMStudioApiUrl'
        }
      ],
      'Azure-OpenAI': [
        {
          name: 'apiKey',
          label: 'API Key',
          required: true,
          inputType: 'password',
          placeholder: 'your-api-key',
          span: 12,
          help: 'config.help.llmAzureOpenAIApiKey'
        },
        {
          name: 'apiUrl',
          label: 'API URL',
          required: true,
          inputType: 'text',
          placeholder: 'https://your-resource-name.openai.azure.com',
          span: 12,
          suffix: '/chat/completions',
          help: 'config.help.llmAzureOpenAIApiUrl'
        }
      ],
      // xAI
      'xAI': [
        {
          name: 'apiKey',
          label: 'API Key',
          required: true,
          inputType: 'password',
          placeholder: 'your-api-key',
          span: 12,
          help: 'config.help.llmXAIApiKey'
        },
        {
          name: 'apiUrl',
          label: 'API URL',
          required: true,
          inputType: 'text',
          placeholder: 'https://api.x.ai/v1',
          span: 12,
          suffix: '/chat/completions',
          help: 'config.help.llmXAIApiUrl'
        }
      ],
      // Mistral
      'Mistral': [
        {
          name: 'apiKey',
          label: 'API Key',
          required: true,
          inputType: 'password',
          placeholder: 'your-api-key',
          span: 12,
          help: 'config.help.llmMistralApiKey'
        }
      ],
      // Google Gemini
      'Gemini': [
        {
          name: 'apiKey',
          label: 'API Key',
          required: true,
          inputType: 'password',
          placeholder: 'your-api-key',
          span: 12,
          help: 'config.help.llmGeminiApiKey'
        },
        {
          name: 'apiUrl',
          label: 'API URL',
          required: true,
          inputType: 'text',
          placeholder: 'https://generativelanguage.googleapis.com',
          span: 12,
          suffix: '/chat/completions',
          help: 'config.help.llmGeminiApiUrl'
        }
      ],
      // Groq
      'Groq': [
        {
          name: 'apiKey',
          label: 'API Key',
          required: true,
          inputType: 'password',
          placeholder: 'your-api-key',
          span: 12,
          help: 'config.help.llmGroqApiKey'
        },
        {
          name: 'apiUrl',
          label: 'API URL',
          required: true,
          inputType: 'text',
          placeholder: 'https://api.groq.com/openai/v1',
          span: 12,
          suffix: '/chat/completions',
          help: 'config.help.llmGroqApiUrl'
        }
      ],
      // OpenRouter
      'OpenRouter': [
        {
          name: 'apiKey',
          label: 'API Key',
          required: true,
          inputType: 'password',
          placeholder: 'your-api-key',
          span: 12,
          help: 'config.help.llmOpenRouterApiKey'
        },
        {
          name: 'apiUrl',
          label: 'API URL',
          required: true,
          inputType: 'text',
          placeholder: 'https://openrouter.ai/api/v1',
          span: 12,
          suffix: '/chat/completions',
          help: 'config.help.llmOpenRouterApiUrl'
        }
      ],
      // StepFun
      'StepFun': [
        {
          name: 'apiKey',
          label: 'API Key',
          required: true,
          inputType: 'password',
          placeholder: 'your-api-key',
          span: 12,
          help: 'config.help.llmStepFunApiKey'
        },
        {
          name: 'apiUrl',
          label: 'API URL',
          required: true,
          inputType: 'text',
          placeholder: 'https://api.stepfun.com/v1',
          span: 12,
          suffix: '/chat/completions',
          help: 'config.help.llmStepFunApiUrl'
        }
      ],
      // NVIDIA
      'NVIDIA': [
        {
          name: 'apiKey',
          label: 'API Key',
          required: true,
          inputType: 'password',
          placeholder: 'your-api-key',
          span: 12,
          help: 'config.help.llmNVIDIAApiKey'
        },
        {
          name: 'apiUrl',
          label: 'API URL',
          required: true,
          inputType: 'text',
          placeholder: 'https://integrate.api.nvidia.com/v1',
          span: 12,
          suffix: '/chat/completions',
          help: 'config.help.llmNVIDIAApiUrl'
        }
      ],
      // 01.AI
      '01.AI': [
        {
          name: 'apiKey',
          label: 'API Key',
          required: true,
          inputType: 'password',
          placeholder: 'your-api-key',
          span: 12,
          help: 'config.help.llm01AIApiKey'
        },
        {
          name: 'apiUrl',
          label: 'API URL',
          required: true,
          inputType: 'text',
          placeholder: 'https://api.01.ai/v1',
          span: 12,
          suffix: '/chat/completions',
          help: 'config.help.llm01AIApiUrl'
        }
      ],
      // Anthropic
      'Anthropic': [
        {
          name: 'apiKey',
          label: 'API Key',
          required: true,
          inputType: 'password',
          placeholder: 'your-api-key',
          span: 12,
          help: 'config.help.llmAnthropicApiKey'
        },
        {
          name: 'apiUrl',
          label: 'API URL',
          required: true,
          inputType: 'text',
          placeholder: 'https://api.anthropic.com/v1',
          span: 12,
          suffix: '/messages',
          help: 'config.help.llmAnthropicApiUrl'
        }
      ],
      // Voyage AI
      'Voyage AI': [
        {
          name: 'apiKey',
          label: 'API Key',
          required: true,
          inputType: 'password',
          placeholder: 'your-api-key',
          span: 12,
          help: 'config.help.llmVoyageAIApiKey'
        },
        {
          name: 'apiUrl',
          label: 'API URL',
          required: true,
          inputType: 'text',
          placeholder: 'https://api.voyageai.com/v1',
          span: 12,
          suffix: '/chat/completions',
          help: 'config.help.llmVoyageAIApiUrl'
        }
      ],
      // GiteeAI
      'GiteeAI': [
        {
          name: 'apiKey',
          label: 'API Key',
          required: true,
          inputType: 'password',
          placeholder: 'your-api-key',
          span: 12,
          help: 'config.help.llmGiteeAIApiKey'
        }
      ],
      // DeepInfra
      'DeepInfra': [
        {
          name: 'apiKey',
          label: 'API Key',
          required: true,
          inputType: 'password',
          placeholder: 'your-api-key',
          span: 12,
          help: 'config.help.llmDeepInfraApiKey'
        },
        {
          name: 'apiUrl',
          label: 'API URL',
          required: true,
          inputType: 'text',
          placeholder: 'https://api.deepinfra.com/v1',
          span: 12,
          suffix: '/chat/completions',
          help: 'config.help.llmDeepInfraApiUrl'
        }
      ],
      'LocalAI': [
        {
          name: 'apiKey',
          label: 'API Key',
          required: false,
          inputType: 'password',
          placeholder: 'your-api-key',
          span: 12,
          help: 'config.help.llmLocalAIApiKey'
        },
        {
          name: 'apiUrl',
          label: 'API URL',
          required: true,
          inputType: 'text',
          placeholder: 'http://localhost:8080/v1',
          span: 12,
          suffix: '/chat/completions',
          help: 'config.help.llmLocalAIApiUrl'
        }
      ],
      'VLLM': [
        {
          name: 'apiUrl',
          label: 'API URL',
          required: true,
          inputType: 'text',
          placeholder: 'http://localhost:8000/v1',
          span: 12,
          suffix: '/chat/completions',
          help: 'config.help.llmVLLMApiUrl'
        }
      ],
      'Xinference': [
        {
          name: 'apiKey',
          label: 'API Key',
          required: false,
          inputType: 'password',
          placeholder: 'your-api-key',
          span: 12,
          help: 'config.help.llmXinferenceApiKey'
        },
        {
          name: 'apiUrl',
          label: 'API URL',
          required: true,
          inputType: 'text',
          placeholder: 'http://localhost:9997/v1',
          span: 12,
          suffix: '/chat/completions',
          help: 'config.help.llmXinferenceApiUrl'
        }
      ],
      // HuggingFace
      'HuggingFace': [
        {
          name: 'apiKey',
          label: 'API Token',
          required: true,
          inputType: 'password',
          placeholder: 'hf_...',
          span: 12,
          help: 'config.help.llmHuggingFaceApiKey'
        },
        {
          name: 'apiUrl',
          label: 'API URL',
          required: true,
          inputType: 'text',
          placeholder: 'https://api-inference.huggingface.co/v1',
          span: 12,
          suffix: '/chat/completions',
          help: 'config.help.llmHuggingFaceApiUrl'
        }
      ],
      // Cohere
      'Cohere': [
        {
          name: 'apiKey',
          label: 'API Key',
          required: true,
          inputType: 'password',
          placeholder: 'your-api-key',
          span: 12,
          help: 'config.help.llmCohereApiKey'
        },
        {
          name: 'apiUrl',
          label: 'API URL',
          required: true,
          inputType: 'text',
          placeholder: 'https://api.cohere.ai/v1',
          span: 12,
          suffix: '/chat',
          help: 'config.help.llmCohereApiUrl'
        }
      ],
      // TogetherAI
      'TogetherAI': [
        {
          name: 'apiKey',
          label: 'API Key',
          required: true,
          inputType: 'password',
          placeholder: 'your-api-key',
          span: 12,
          help: 'config.help.llmTogetherAIApiKey'
        },
        {
          name: 'apiUrl',
          label: 'API URL',
          required: true,
          inputType: 'text',
          placeholder: 'https://api.together.xyz/v1',
          span: 12,
          suffix: '/chat/completions',
          help: 'config.help.llmTogetherAIApiUrl'
        }
      ],
      // Replicate
      'Replicate': [
        {
          name: 'apiKey',
          label: 'API Token',
          required: true,
          inputType: 'password',
          placeholder: 'r8_...',
          span: 12,
          help: 'config.help.llmReplicateApiKey'
        },
        {
          name: 'apiUrl',
          label: 'API URL',
          required: true,
          inputType: 'text',
          placeholder: 'https://api.replicate.com/v1',
          span: 12,
          suffix: '/predictions',
          help: 'config.help.llmReplicateApiUrl'
        }
      ],
      // 302.AI
      '302.AI': [
        {
          name: 'apiKey',
          label: 'API Key',
          required: true,
          inputType: 'password',
          placeholder: 'your-api-key',
          span: 12,
          help: 'config.help.llm302AIApiKey'
        },
        {
          name: 'apiUrl',
          label: 'API URL',
          required: true,
          inputType: 'text',
          placeholder: 'https://api.302.ai/v1',
          span: 12,
          suffix: '/chat/completions',
          help: 'config.help.llm302AIApiUrl'
        }
      ],
      // Fish Audio
      'Fish Audio': [
        {
          name: 'apiKey',
          label: 'API Key',
          required: true,
          inputType: 'password',
          placeholder: 'your-api-key',
          span: 12,
          help: 'config.help.llmFishAudioApiKey'
        },
        {
          name: 'apiUrl',
          label: 'API URL',
          required: true,
          inputType: 'text',
          placeholder: 'https://api.fish.audio/v1',
          span: 12,
          suffix: '/tts',
          help: 'config.help.llmFishAudioApiUrl'
        }
      ],
      // PPIO
      'PPIO': [
        {
          name: 'apiKey',
          label: 'API Key',
          required: true,
          inputType: 'password',
          placeholder: 'your-api-key',
          span: 12,
          help: 'config.help.llmPPIOApiKey'
        },
        {
          name: 'apiUrl',
          label: 'API URL',
          required: true,
          inputType: 'text',
          placeholder: 'https://api.ppio.cloud/v1',
          span: 12,
          suffix: '/chat/completions',
          help: 'config.help.llmPPIOApiUrl'
        }
      ],
      // NovitaAI
      'NovitaAI': [
        {
          name: 'apiKey',
          label: 'API Key',
          required: true,
          inputType: 'password',
          placeholder: 'your-api-key',
          span: 12,
          help: 'config.help.llmNovitaAIApiKey'
        },
        {
          name: 'apiUrl',
          label: 'API URL',
          required: true,
          inputType: 'text',
          placeholder: 'https://api.novita.ai/v3',
          span: 12,
          suffix: '/openai/chat/completions',
          help: 'config.help.llmNovitaAIApiUrl'
        }
      ],
      // GPUStack
      'GPUStack': [
        {
          name: 'apiKey',
          label: 'API Key',
          required: false,
          inputType: 'password',
          placeholder: 'your-api-key',
          span: 12,
          help: 'config.help.llmGPUStackApiKey'
        },
        {
          name: 'apiUrl',
          label: 'API URL',
          required: true,
          inputType: 'text',
          placeholder: 'http://localhost:80/v1-openai',
          span: 12,
          suffix: '/chat/completions',
          help: 'config.help.llmGPUStackApiUrl'
        }
      ],
      // Upstage
      'Upstage': [
        {
          name: 'apiKey',
          label: 'API Key',
          required: true,
          inputType: 'password',
          placeholder: 'your-api-key',
          span: 12,
          help: 'config.help.llmUpstageApiKey'
        },
        {
          name: 'apiUrl',
          label: 'API URL',
          required: true,
          inputType: 'text',
          placeholder: 'https://api.upstage.ai/v1/solar',
          span: 12,
          suffix: '/chat/completions',
          help: 'config.help.llmUpstageApiUrl'
        }
      ],
      // LeptonAI
      'LeptonAI': [
        {
          name: 'apiKey',
          label: 'API Token',
          required: true,
          inputType: 'password',
          placeholder: 'your-api-token',
          span: 12,
          help: 'config.help.llmLeptonAIApiKey'
        },
        {
          name: 'apiUrl',
          label: 'API URL',
          required: true,
          inputType: 'text',
          placeholder: 'https://api.lepton.ai/api/v1',
          span: 12,
          suffix: '/chat/completions',
          help: 'config.help.llmLeptonAIApiUrl'
        }
      ],
      // PerfXCloud
      'PerfXCloud': [
        {
          name: 'apiKey',
          label: 'API Key',
          required: true,
          inputType: 'password',
          placeholder: 'your-api-key',
          span: 12,
          help: 'config.help.llmPerfXCloudApiKey'
        },
        {
          name: 'apiUrl',
          label: 'API URL',
          required: true,
          inputType: 'text',
          placeholder: 'https://cloud.perfxlab.cn/api/v1',
          span: 12,
          suffix: '/chat/completions',
          help: 'config.help.llmPerfXCloudApiUrl'
        }
      ],
      // Google Cloud
      'Google Cloud': [
        {
          name: 'apiKey',
          label: 'API Key',
          required: true,
          inputType: 'password',
          placeholder: 'your-api-key',
          span: 12,
          help: 'config.help.llmGoogleCloudApiKey'
        },
        {
          name: 'projectId',
          label: 'Project ID',
          required: true,
          inputType: 'text',
          placeholder: 'your-project-id',
          span: 12,
          help: 'config.help.llmGoogleCloudProjectId'
        },
        {
          name: 'apiUrl',
          label: 'API URL',
          required: true,
          inputType: 'text',
          placeholder: 'https://generativelanguage.googleapis.com/v1',
          span: 12,
          suffix: '/models',
          help: 'config.help.llmGoogleCloudApiUrl'
        }
      ],
      // Bedrock (AWS)
      'Bedrock': [
        {
          name: 'apiKey',
          label: 'Access Key ID',
          required: true,
          inputType: 'password',
          placeholder: 'your-access-key-id',
          span: 12,
          help: 'config.help.llmBedrockApiKey'
        },
        {
          name: 'apiSecret',
          label: 'Secret Access Key',
          required: true,
          inputType: 'password',
          placeholder: 'your-secret-access-key',
          span: 12,
          help: 'config.help.llmBedrockApiSecret'
        },
        {
          name: 'region',
          label: 'AWS Region',
          required: true,
          inputType: 'text',
          placeholder: 'us-east-1',
          span: 12,
          help: 'config.help.llmBedrockRegion'
        }
      ],
      // CometAPI
      'CometAPI': [
        {
          name: 'apiKey',
          label: 'API Key',
          required: true,
          inputType: 'password',
          placeholder: 'your-api-key',
          span: 12,
          help: 'config.help.llmCometAPIApiKey'
        },
        {
          name: 'apiUrl',
          label: 'API URL',
          required: true,
          inputType: 'text',
          placeholder: 'https://api.comet.com/v1',
          span: 12,
          suffix: '/chat/completions',
          help: 'config.help.llmCometAPIApiUrl'
        }
      ],
      // DeerAPI
      'DeerAPI': [
        {
          name: 'apiKey',
          label: 'API Key',
          required: true,
          inputType: 'password',
          placeholder: 'your-api-key',
          span: 12,
          help: 'config.help.llmDeerAPIApiKey'
        },
        {
          name: 'apiUrl',
          label: 'API URL',
          required: true,
          inputType: 'text',
          placeholder: 'https://api.deerapi.com/v1',
          span: 12,
          suffix: '/chat/completions',
          help: 'config.help.llmDeerAPIApiUrl'
        }
      ]
    }
  },
  stt: {
    label: 'config.stt',
    permissionPrefix: 'system:config',
    typeOptions: [
      { label: 'Tencent Cloud', value: 'tencent', key: '0' },
      {
        label: 'Aliyun (DashScope)',
        value: 'aliyun',
        key: '1',
        // 8k 系列服务端会自动降采样后再送；paraformer-realtime-8k-v2 是唯一支持情感识别的 Paraformer 模型
        configNameOptions: [
          'paraformer-realtime-v2',
          'paraformer-realtime-v1',
          'paraformer-realtime-8k-v2',
          'paraformer-realtime-8k-v1',
          'fun-asr-realtime',
          'fun-asr-realtime-2025-11-07',
          'fun-asr-realtime-2025-09-15',
          'fun-asr-flash-8k-realtime',
          'fun-asr-flash-8k-realtime-2026-01-28',
          'gummy-realtime-v1',
          'gummy-chat-v1',
          'qwen3-asr-flash-realtime',
        ]
      },
      { label: 'Aliyun (NLS)', value: 'aliyun-nls', key: '2' },
      { label: 'XunFei', value: 'xfyun', key: '3' },
      { label: 'FunASR', value: 'funasr', key: '4' },
      { label: 'VolcEngine (Doubao)', value: 'volcengine', key: '5' }
    ],
    typeFields: {
      tencent: [
        { 
          name: 'appId', 
          label: 'App Id', 
          required: true, 
          span: 12,
          help: 'config.help.sttTencentAppId',
          placeholder: 'your-app-id'
        },
        { 
          name: 'apiKey', 
          label: 'Secret Id', 
          required: true, 
          span: 12,
          help: 'config.help.sttTencentApiKey',
          placeholder: 'your-secret-id'
        },
        { 
          name: 'apiSecret', 
          label: 'Secret Key', 
          required: true, 
          span: 12,
          help: 'config.help.sttTencentApiSecret',
          placeholder: 'your-secret-key'
        },
      ],
      aliyun: [
        { 
          name: 'apiKey', 
          label: 'App Key', 
          required: true, 
          span: 12,
          help: 'config.help.sttAliyunApiKey',
          placeholder: 'your-app-key'
        }
      ],
      'aliyun-nls': [
        {
          name: 'ak',
          label: 'Access Key',
          required: true,
          span: 12,
          help: 'config.help.sttAliyunNlsAk',
          placeholder: 'your-access-key'
        },
        {
          name: 'sk',
          label: 'Secret Key',
          required: true,
          inputType: 'password',
          span: 12,
          help: 'config.help.sttAliyunNlsSk',
          placeholder: 'your-secret-key'
        },
        {
          name: 'apiKey',
          label: 'App Key',
          required: true,
          span: 12,
          help: 'config.help.sttAliyunNlsApiKey',
          placeholder: 'your-app-key'
        }
      ],
      xfyun: [
        { 
          name: 'appId', 
          label: 'App Id', 
          required: true, 
          span: 12,
          help: 'config.help.sttXfyunAppId',
          placeholder: 'your-app-id'
        },
        { 
          name: 'apiSecret', 
          label: 'Api Secret', 
          required: true, 
          span: 12,
          help: 'config.help.sttXfyunApiSecret',
          placeholder: 'your-api-secret'
        },
        { 
          name: 'apiKey', 
          label: 'Api Key', 
          required: true, 
          span: 12,
          help: 'config.help.sttXfyunApiKey',
          placeholder: 'your-api-key'
        }
      ],
      funasr: [
        { 
          name: 'apiUrl', 
          label: 'Websocket URL', 
          required: true, 
          span: 12, 
          defaultUrl: "ws://127.0.0.1:10095",
          help: 'config.help.sttFunasrApiUrl',
          placeholder: 'ws://127.0.0.1:10095'
        }
      ],
      volcengine: [
        {
          name: 'apiKey',
          label: 'API Key',
          required: true,
          inputType: 'password',
          span: 12,
          help: 'config.help.sttVolcengineApiKey',
          placeholder: 'your-api-key'
        }
      ]
    }
  },
  tts: {
    label: 'config.tts',
    permissionPrefix: 'system:config',
    typeOptions: [
      { label: 'Tencent Cloud', value: 'tencent', key: '0' },
      { label: 'Aliyun (DashScope)', value: 'aliyun', key: '1' },
      { label: 'Aliyun (NLS)', value: 'aliyun-nls', key: '2' },
      { label: 'VolcEngine (Doubao)', value: 'volcengine', key: '3' },
      { label: 'XunFei', value: 'xfyun', key: '4' },
      { label: 'MiniMax', value: 'minimax', key: '5' },
      { label: 'Sherpa-ONNX', value: 'sherpa-onnx', key: '6' }
    ],
    typeFields: {
      tencent: [
        {
          name: 'appId',
          label: 'App Id',
          required: true,
          span: 12,
          help: 'config.help.ttsTencentAppId',
          placeholder: 'your-app-id'
        },
        {
          name: 'apiKey',
          label: 'Secret Id',
          required: true,
          span: 12,
          help: 'config.help.ttsTencentApiKey',
          placeholder: 'your-secret-id'
        },
        {
          name: 'apiSecret',
          label: 'Secret Key',
          required: true,
          span: 12,
          help: 'config.help.ttsTencentApiSecret',
          placeholder: 'your-secret-key'
        },
      ],
      aliyun: [
        { 
          name: 'apiKey', 
          label: 'API Key', 
          required: true, 
          span: 12,
          help: 'config.help.ttsAliyunApiKey',
          placeholder: 'your-api-key'
        }
      ],
      'aliyun-nls': [
        {
          name: 'ak',
          label: 'Access Key',
          required: true,
          span: 12,
          help: 'config.help.ttsAliyunNlsAk',
          placeholder: 'your-access-key'
        },
        {
          name: 'sk',
          label: 'Secret Key',
          required: true,
          inputType: 'password',
          span: 12,
          help: 'config.help.ttsAliyunNlsSk',
          placeholder: 'your-secret-key'
        },
        {
          name: 'apiKey',
          label: 'App Key',
          required: true,
          span: 12,
          help: 'config.help.ttsAliyunNlsApiKey',
          placeholder: 'your-app-key'
        }
      ],
      volcengine: [
        {
          name: 'apiKey',
          label: 'API Key',
          required: true,
          span: 12,
          help: 'config.help.ttsVolcengineApiKey',
          placeholder: 'your-api-key'
        }
      ],
      xfyun: [
        { 
          name: 'appId', 
          label: 'App Id', 
          required: true, 
          span: 12,
          help: 'config.help.ttsXfyunAppId',
          placeholder: 'your-app-id'
        },
        { 
          name: 'apiSecret', 
          label: 'Api Secret', 
          required: true, 
          span: 12,
          help: 'config.help.ttsXfyunApiSecret',
          placeholder: 'your-api-secret'
        },
        { 
          name: 'apiKey', 
          label: 'Api Key', 
          required: true, 
          span: 12,
          help: 'config.help.ttsXfyunApiKey',
          placeholder: 'your-api-key'
        }
      ],
      minimax: [
        { 
          name: 'appId', 
          label: 'Group Id', 
          required: true, 
          span: 12,
          help: 'config.help.ttsMinimaxAppId',
          placeholder: 'your-group-id'
        },
        { 
          name: 'apiKey', 
          label: 'API Key', 
          required: true, 
          span: 12,
          help: 'config.help.ttsMinimaxApiKey',
          placeholder: 'your-api-key'
        }
      ],
      'sherpa-onnx': [],
    }
  },
  oss: {
    label: 'config.oss',
    permissionPrefix: 'system:config',
    typeOptions: [
      { label: 'Local', value: 'local', key: '0' },
      { label: 'Tencent Cloud (COS)', value: 'tencent', key: '1' },
      { label: 'Aliyun (OSS)', value: 'aliyun', key: '2' },
      { label: 'MinIO', value: 'minio', key: '3' },
      { label: 'Cloudflare R2', value: 'r2', key: '4' },
      { label: 'Backblaze B2', value: 'b2', key: '5' },
      { label: 'Huawei Cloud (OBS)', value: 'huawei-obs', key: '6' },
      { label: 'Wasabi', value: 'wasabi', key: '7' },
      { label: 'DigitalOcean Spaces', value: 'do-spaces', key: '8' },
      { label: 'Qiniu (Kodo)', value: 'qiniu', key: '9' },
      { label: 'S3 Compatible', value: 's3', key: '10' }
    ],
    typeFields: {
      local: [],
      tencent: [
        {
          name: 'apiKey',
          label: 'SecretId',
          required: true,
          inputType: 'password',
          span: 12,
          help: 'config.help.ossTencentApiKey',
          placeholder: 'your-secret-id'
        },
        {
          name: 'apiSecret',
          label: 'SecretKey',
          required: true,
          inputType: 'password',
          span: 12,
          help: 'config.help.ossTencentApiSecret',
          placeholder: 'your-secret-key'
        },
        {
          name: 'appId',
          label: 'Region',
          required: true,
          inputType: 'text',
          span: 12,
          help: 'config.help.ossTencentAppId',
          placeholder: 'ap-guangzhou'
        },
        {
          name: 'configName',
          label: 'Bucket',
          required: true,
          inputType: 'text',
          span: 12,
          help: 'config.help.ossTencentConfigName',
          placeholder: 'my-bucket-1250000000'
        },
        {
          name: 'apiUrl',
          label: 'config.field.pathPrefix',
          required: false,
          inputType: 'text',
          span: 12,
          help: 'config.help.ossTencentApiUrl',
          placeholder: 'uploads/'
        }
      ],
      aliyun: [
        {
          name: 'ak',
          label: 'AccessKey ID',
          required: true,
          inputType: 'password',
          span: 12,
          help: 'config.help.ossAliyunAk',
          placeholder: 'your-access-key-id'
        },
        {
          name: 'sk',
          label: 'AccessKey Secret',
          required: true,
          inputType: 'password',
          span: 12,
          help: 'config.help.ossAliyunSk',
          placeholder: 'your-access-key-secret'
        },
        {
          name: 'apiUrl',
          label: 'Endpoint',
          required: true,
          inputType: 'text',
          span: 12,
          help: 'config.help.ossAliyunApiUrl',
          placeholder: 'oss-cn-hangzhou.aliyuncs.com'
        },
        {
          name: 'configName',
          label: 'Bucket',
          required: true,
          inputType: 'text',
          span: 12,
          help: 'config.help.ossAliyunConfigName',
          placeholder: 'my-bucket'
        }
      ],
      s3: s3CompatibleFields('http://host:9000', 'config.help.ossS3Endpoint'),
      minio: s3CompatibleFields('http://localhost:9000', 'config.help.ossMinioEndpoint'),
      r2: s3CompatibleFields('https://<account>.r2.cloudflarestorage.com', 'config.help.ossR2Endpoint'),
      b2: s3CompatibleFields('https://s3.us-west-002.backblazeb2.com', 'config.help.ossB2Endpoint'),
      'huawei-obs': s3CompatibleFields('https://obs.cn-north-4.myhuaweicloud.com', 'config.help.ossHuaweiObsEndpoint'),
      wasabi: s3CompatibleFields('https://s3.us-east-1.wasabisys.com', 'config.help.ossWasabiEndpoint'),
      'do-spaces': s3CompatibleFields('https://<region>.digitaloceanspaces.com', 'config.help.ossDoSpacesEndpoint'),
      qiniu: s3CompatibleFields('https://s3.cn-east-1.qiniucs.com', 'config.help.ossQiniuEndpoint')
    }
  }
};
