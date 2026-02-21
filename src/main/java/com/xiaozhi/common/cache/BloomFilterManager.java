package com.xiaozhi.common.cache;

import com.xiaozhi.dao.DeviceMapper;
import com.xiaozhi.entity.SysDevice;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 布隆过滤器管理器
 * 用于防止不存在的设备ID穿透缓存
 *
 * @author Joey
 */
@Component
public class BloomFilterManager {

    private static final Logger logger = LoggerFactory.getLogger(BloomFilterManager.class);

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private DeviceMapper deviceMapper;

    private RBloomFilter<String> deviceIdBloomFilter;

    /**
     * 初始化布隆过滤器
     * 应用启动时加载所有设备ID
     */
    @PostConstruct
    public void init() {
        try {
            deviceIdBloomFilter = redissonClient.getBloomFilter("xiaozhi:bloom:deviceId");

            // 检查是否已初始化
            if (!deviceIdBloomFilter.isExists()) {
                // 预期100万设备, 误判率0.01% (万分之一)
                deviceIdBloomFilter.tryInit(1000000L, 0.0001);
                logger.info("布隆过滤器初始化成功 - 预期元素数: 1000000, 误判率: 0.01%");
                loadAllDeviceIds();
            } else {
                // 验证已有过滤器配置是否兼容，不兼容则重建
                try {
                    deviceIdBloomFilter.contains("__probe__");
                    logger.info("布隆过滤器已存在,当前元素数: {}", deviceIdBloomFilter.count());
                } catch (Exception configMismatch) {
                    logger.warn("布隆过滤器配置已变更，重新初始化: {}", configMismatch.getMessage());
                    deviceIdBloomFilter.delete();
                    deviceIdBloomFilter = redissonClient.getBloomFilter("xiaozhi:bloom:deviceId");
                    deviceIdBloomFilter.tryInit(1000000L, 0.0001);
                    logger.info("布隆过滤器重新初始化成功");
                    loadAllDeviceIds();
                }
            }

        } catch (Exception e) {
            logger.error("布隆过滤器初始化失败", e);
        }
    }

    /**
     * 加载所有设备ID到布隆过滤器
     */
    private void loadAllDeviceIds() {
        try {
            SysDevice query = new SysDevice();
            List<SysDevice> devices = deviceMapper.query(query);

            int count = 0;
            for (SysDevice device : devices) {
                if (device.getDeviceId() != null) {
                    deviceIdBloomFilter.add(device.getDeviceId());
                    count++;
                }
            }

            logger.info("已加载 {} 个设备ID到布隆过滤器", count);

        } catch (Exception e) {
            logger.error("加载设备ID到布隆过滤器失败", e);
        }
    }

    /**
     * 检查设备ID是否可能存在
     *
     * @param deviceId 设备ID
     * @return true-可能存在, false-一定不存在
     */
    public boolean mightContain(String deviceId) {
        if (deviceId == null || deviceIdBloomFilter == null) {
            return true;  // 安全降级,允许查询
        }

        try {
            return deviceIdBloomFilter.contains(deviceId);
        } catch (Exception e) {
            logger.error("布隆过滤器查询失败: {}", deviceId, e);
            return true;  // 发生异常时降级,允许查询
        }
    }

    /**
     * 添加新设备ID到布隆过滤器
     *
     * @param deviceId 设备ID
     */
    public void addDeviceId(String deviceId) {
        if (deviceId == null || deviceIdBloomFilter == null) {
            return;
        }

        try {
            deviceIdBloomFilter.add(deviceId);
            logger.debug("设备ID已添加到布隆过滤器: {}", deviceId);
        } catch (Exception e) {
            logger.error("添加设备ID到布隆过滤器失败: {}", deviceId, e);
        }
    }

    /**
     * 重新加载所有设备ID
     * 在数据迁移或布隆过滤器重置后调用
     */
    public void reload() {
        try {
            if (deviceIdBloomFilter != null) {
                deviceIdBloomFilter.delete();
            }

            deviceIdBloomFilter = redissonClient.getBloomFilter("xiaozhi:bloom:deviceId");
            deviceIdBloomFilter.tryInit(1000000L, 0.0001);

            loadAllDeviceIds();

            logger.info("布隆过滤器已重新加载");
        } catch (Exception e) {
            logger.error("重新加载布隆过滤器失败", e);
        }
    }

    /**
     * 获取布隆过滤器统计信息
     *
     * @return 统计信息
     */
    public String getStats() {
        if (deviceIdBloomFilter == null) {
            return "布隆过滤器未初始化";
        }

        try {
            long count = deviceIdBloomFilter.count();
            long expectedInsertions = deviceIdBloomFilter.getExpectedInsertions();
            double falseProbability = deviceIdBloomFilter.getFalseProbability();

            return String.format("布隆过滤器统计 - 当前元素: %d, 预期容量: %d, 误判率: %.4f%%",
                count, expectedInsertions, falseProbability * 100);
        } catch (Exception e) {
            logger.error("获取布隆过滤器统计信息失败", e);
            return "获取统计信息失败";
        }
    }
}
