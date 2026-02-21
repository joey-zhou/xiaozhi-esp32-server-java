export default {
  user: {
    add: '/user',
    login: '/user/login',
    telLogin: '/user/tel-login',
    checkToken: '/user/check-token',
    refreshToken: '/user/refresh-token',
    query: '/user',
    update: '/user',
    resetPassword: '/user/resetPassword',
    sendEmailCaptcha: '/user/sendEmailCaptcha',
    sendSmsCaptcha: '/user/sendSmsCaptcha',
    checkCaptcha: '/user/checkCaptcha',
    checkUser: '/user/checkUser',
  },
  device: {
    add: '/device',
    query: '/device',
    update: '/device',
    delete: '/device',
    export: '/device/export',
  },
  agent: {
    add: '/agent',
    query: '/agent',
    update: '/agent',
    delete: '/agent',
  },
  role: {
    add: '/role',
    query: '/role',
    update: '/role',
    delete: '/role',
    testVoice: '/role/testVoice',
  },
  template: {
    query: '/template',
    add: '/template',
    update: '/template',
    delete: '/template',
  },
  message: {
    query: '/message',
    update: '/message',
    delete: '/message',
    export: '/message/export',
  },
  config: {
    add: '/config',
    query: '/config',
    update: '/config',
    getModels: '/config/getModels',
  },
  mcpTool: {
    toggleStatus: '/mcpTool/toggleStatus',
    toggleGlobalStatus: '/mcpTool/toggleGlobalStatus',
    batchSetExcludeTools: '/mcpTool/batchSetExcludeTools',
    getDisabledTools: '/mcpTool/getDisabledTools',
    getSystemGlobalTools: '/mcpTool/getSystemGlobalTools',
    refreshCache: '/mcpTool/refreshCache',
  },
  upload: '/api/file/upload',
  memory: {
    summary: '/memory/summary',
  },
}




