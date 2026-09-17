/**
 * 后端接口路径表
 *
 * 同一资源的增删改查共用一个 root，子资源路径由各 service 自行拼接。
 * 不按 CRUD 动作起别名——别名值全都一样，只会让人误以为 query 常量不能拿来发 POST。
 * 只登记后端真实存在的路径，加之前先去对应 Controller 核对。
 */
export default {
  user: {
    root: '/user',
    login: '/user/login',
    telLogin: '/user/tel-login',
    checkToken: '/user/check-token',
    logout: '/user/logout',
    resetPassword: '/user/resetPassword',
    sendEmailCaptcha: '/user/sendEmailCaptcha',
    sendSmsCaptcha: '/user/sendSmsCaptcha',
    checkUser: '/user/checkUser',
  },
  authRole: '/auth-role',
  // 设备的发消息/暂停/继续/停止等动作都是 /device/{id}/xxx 子资源
  device: '/device',
  agent: '/agent',
  role: {
    root: '/role',
    testVoice: '/role/testVoice',
    sherpaVoices: '/role/sherpaVoices',
    localStt: '/role/localStt',
  },
  template: '/template',
  message: {
    root: '/message',
  },
  // 网页聊天会话：GET 列表、PATCH /{sessionId} 重命名、DELETE 批量删除
  conversation: '/conversations',
  config: {
    root: '/config',
    test: '/config/test',
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
  chat: {
    open: '/chat/open',
    stream: '/chat/stream',
    close: '/chat/close',
  },
}
