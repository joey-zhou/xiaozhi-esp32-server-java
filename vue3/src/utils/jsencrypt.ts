import JSEncrypt from 'jsencrypt'

// 密钥对生成 http://web.chacuo.net/netrsakeypair

const publicKey =
  'MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDQJBHZKLLQKT8tIPwFMMQ8z2gh' +
  'bhu6efjO0d/WXo+KdpUxlyzwwQvMo2v62zF4tbjYJykI8ynzwRWGPkoZjFiG7Cxu' +
  'MceKE4DKo59bFDNrKhCWrJ3QmyQpk9E7WM0BfogxK8Vfb4DpvvQjn3sZMnSL4O1N' +
  'lWzIkZ4MpwVW+5HLyQIDAQAB'

const privateKey =
  'MIICeAIBADANBgkqhkiG9w0BAQEFAASCAmIwggJeAgEAAoGBANAkEdkostApPy0g' +
  '/AUwxDzPaCFuG7p5+M7R39Zej4p2lTGXLPDBC8yja/rbMXi1uNgnKQjzKfPBFYY+' +
  'ShmMWIbsLG4xx4oTgMqjn1sUM2sqEJasndCbJCmT0TtYzQF+iDErxV9vgOm+9COf' +
  'exkydIvg7U2VbMiRngynBVb7kcvJAgMBAAECgYEAncIJCShwx5fLu5/ZhPGee1zU' +
  '1ynGuINEUzX9y1RmxZL1p92mgWBAWj2vVTaX+5742FRuJMtxi8BYWSSTM2QNn/Ar' +
  'isXedSqJLwh2OWTb8k+qjY0dEZCovAYjafWpMW7dMMVGDsImPGDZlJ9z44zy606A' +
  'qEMt2yU/OBf6wPlabJECQQDn3eYx+/E2hV3P+FH+U8A1Y0Wu1EDlzydz4hiJRl5i' +
  'qRrEhTI5YvxdFN+6j1b9aKENiDH412sermSDaNxxjhuNAkEA5c3+M9TBTJlqBmUA' +
  'dn5dbjpUuB3KPtocCclRTUU4fm7OPR66mNoDoWL4iq6cIengMQXCp4YvigsidMR2' +
  'GzvELQJBAIbMrei/VVPiI1EmR9z5KdSf+0IR6gzw6znm52bffz4SnBpGaZWNY7Rl' +
  'z1Axx1wZ+Q/Z71uBOaijsJHpY8es230CQQDT2CyxnTzAn2CFGpDtqxn4Jl+5BwVN' +
  'IYXdY6/GOryUmRMYdv5vL/NO0Ezsk4CtJsuchYHnKyUh7ZfK6t0xx8vVAkB7bFzF' +
  'Kb5cejiwCL0fTT57KYz/8omSkNkI7qfYfst5VEexLChIE/ZDVRkyBepf466l4RXe' +
  'SswvOeKybPJxrNSG'

// 加密
export function encrypt(txt: string): string | false {
  const encryptor = new JSEncrypt()
  encryptor.setPublicKey(publicKey) // 设置公钥
  return encryptor.encrypt(txt) // 对数据进行加密
}

// 解密
export function decrypt(txt: string): string | false {
  const encryptor = new JSEncrypt()
  encryptor.setPrivateKey(privateKey) // 设置私钥
  return encryptor.decrypt(txt) // 对数据进行解密
}




