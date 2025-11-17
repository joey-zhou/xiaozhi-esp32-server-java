/**
 * 统一的日志工具
 * 开发环境输出到控制台，生产环境可以集成日志上报服务
 */

const isDev = import.meta.env.DEV

/**
 * 日志级别
 */
export enum LogLevel {
  DEBUG = 'debug',
  INFO = 'info',
  WARN = 'warn',
  ERROR = 'error'
}

/**
 * 日志配置
 */
interface LoggerConfig {
  /** 是否启用日志 */
  enabled: boolean
  /** 最小日志级别 */
  minLevel: LogLevel
  /** 是否显示时间戳 */
  showTimestamp: boolean
  /** 是否显示日志级别 */
  showLevel: boolean
}

/**
 * 默认配置
 */
const defaultConfig: LoggerConfig = {
  enabled: isDev,
  minLevel: isDev ? LogLevel.DEBUG : LogLevel.ERROR,
  showTimestamp: true,
  showLevel: true
}

/**
 * 日志级别权重（用于比较）
 */
const levelWeight: Record<LogLevel, number> = {
  [LogLevel.DEBUG]: 0,
  [LogLevel.INFO]: 1,
  [LogLevel.WARN]: 2,
  [LogLevel.ERROR]: 3
}

/**
 * 格式化时间戳
 */
function formatTimestamp(): string {
  const now = new Date()
  return `[${now.toLocaleTimeString()}]`
}

/**
 * 格式化日志前缀
 */
function formatPrefix(level: LogLevel, config: LoggerConfig): string {
  const parts: string[] = []
  
  if (config.showTimestamp) {
    parts.push(formatTimestamp())
  }
  
  if (config.showLevel) {
    parts.push(`[${level.toUpperCase()}]`)
  }
  
  return parts.join(' ')
}

/**
 * 判断是否应该输出日志
 */
function shouldLog(level: LogLevel, config: LoggerConfig): boolean {
  if (!config.enabled) return false
  return levelWeight[level] >= levelWeight[config.minLevel]
}

/**
 * Logger 类
 */
class Logger {
  private config: LoggerConfig

  constructor(config: Partial<LoggerConfig> = {}) {
    this.config = { ...defaultConfig, ...config }
  }

  /**
   * 更新配置
   */
  configure(config: Partial<LoggerConfig>) {
    this.config = { ...this.config, ...config }
  }

  /**
   * 调试日志
   */
  debug(...args: any[]) {
    if (shouldLog(LogLevel.DEBUG, this.config)) {
      const prefix = formatPrefix(LogLevel.DEBUG, this.config)
      console.log(prefix, ...args)
    }
  }

  /**
   * 信息日志
   */
  info(...args: any[]) {
    if (shouldLog(LogLevel.INFO, this.config)) {
      const prefix = formatPrefix(LogLevel.INFO, this.config)
      console.info(prefix, ...args)
    }
  }

  /**
   * 警告日志
   */
  warn(...args: any[]) {
    if (shouldLog(LogLevel.WARN, this.config)) {
      const prefix = formatPrefix(LogLevel.WARN, this.config)
      console.warn(prefix, ...args)
    }
  }

  /**
   * 错误日志
   */
  error(...args: any[]) {
    if (shouldLog(LogLevel.ERROR, this.config)) {
      const prefix = formatPrefix(LogLevel.ERROR, this.config)
      console.error(prefix, ...args)
      
      // 在生产环境可以在这里添加错误上报逻辑
      // if (!isDev) {
      //   reportError(args)
      // }
    }
  }

  /**
   * 日志分组开始
   */
  group(label: string) {
    if (this.config.enabled) {
      console.group(label)
    }
  }

  /**
   * 日志分组结束
   */
  groupEnd() {
    if (this.config.enabled) {
      console.groupEnd()
    }
  }

  /**
   * 表格输出
   */
  table(data: any) {
    if (this.config.enabled && isDev) {
      console.table(data)
    }
  }

  /**
   * 性能计时开始
   */
  time(label: string) {
    if (this.config.enabled && isDev) {
      console.time(label)
    }
  }

  /**
   * 性能计时结束
   */
  timeEnd(label: string) {
    if (this.config.enabled && isDev) {
      console.timeEnd(label)
    }
  }
}

/**
 * 默认 logger 实例
 */
export const logger = new Logger()

/**
 * 创建命名 logger（用于特定模块）
 */
export function createLogger(moduleName: string, config?: Partial<LoggerConfig>): Logger {
  const moduleLogger = new Logger(config)
  
  // 重写方法，添加模块名前缀
  const originalMethods = {
    debug: moduleLogger.debug.bind(moduleLogger),
    info: moduleLogger.info.bind(moduleLogger),
    warn: moduleLogger.warn.bind(moduleLogger),
    error: moduleLogger.error.bind(moduleLogger)
  }
  
  moduleLogger.debug = (...args: any[]) => originalMethods.debug(`[${moduleName}]`, ...args)
  moduleLogger.info = (...args: any[]) => originalMethods.info(`[${moduleName}]`, ...args)
  moduleLogger.warn = (...args: any[]) => originalMethods.warn(`[${moduleName}]`, ...args)
  moduleLogger.error = (...args: any[]) => originalMethods.error(`[${moduleName}]`, ...args)
  
  return moduleLogger
}

/**
 * 快捷方法（兼容老代码）
 */
export const log = logger.debug.bind(logger)
export const info = logger.info.bind(logger)
export const warn = logger.warn.bind(logger)
export const error = logger.error.bind(logger)

export default logger

