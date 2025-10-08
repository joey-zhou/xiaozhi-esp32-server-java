declare module 'jsencrypt' {
  export default class JSEncrypt {
    setPublicKey(key: string): void
    setPrivateKey(key: string): void
    encrypt(text: string): string | false
    decrypt(text: string): string | false
  }
}




