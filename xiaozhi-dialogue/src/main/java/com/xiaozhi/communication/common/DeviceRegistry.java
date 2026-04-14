package com.xiaozhi.communication.common;

import jakarta.annotation.Resource;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

/**
 * 设备-实例注册表。
 * 通过 Redis 维护 device → instance 映射，用于集群部署场景下：
 * <ul>
 *   <li>设备上线时绑定到当前实例</li>
 *   <li>设备下线时解绑</li>
 *   <li>心跳刷新 TTL，防止映射过期</li>
 *   <li>启动时查询属于本实例的设备（用于精准重置状态）</li>
 * </ul>
 */
@Component
public class DeviceRegistry {

    private static final String KEY_PREFIX = "xiaozhi:device:instance:";
    private static final Duration TTL = Duration.ofSeconds(300); // 5 分钟

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private InstanceIdHolder instanceIdHolder;

    /**
     * 设备上线：绑定到本实例
     */
    public void bind(String deviceId) {
        stringRedisTemplate.opsForValue().set(
                KEY_PREFIX + deviceId, instanceIdHolder.getInstanceId(), TTL);
    }

    /**
     * 设备下线：解绑
     */
    public void unbind(String deviceId) {
        stringRedisTemplate.delete(KEY_PREFIX + deviceId);
    }

    /**
     * 刷新心跳（由 InactiveSessionChecker 定期调用）
     */
    public void refresh(String deviceId) {
        stringRedisTemplate.expire(KEY_PREFIX + deviceId, TTL);
    }

    /**
     * 查询设备所在实例
     */
    public String getInstance(String deviceId) {
        return stringRedisTemplate.opsForValue().get(KEY_PREFIX + deviceId);
    }

    /**
     * 查询属于本实例的所有设备 ID。
     * 通过 SCAN 遍历 {@code xiaozhi:device:instance:*}，筛选 value 等于本实例 ID 的 key。
     */
    public Set<String> getOwnDeviceIds() {
        Set<String> ownDeviceIds = new HashSet<>();
        String ownInstanceId = instanceIdHolder.getInstanceId();
        ScanOptions options = ScanOptions.scanOptions().match(KEY_PREFIX + "*").count(100).build();
        try (Cursor<String> cursor = stringRedisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                String instanceId = stringRedisTemplate.opsForValue().get(key);
                if (ownInstanceId.equals(instanceId)) {
                    ownDeviceIds.add(key.substring(KEY_PREFIX.length()));
                }
            }
        }
        return ownDeviceIds;
    }

    /**
     * 判断设备是否属于本实例
     */
    public boolean isOwned(String deviceId) {
        return instanceIdHolder.getInstanceId().equals(getInstance(deviceId));
    }
}
