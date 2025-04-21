export default {
  user: {
    add: "/api/user/add",
    login: "/api/user/login",
    query: "/api/user/query",
    queryUsers: "/api/user/queryUsers",
    update: "/api/user/update",
    sendEmailCaptcha: "/api/user/sendEmailCaptcha",
    checkCaptcha: "/api/user/checkCaptcha",
    checkUser: "/api/user/checkUser",
  },
  device: {
    add: "/api/device/add",
    query: "/api/device/query",
    update: "/api/device/update",
    export: "/api/device/export"
  },
  agent: {
    add: "/api/agent/add",
    query: "/api/agent/query",
    update: "/api/agent/update",
    delete: "/api/agent/delete"
  },
  role: {
    add: "/api/role/add",
    query: "/api/role/query",
    update: "/api/role/update",
    testVoice: "/api/role/testVoice"
  },
  message: {
    query: "/api/message/query",
    export: "/api/message/export"
  },
  config: {
    add: "/api/config/add",
    query: "/api/config/query",
    update: "/api/config/update"
  },
  uploadAvatar: "/api/uploadAvatar"
};
