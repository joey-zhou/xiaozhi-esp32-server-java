package com.xiaozhi.storage;

import com.xiaozhi.common.config.RuntimePathConfig;
import com.xiaozhi.communication.common.InstanceIdHolder;
import com.xiaozhi.storage.service.StorageServiceFactory;
import com.xiaozhi.utils.DateUtils;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;
/**
 * 本地存储共享性自检 — server 与 dialogue 两个进程启动时都会跑。
 * <p>
 * 本地存储把录音写在音频目录下，sys_message.audioPath 记的是相对路径，读的一方按同一个相对路径找文件。
 * 两个进程各自在不同工作目录（甚至不同机器）启动时，写进去的文件对方根本看不到，
 * 表现为对话历史录音放不出来、从消息注册声纹失败。这类问题只有用户点开历史消息才会暴露，
 * 所以在启动阶段就把它判出来：各进程先在音频目录下写一个探针文件并把自己登记到 Redis，
 * 再逐个确认其它存活实例的探针在本地可见，看不见就直接让进程起不来。
 * <p>
 * 判定一律偏保守，宁可漏判也不能误拦：Redis 读不到、存储配置读不到、探针写不进去都直接放行。
 * 只校验登记表里有记录的实例，没登记的（如尚未升级到本版本的实例）跳过，避免滚动升级期间把新实例拦停。
 * <p>
 * 登记身份取「instanceId#pid」而不是 instanceId：同一台机器上起的 server 与 dialogue，
 * instanceId 都是 hostname，只按 instanceId 登记会互相覆盖、谁也看不见谁，而这恰恰是最常见的配错形态
 * ——两个进程各自 cd 到自己的目录再启动，audio/ 落在两个地方。带上 pid 才能把这种同机不同目录抓出来。
 * <p>
 * 已知边界：实例被 kill -9 后它的登记会残留到 TTL 过期，这段时间里若该实例已被摘除，其它实例重启会因为
 * 找不到它的探针而误判，失败信息里已提示等待 TTL 过期后重试。
 */
@Slf4j
@Component(StorageSharingSelfCheck.BEAN_NAME)
public class StorageSharingSelfCheck {

    public static final String BEAN_NAME = "storageSharingSelfCheck";

    /** 探针登记表：field=instanceId#pid，value=该进程写下探针时看到的绝对路径 */
    private static final String PROBE_REGISTRY_KEY = "xiaozhi:storage:probes";

    /** 每个实例一个存活标记，过期即认为实例已下线，登记表里的残留由读取方顺手清掉 */
    private static final String PROBE_HEARTBEAT_KEY_PREFIX = "xiaozhi:storage:probe:heartbeat:";

    private static final long PROBE_TTL_SECONDS = 60;
    private static final long PROBE_REFRESH_SECONDS = 30;

    /** 探针目录名不是 ISO 日期格式，不会被 AudioCleanupTask 的按天清理扫到 */
    private static final String PROBE_DIR_NAME = ".probe";

    @Value("${xiaozhi.storage.sharing-check.enabled:true}")
    private boolean enabled;

    @Resource
    private StorageServiceFactory storageServiceFactory;

    @Resource
    private RuntimePathConfig runtimePathConfig;

    @Resource
    private InstanceIdHolder instanceIdHolder;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /** 登记与探针文件都按进程区分，同机同 hostname 的两个进程才不会互相覆盖；依赖注入完成后才求值 */
    private volatile String probeId;

    private volatile Path registeredProbe;
    private volatile ScheduledExecutorService refreshScheduler;

    @PostConstruct
    public void verify() {
        if (!enabled) {
            log.info("本地存储共享性自检已关闭(xiaozhi.storage.sharing-check.enabled=false)");
            return;
        }
        if (!isLocalStorage()) {
            return;
        }

        probeId = instanceIdHolder.getInstanceId() + "#" + ProcessHandle.current().pid();
        // 先写探针再判定：判定在前会让先启动的实例永远看不到后启动实例的探针
        Path probe = writeProbe(probeId);
        if (probe == null || !registerProbe(probeId, probe)) {
            return;
        }
        registeredProbe = probe;

        Map<String, String> peers = readPeerProbes(probeId);
        if (peers == null) {
            return;
        }

        List<String> invisible = new ArrayList<>();
        for (Map.Entry<String, String> peer : peers.entrySet()) {
            Path expected = probePathOf(peer.getKey());
            if (!Files.exists(expected)) {
                invisible.add("进程 " + peer.getKey() + " 的探针不可见：本地期望路径 " + expected
                        + "，该进程写入路径 " + peer.getValue());
            }
        }
        if (!invisible.isEmpty()) {
            String message = buildFailureMessage(invisible);
            log.error(message);
            throw new IllegalStateException(message);
        }

        log.info("本地存储共享性自检通过: probeId={}, 音频目录={}, 已确认可见的其它进程探针 {} 个",
                probeId, runtimePathConfig.resolveAudioDir(), peers.size());
        startProbeRefresh(probeId, probe);
    }

    /**
     * 退出时只撤销登记，探针文件留在原地。
     * 探针文件名只取实例标识，一个实例最多留一个文件，不会堆积；
     * 反过来若在退出时删掉它，正在启动的实例可能刚读到登记就扑空，把一次正常下线判成目录不共享。
     */
    @PreDestroy
    public void release() {
        ScheduledExecutorService scheduler = refreshScheduler;
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        if (registeredProbe == null) {
            return;
        }
        try {
            if (probeId == null) {
                return;
            }
            stringRedisTemplate.opsForHash().delete(PROBE_REGISTRY_KEY, probeId);
            stringRedisTemplate.delete(PROBE_HEARTBEAT_KEY_PREFIX + probeId);
        } catch (Exception e) {
            log.warn("注销存储探针登记失败: {}", e.getMessage());
        }
    }

    /** 云存储各实例读的是同一个桶，不存在目录共享问题；读不到存储配置时无从判断，同样跳过 */
    private boolean isLocalStorage() {
        try {
            String provider = storageServiceFactory.getStorageService().getProvider();
            if (!"local".equals(provider)) {
                log.info("当前存储为 {}，跳过本地存储共享性自检", provider);
                return false;
            }
            return true;
        } catch (Exception e) {
            log.warn("读取存储配置失败，跳过本地存储共享性自检: {}", e.getMessage());
            return false;
        }
    }

    private Path writeProbe(String id) {
        Path probe = probePathOf(id);
        try {
            Files.createDirectories(probe.getParent());
            Files.writeString(probe, "probeId=" + id + System.lineSeparator()
                    + "writtenAt=" + DateUtils.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    + System.lineSeparator());
            return probe;
        } catch (Exception e) {
            log.warn("写入存储探针失败，跳过本地存储共享性自检: path={}, {}", probe, e.getMessage());
            return null;
        }
    }

    private boolean registerProbe(String id, Path probe) {
        try {
            stringRedisTemplate.opsForHash().put(PROBE_REGISTRY_KEY, id, probe.toString());
            stringRedisTemplate.opsForValue().set(PROBE_HEARTBEAT_KEY_PREFIX + id, "1",
                    PROBE_TTL_SECONDS, TimeUnit.SECONDS);
            return true;
        } catch (Exception e) {
            log.warn("登记存储探针失败，跳过本地存储共享性自检: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 读出其它存活实例的探针登记，返回 null 表示 Redis 不可用、本轮不判定。
     * 存活标记已过期的登记属于僵尸记录，顺手清掉且不参与判定。
     */
    private Map<String, String> readPeerProbes(String selfProbeId) {
        try {
            Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(PROBE_REGISTRY_KEY);
            List<String> peerIds = new ArrayList<>();
            List<String> peerProbes = new ArrayList<>();
            List<String> heartbeatKeys = new ArrayList<>();
            for (Map.Entry<Object, Object> entry : entries.entrySet()) {
                String peerId = String.valueOf(entry.getKey());
                if (selfProbeId.equals(peerId)) {
                    continue;
                }
                peerIds.add(peerId);
                peerProbes.add(String.valueOf(entry.getValue()));
                heartbeatKeys.add(PROBE_HEARTBEAT_KEY_PREFIX + peerId);
            }

            Map<String, String> peers = new LinkedHashMap<>();
            if (peerIds.isEmpty()) {
                return peers;
            }

            List<String> heartbeats = stringRedisTemplate.opsForValue().multiGet(heartbeatKeys);
            for (int i = 0; i < peerIds.size(); i++) {
                String heartbeat = heartbeats != null && i < heartbeats.size() ? heartbeats.get(i) : null;
                if (heartbeat != null) {
                    peers.put(peerIds.get(i), peerProbes.get(i));
                    continue;
                }
                stringRedisTemplate.opsForHash().delete(PROBE_REGISTRY_KEY, peerIds.get(i));
                log.info("清理过期的存储探针登记: {}", peerIds.get(i));
            }
            return peers;
        } catch (Exception e) {
            log.warn("读取存储探针登记失败，跳过本地存储共享性自检: {}", e.getMessage());
            return null;
        }
    }

    private void startProbeRefresh(String id, Path probe) {
        refreshScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "storage-probe-refresh");
            thread.setDaemon(true);
            return thread;
        });
        refreshScheduler.scheduleAtFixedRate(() -> {
            try {
                if (!Files.exists(probe)) {
                    writeProbe(id);
                }
                registerProbe(id, probe);
            } catch (Exception e) {
                log.warn("续期存储探针失败: {}", e.getMessage());
            }
        }, PROBE_REFRESH_SECONDS, PROBE_REFRESH_SECONDS, TimeUnit.SECONDS);
    }

    private Path probePathOf(String id) {
        return runtimePathConfig.resolveAudioDir().resolve(PROBE_DIR_NAME).resolve(probeFileName(id));
    }

    /** 标识里可能带路径分隔符，收敛成单层文件名后再落盘 */
    private static String probeFileName(String id) {
        return id.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private String buildFailureMessage(List<String> invisible) {
        StringBuilder message = new StringBuilder("本地存储共享性自检未通过，进程拒绝启动：");
        message.append(System.lineSeparator()).append("当前进程=").append(probeId)
                .append("，音频目录=").append(runtimePathConfig.resolveAudioDir());
        for (String item : invisible) {
            message.append(System.lineSeparator()).append("  - ").append(item);
        }
        message.append(System.lineSeparator())
                .append("本地存储要求所有 server/dialogue 实例共享同一个音频目录；当前部署不满足，")
                .append("请把 xiaozhi.runtime.audio-dir 指向共享目录，或配置对象存储。")
                .append(System.lineSeparator())
                .append("若刚强制杀过实例(kill -9)，它的登记会残留到 ").append(PROBE_TTL_SECONDS)
                .append(" 秒 TTL 过期，请等待后重试；")
                .append("确需绕过可临时设置 xiaozhi.storage.sharing-check.enabled=false。");
        return message.toString();
    }
}
