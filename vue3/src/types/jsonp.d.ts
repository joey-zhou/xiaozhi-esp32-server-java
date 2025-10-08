declare module 'jsonp' {
  interface JsonpOptions {
    param?: string
    timeout?: number
    prefix?: string
    name?: string
  }

  function jsonp(
    url: string,
    options: JsonpOptions,
    callback: (err: Error | null, data: any) => void
  ): void

  export = jsonp
}
