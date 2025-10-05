/**
 * 系统中各类服务提供商配置
 * 统一管理各类服务的提供商信息，便于维护和扩展
 */

// 配置类型信息映射
export const configTypeMap = {
  llm: {
    label: '模型',
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
          help: '在 https://platform.openai.com/api-keys 申请'
        },
        {
          name: 'apiUrl',
          label: 'API URL',
          required: true,
          inputType: 'text',
          placeholder: 'https://api.openai.com/v1',
          span: 12,
          suffix: '/chat/completions',
          help: 'OpenAI官方地址或代理地址'
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
          help: '在 Azure 门户中申请'
        },
        {
          name: 'apiUrl',
          label: 'API URL',
          required: true,
          inputType: 'text',
          placeholder: 'https://your-resource-name.openai.azure.com',
          span: 12,
          suffix: '/chat/completions',
          help: 'Azure OpenAI 服务地址'
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
          help: '在 https://x.ai/api-keys 申请'
        },
        {
          name: 'apiUrl',
          label: 'API URL',
          required: true,
          inputType: 'text',
          placeholder: 'https://api.x.ai/v1',
          span: 12,
          suffix: '/chat/completions',
          help: 'xAI API 接口地址'
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
          help: '在 https://bailian.console.aliyun.com/?apiKey=1#/api-key 申请'
        },
        {
          name: 'apiUrl',
          label: 'API URL',
          required: true,
          inputType: 'text',
          placeholder: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
          span: 12,
          suffix: '/chat/completions',
          help: '阿里云通义千问 API 接口地址'
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
          help: '在 https://bigmodel.cn/usercenter/proj-mgmt/apikeys 申请'
        },
        {
          name: 'apiUrl',
          label: 'API URL',
          required: true,
          inputType: 'text',
          placeholder: 'https://open.bigmodel.cn/api/paas/v4',
          span: 12,
          suffix: '/chat/completions',
          help: '智谱AI大模型 API 接口地址'
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
          help: '在 https://platform.deepseek.com/ 申请'
        },
        {
          name: 'apiUrl',
          label: 'API URL',
          required: true,
          inputType: 'text',
          placeholder: 'https://api.deepseek.com',
          span: 12,
          suffix: '/chat/completions',
          help: 'DeepSeek API 接口地址'
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
          help: '在 https://console.volcengine.com/ark/region:ark+cn-beijing/apiKey 申请'
        },
        {
          name: 'apiUrl',
          label: 'API URL',
          required: true,
          inputType: 'text',
          placeholder: 'https://ark.cn-beijing.volces.com/api/v3',
          span: 12,
          suffix: '/chat/completions',
          help: '火山引擎豆包大模型 API 接口地址'
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
          help: '在百度AI开放平台申请'
        },
        {
          name: 'apiUrl',
          label: 'API URL',
          required: true,
          inputType: 'text',
          placeholder: 'https://api.baichuan-ai.com/v1',
          span: 12,
          suffix: '/chat/completions',
          help: '百川智能 API 接口地址'
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
          help: '在 https://platform.minimaxi.com/ 申请'
        },
        {
          name: 'apiUrl',
          label: 'API URL',
          required: true,
          inputType: 'text',
          placeholder: 'https://api.minimax.chat/v1',
          span: 12,
          suffix: '/chat/completions',
          help: 'MiniMax API 接口地址'
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
          help: '在 https://console.mistral.ai/ 申请'
        },
        {
          name: 'apiUrl',
          label: 'API URL',
          required: true,
          inputType: 'text',
          placeholder: 'https://api.mistral.ai/v1',
          span: 12,
          suffix: '/chat/completions',
          help: 'Mistral API 接口地址'
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
          help: '在 https://aistudio.google.com/apikey 申请'
        },
        {
          name: 'apiUrl',
          label: 'API URL',
          required: true,
          inputType: 'text',
          placeholder: 'https://generativelanguage.googleapis.com',
          span: 12,
          suffix: '/chat/completions',
          help: 'Google Gemini API 接口地址'
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
          help: '在 https://console.groq.com/ 申请'
        },
        {
          name: 'apiUrl',
          label: 'API URL',
          required: true,
          inputType: 'text',
          placeholder: 'https://api.groq.com/openai/v1',
          span: 12,
          suffix: '/chat/completions',
          help: 'Groq API 接口地址'
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
          help: '在 https://openrouter.ai/ 申请'
        },
        {
          name: 'apiUrl',
          label: 'API URL',
          required: true,
          inputType: 'text',
          placeholder: 'https://openrouter.ai/api/v1',
          span: 12,
          suffix: '/chat/completions',
          help: 'OpenRouter API 接口地址'
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
          help: '在 StepFun 平台申请'
        },
        {
          name: 'apiUrl',
          label: 'API URL',
          required: true,
          inputType: 'text',
          placeholder: 'https://api.stepfun.com/v1',
          span: 12,
          suffix: '/chat/completions',
          help: 'StepFun API 接口地址'
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
          help: '在 NVIDIA AI Foundation 申请'
        },
        {
          name: 'apiUrl',
          label: 'API URL',
          required: true,
          inputType: 'text',
          placeholder: 'https://integrate.api.nvidia.com/v1',
          span: 12,
          suffix: '/chat/completions',
          help: 'NVIDIA API 接口地址'
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
          help: '在 https://platform.01.ai/ 申请'
        },
        {
          name: 'apiUrl',
          label: 'API URL',
          required: true,
          inputType: 'text',
          placeholder: 'https://api.01.ai/v1',
          span: 12,
          suffix: '/chat/completions',
          help: '01.AI API 接口地址'
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
          help: '在 https://console.anthropic.com/ 申请'
        },
        {
          name: 'apiUrl',
          label: 'API URL',
          required: true,
          inputType: 'text',
          placeholder: 'https://api.anthropic.com/v1',
          span: 12,
          suffix: '/messages',
          help: 'Anthropic API 接口地址'
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
          help: '在 https://dash.voyageai.com/ 申请'
        },
        {
          name: 'apiUrl',
          label: 'API URL',
          required: true,
          inputType: 'text',
          placeholder: 'https://api.voyageai.com/v1',
          span: 12,
          suffix: '/chat/completions',
          help: 'Voyage AI API 接口地址'
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
          help: '在 https://ai.gitee.com/ 平台申请'
        },
        {
          name: 'apiUrl',
          label: 'API URL',
          required: true,
          inputType: 'text',
          placeholder: 'https://ai.gitee.com/v1',
          span: 12,
          suffix: '/chat/completions',
          help: 'GiteeAI API 接口地址'
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
          help: '在 https://deepinfra.com/ 申请'
        },
        {
          name: 'apiUrl',
          label: 'API URL',
          required: true,
          inputType: 'text',
          placeholder: 'https://api.deepinfra.com/v1',
          span: 12,
          suffix: '/chat/completions',
          help: 'DeepInfra API 接口地址'
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
          help: '本地 Ollama 服务地址，需要先安装并启动 Ollama'
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
          help: '本地 LocalAI 服务密钥（可选）'
        },
        {
          name: 'apiUrl',
          label: 'API URL',
          required: true,
          inputType: 'text',
          placeholder: 'http://localhost:8080/v1',
          span: 12,
          suffix: '/chat/completions',
          help: '本地 LocalAI 服务地址'
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
          help: '本地 VLLM 服务地址'
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
          help: '本地 Xinference 服务密钥（可选）'
        },
        {
          name: 'apiUrl',
          label: 'API URL',
          required: true,
          inputType: 'text',
          placeholder: 'http://localhost:9997/v1',
          span: 12,
          suffix: '/chat/completions',
          help: '本地 Xinference 服务地址'
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
          help: '本地 LM Studio 服务地址'
        }
      ]
    }
  },
  stt: {
    label: '语音识别',
    typeOptions: [
      { label: 'Tencent', value: 'tencent', key: '0' },
      { label: 'Aliyun', value: 'aliyun', key: '1' },
      { label: 'Xfyun', value: 'xfyun', key: '2' },
      { label: 'FunASR', value: 'funasr', key: '3' }
    ],
    typeFields: {
      tencent: [
        { 
          name: 'appId', 
          label: 'App Id', 
          required: true, 
          span: 12,
          help: '在 https://console.cloud.tencent.com/cam/capi 申请',
          placeholder: 'your-app-id'
        },
        { 
          name: 'apiKey', 
          label: 'Secret Id', 
          required: true, 
          span: 12,
          help: '腾讯云API密钥ID',
          placeholder: 'your-secret-id'
        },
        { 
          name: 'apiSecret', 
          label: 'Secret Key', 
          required: true, 
          span: 12,
          help: '腾讯云API密钥Key',
          placeholder: 'your-secret-key'
        },
      ],
      aliyun: [
        { 
          name: 'apiKey', 
          label: 'App Key', 
          required: true, 
          span: 12,
          help: '在 https://bailian.console.aliyun.com/?apiKey=1#/api-key 申请',
          placeholder: 'your-app-key'
        }
      ],
      xfyun: [
        { 
          name: 'appId', 
          label: 'App Id', 
          required: true, 
          span: 12,
          help: '在 https://console.xfyun.cn/ 申请讯飞开放平台AppID',
          placeholder: 'your-app-id'
        },
        { 
          name: 'apiSecret', 
          label: 'Api Secret', 
          required: true, 
          span: 12,
          help: '讯飞开放平台API Secret',
          placeholder: 'your-api-secret'
        },
        { 
          name: 'apiKey', 
          label: 'Api Key', 
          required: true, 
          span: 12,
          help: '讯飞开放平台API Key',
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
          help: '本地FunASR服务WebSocket地址，需要先部署FunASR服务',
          placeholder: 'ws://127.0.0.1:10095'
        }
      ]
    }
  },
  tts: {
    label: '语音合成',
    typeOptions: [
      { label: 'Aliyun', value: 'aliyun', key: '0' },
      { label: 'Volcengine(doubao)', value: 'volcengine', key: '1' },
      { label: 'Xfyun', value: 'xfyun', key: '2' },
      { label: 'Minimax', value: 'minimax', key: '3' }
    ],
    typeFields: {
      aliyun: [
        { 
          name: 'apiKey', 
          label: 'API Key', 
          required: true, 
          span: 12,
          help: '在 https://bailian.console.aliyun.com/?apiKey=1#/api-key 申请',
          placeholder: 'your-api-key'
        }
      ],
      volcengine: [
        { 
          name: 'appId', 
          label: 'App Id', 
          required: true, 
          span: 12,
          help: '在 https://console.volcengine.com/speech/app 申请',
          placeholder: 'your-app-id'
        },
        { 
          name: 'apiKey', 
          label: 'Access Token', 
          required: true, 
          span: 12,
          help: '火山引擎语音合成服务访问令牌',
          placeholder: 'your-access-token'
        }
      ],
      xfyun: [
        { 
          name: 'appId', 
          label: 'App Id', 
          required: true, 
          span: 12,
          help: '在 https://console.xfyun.cn/ 申请讯飞开放平台AppID',
          placeholder: 'your-app-id'
        },
        { 
          name: 'apiSecret', 
          label: 'Api Secret', 
          required: true, 
          span: 12,
          help: '讯飞开放平台API Secret',
          placeholder: 'your-api-secret'
        },
        { 
          name: 'apiKey', 
          label: 'Api Key', 
          required: true, 
          span: 12,
          help: '讯飞开放平台API Key',
          placeholder: 'your-api-key'
        }
      ],
      minimax: [
        { 
          name: 'appId', 
          label: 'Group Id', 
          required: true, 
          span: 12,
          help: '在 https://platform.minimaxi.com/user-center/basic-information 获取',
          placeholder: 'your-group-id'
        },
        { 
          name: 'apiKey', 
          label: 'API Key', 
          required: true, 
          span: 12,
          help: '在 https://platform.minimaxi.com/user-center/basic-information/interface-key 申请',
          placeholder: 'your-api-key'
        }
      ],
    }
  }
};