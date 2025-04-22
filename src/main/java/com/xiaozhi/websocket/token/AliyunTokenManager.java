package com.xiaozhi.websocket.token;

import com.xiaozhi.entity.SysConfig;
import com.xiaozhi.service.SysConfigService;
import com.xiaozhi.utils.AliyunAccessToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 阿里云Token管理器
 * 负责管理阿里云NLS服务的Token
 */
@Component
public class AliyunTokenManager implements TokenManager {
    private static final Logger logger = LoggerFactory.getLogger(AliyunTokenManager.class);
    
    // Token缓存，以configId为键
    private final Map<Integer, String> tokenCache = new ConcurrentHashMap<>();
    // Token过期时间缓存，以configId为键
    private final Map<Integer, Long> expirationCache = new ConcurrentHashMap<>();
    // 用于确保并发安全的锁，以configId为键
    private final Map<Integer, ReentrantLock> tokenLocks = new ConcurrentHashMap<>();
    // 配置缓存，以configId为键
    private final Map<Integer, SysConfig> configCache = new ConcurrentHashMap<>();
    
    // 提前刷新时间（毫秒），设置为1小时
    private static final long REFRESH_BEFORE_EXPIRY = 3600000;
    
    @Autowired
    private SysConfigService configService;
    
    /**
     * 系统启动时初始化所有Token
     */
    // @PostConstruct
    public void init() {
        logger.info("初始化阿里云Token管理器");
        initializeAllTokens();
    }
    
    /**
     * 初始化所有需要的Token
     */
    @Override
    public void initializeAllTokens() {
        try {
            // 查询所有阿里云相关的配置
            List<SysConfig> aliyunConfigs = configService.query(new SysConfig().setProvider("aliyun"));
            if (aliyunConfigs == null || aliyunConfigs.isEmpty()) {
                logger.info("没有找到阿里云配置，跳过Token初始化");
                return;
            }

            logger.info("开始初始化 {} 个阿里云配置的Token", aliyunConfigs.size());
            for (SysConfig config : aliyunConfigs) {
                try {
                    // 检查配置是否与设备关联
                    if ((config.getDeviceId() == null || config.getDeviceId().isEmpty()) && (config.getRoleId() == null)) {
                        logger.info("配置ID: {} 没有关联设备，跳过Token初始化", config.getConfigId());
                        continue;
                    }
                    
                    Integer configId = config.getConfigId();
                    // 缓存配置信息
                    configCache.put(configId, config);
                    // 为每个配置创建一个锁
                    tokenLocks.putIfAbsent(configId, new ReentrantLock());
                    // 刷新Token
                    refreshToken(config);
                } catch (Exception e) {
                    logger.error("初始化配置ID: {} 的Token失败: {}", config.getConfigId(), e.getMessage(), e);
                }
            }
            logger.info("阿里云Token初始化完成");
        } catch (Exception e) {
            logger.error("初始化阿里云Token时发生错误: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 定时任务，每小时检查并刷新即将过期的Token
     */
    @Scheduled(fixedRate = 3600000) // 每小时执行一次
    public void scheduleTokenRefresh() {
        logger.info("开始定时检查Token过期情况");
        try {
            for (Integer configId : tokenCache.keySet()) {
                if (isTokenExpiringSoon(configId)) {
                    logger.info("配置ID: {} 的Token即将过期，开始刷新", configId);
                    
                    // 从缓存获取配置信息
                    SysConfig config = configCache.get(configId);
                    if (config == null) {
                        // 如果缓存中没有，则从数据库重新获取
                        config = configService.selectConfigById(configId);
                        if (config == null) {
                            logger.error("找不到配置ID: {}, 无法刷新Token", configId);
                            continue;
                        }
                        // 更新缓存
                        configCache.put(configId, config);
                    }
                    
                    // 刷新Token
                    refreshToken(config);
                }
            }
        } catch (Exception e) {
            logger.error("定时刷新Token时发生错误: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 获取指定配置的有效Token
     * @param config 配置对象
     * @return Token字符串，如果无效则返回null
     */
    @Override
    public String getValidToken(SysConfig config) {
        Integer configId = config.getConfigId();
        // 缓存配置信息
        configCache.put(configId, config);
        
        // 如果Token不存在或即将过期，则刷新
        if (!tokenCache.containsKey(configId) || isTokenExpiringSoon(configId)) {
            return refreshToken(config);
        }
        return tokenCache.get(configId);
    }
    
    /**
     * 刷新指定配置的Token
     * @param config 配置对象
     * @return 刷新后的Token字符串
     */
    @Override
    public String refreshToken(SysConfig config) {
        Integer configId = config.getConfigId();
        // 获取该配置的锁
        ReentrantLock lock = tokenLocks.computeIfAbsent(configId, k -> new ReentrantLock());
        
        // 尝试获取锁，避免并发刷新同一个Token
        if (lock.tryLock()) {
            try {
                // 再次检查Token是否已经被其他线程刷新
                if (tokenCache.containsKey(configId) && !isTokenExpiringSoon(configId)) {
                    return tokenCache.get(configId);
                }
                
                // 获取新Token
                Map<String, String> tokenInfo = AliyunAccessToken.createToken(config.getAppId(), config.getApiSecret());
                if (tokenInfo == null || !tokenInfo.containsKey("token")) {
                    logger.error("获取配置ID: {} 的Token失败", configId);
                    return null;
                }
                
                String token = tokenInfo.get("token");
                long expireTime = Long.parseLong(tokenInfo.get("expireTime"));
                
                // 更新缓存
                tokenCache.put(configId, token);
                expirationCache.put(configId, expireTime);
                
                return token;
            } catch (Exception e) {
                logger.error("刷新配置ID: {} 的Token时发生错误: {}", configId, e.getMessage(), e);
                return null;
            } finally {
                lock.unlock();
            }
        } else {
            // 如果无法获取锁，等待其他线程刷新完成
            logger.info("配置ID: {} 的Token正在被其他线程刷新，等待完成", configId);
            try {
                // 等待一小段时间
                Thread.sleep(100);
                // 重新获取Token
                return getValidToken(config);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("等待Token刷新时被中断", e);
                return null;
            }
        }
    }
    
    /**
     * 检查Token是否即将过期
     * @param configId 配置ID
     * @return 如果Token即将过期返回true，否则返回false
     */
    @Override
    public boolean isTokenExpiringSoon(Integer configId) {
        if (!expirationCache.containsKey(configId)) {
            return true;
        }
        
        long expireTime = expirationCache.get(configId);
        long currentTime = System.currentTimeMillis() / 1000; // 转换为秒
        
        // 如果Token将在REFRESH_BEFORE_EXPIRY时间内过期，则视为即将过期
        return (expireTime - currentTime) * 1000 < REFRESH_BEFORE_EXPIRY;
    }
}