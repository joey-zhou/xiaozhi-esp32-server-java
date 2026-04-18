package com.xiaozhi.communication.registry;

import com.xiaozhi.utils.JsonUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;
/**
 * 基于 Redis 的 Dialogue 服务器注册中心实现
 * <p>
 * 使用 Redis Hash 存储所有实例信息，每个实例有独立的 TTL key 做健康检测。
 * </p>
 */
@Slf4j
@Service
public class RedisDialogueServerRegistry implements DialogueServerRegistry {

    private static final String REGISTRY_HASH_KEY = "xiaozhi:dialogue:servers";
    private static final String HEARTBEAT_KEY_PREFIX = "xiaozhi:dialogue:heartbeat:";
    private static final long HEARTBEAT_TTL_SECONDS = 60;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void register(DialogueServerInfo serverInfo) {
        serverInfo.setLastHeartbeat(System.currentTimeMillis());
        String json = JsonUtil.toJson(serverInfo);
        stringRedisTemplate.opsForHash().put(REGISTRY_HASH_KEY, serverInfo.getInstanceId(), json);
        stringRedisTemplate.opsForValue().set(
                HEARTBEAT_KEY_PREFIX + serverInfo.getInstanceId(), "1",
                HEARTBEAT_TTL_SECONDS, TimeUnit.SECONDS);
        log.info("Dialogue服务器已注册: {}", serverInfo.getInstanceId());
    }

    @Override
    public void unregister(String instanceId) {
        stringRedisTemplate.opsForHash().delete(REGISTRY_HASH_KEY, instanceId);
        stringRedisTemplate.delete(HEARTBEAT_KEY_PREFIX + instanceId);
        log.info("Dialogue服务器已注销: {}", instanceId);
    }

    @Override
    public void heartbeat(DialogueServerInfo serverInfo) {
        serverInfo.setLastHeartbeat(System.currentTimeMillis());
        String json = JsonUtil.toJson(serverInfo);
        stringRedisTemplate.opsForHash().put(REGISTRY_HASH_KEY, serverInfo.getInstanceId(), json);
        stringRedisTemplate.opsForValue().set(
                HEARTBEAT_KEY_PREFIX + serverInfo.getInstanceId(), "1",
                HEARTBEAT_TTL_SECONDS, TimeUnit.SECONDS);
    }

    @Override
    public List<DialogueServerInfo> getAvailableServers() {
        List<DialogueServerInfo> result = new ArrayList<>();
        try {
            Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(REGISTRY_HASH_KEY);
            if (entries.isEmpty()) {
                return result;
            }

            List<Map.Entry<Object, Object>> serverEntries = new ArrayList<>(entries.entrySet());
            List<String> heartbeatKeys = new ArrayList<>(serverEntries.size());
            for (Map.Entry<Object, Object> entry : serverEntries) {
                heartbeatKeys.add(HEARTBEAT_KEY_PREFIX + entry.getKey());
            }

            List<String> heartbeatValues = stringRedisTemplate.opsForValue().multiGet(heartbeatKeys);
            for (int i = 0; i < serverEntries.size(); i++) {
                Map.Entry<Object, Object> entry = serverEntries.get(i);
                String instanceId = (String) entry.getKey();
                String heartbeatValue = heartbeatValues != null && i < heartbeatValues.size() ? heartbeatValues.get(i) : null;
                if (heartbeatValue != null) {
                    DialogueServerInfo info = JsonUtil.fromJson((String) entry.getValue(), DialogueServerInfo.class);
                    if (info != null) {
                        result.add(info);
                    }
                    continue;
                }

                // 心跳过期，清理僵尸注册
                stringRedisTemplate.opsForHash().delete(REGISTRY_HASH_KEY, instanceId);
                log.info("清理过期的Dialogue服务器: {}", instanceId);
            }
        } catch (Exception e) {
            log.error("获取可用Dialogue服务器列表失败", e);
        }
        return result;
    }

    @Override
    public DialogueServerInfo selectServer() {
        List<DialogueServerInfo> servers = getAvailableServers();
        if (servers.isEmpty()) {
            return null;
        }
        // 随机负载均衡
        int index = ThreadLocalRandom.current().nextInt(servers.size());
        return servers.get(index);
    }
}
