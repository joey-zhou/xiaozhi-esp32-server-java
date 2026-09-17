package com.xiaozhi.communication.common;

import jakarta.annotation.Resource;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
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

    /** 比较后再删：绑定已经指向别的实例时不能删，取值与删除必须在一次 Redis 调用里完成 */
    private static final RedisScript<Long> UNBIND_IF_OWNED = RedisScript.of(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) end return 0",
            Long.class);

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
     * 会话收尾时解绑：绑定仍指向本实例才删。
     * 设备迁移到别的实例后旧实例才收尾时，无条件删会把新实例刚写好的路由抹掉，
     * 该设备在下次重连之前收不到任何跨实例下发的消息。
     */
    public void unbindIfOwned(String deviceId) {
        stringRedisTemplate.execute(UNBIND_IF_OWNED,
                List.of(KEY_PREFIX + deviceId), instanceIdHolder.getInstanceId());
    }

    /**
     * 批量刷新心跳（由 InactiveSessionChecker 定期调用）。
     * 走 pipeline 把所有设备的 EXPIRE 合并成一次网络往返，避免会话数变多后逐个同步请求拖慢调度线程。
     */
    public void refreshAll(Collection<String> deviceIds) {
        if (deviceIds.isEmpty()) {
            return;
        }
        stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (String deviceId : deviceIds) {
                connection.expire((KEY_PREFIX + deviceId).getBytes(StandardCharsets.UTF_8), TTL.getSeconds());
            }
            return null;
        });
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
