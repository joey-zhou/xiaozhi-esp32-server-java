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
  authRole: {
    query: '/auth-role',
    permissions: '/auth-role',
  },
  device: {
    add: '/device',
    query: '/device',
    update: '/device',
    delete: '/device',
    export: '/device/export',
  },
  agent: {
    query: '/agent',
  },
  role: {
    add: '/role',
    query: '/role',
    update: '/role',
    delete: '/role',
    testVoice: '/role/testVoice',
    sherpaVoices: '/role/sherpaVoices',
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
    conversations: '/message/conversations',
  },
  config: {
    add: '/config',
    query: '/config',
    update: '/config',
    delete: '/config',
  },
  mcpTool: {
    toggleRoleTool: '/mcpTool/role',         // PATCH /mcpTool/role/{roleId}/tools
    toggleGlobalTool: '/mcpTool/global',     // PATCH /mcpTool/global/tools
    batchExclude: '/mcpTool/role',           // POST /mcpTool/role/{roleId}/exclude-tools
    disabledTools: '/mcpTool/role',          // GET /mcpTool/role/{roleId}/disabled-tools
    systemGlobalTools: '/mcpTool/system-global',
  },
  upload: '/file/upload',
  memory: {
    summary: '/memory/summary',
  },
  // Web 聊天 API
  chat: {
    open: '/chat/open',
    stream: '/chat/stream',
    close: '/chat/close',
  },
}
