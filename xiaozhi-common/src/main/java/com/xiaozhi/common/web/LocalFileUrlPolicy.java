package com.xiaozhi.common.web;

import com.xiaozhi.common.config.RuntimePathConfig;
import com.xiaozhi.communication.ServerAddressProvider;
import com.xiaozhi.utils.DateUtils;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.StringJoiner;
import java.util.UUID;

/**
 * 本地存储文件的对外访问签名。
 * <p>
 * 本地存储把文件目录直接以静态资源暴露，路径由日期、设备 MAC、角色 ID、原始文件名拼成，基本可枚举，
 * 因此受保护目录下的地址一律带时效签名，未带合法签名的请求拒绝。
 * <p>
 * 签名与校验统一走这里：下发侧由 {@code LocalStorageService.getAccessUrl} 调用（与云存储的
 * 私有桶预签名对齐，接口语义一致），入站侧由静态资源拦截器调用。密钥存 Redis，多实例共享。
 */
@Component
public class LocalFileUrlPolicy {

    private static final String SIGNING_KEY_REDIS_KEY = "xiaozhi:file:url-key";

    private static final String EXPIRE_PARAM = "exp";

    private static final String SIGNATURE_PARAM = "sig";

    /** 签名有效期：覆盖一次页面浏览、一次回放或一次固件下载，过期后重新拉取列表取新地址 */
    @Value("${xiaozhi.file.url-ttl-hours:6}")
    private long urlTtlHours = 6;

    /**
     * 需要签名的目录，逗号分隔，与静态资源映射一一对应。
     * 留空表示本地文件不做访问控制。
     */
    @Value("${xiaozhi.file.protected-prefixes:}")
    private String protectedPrefixesConfig;

    /** 本地存储根目录，与 LocalStorageService 同一配置项 */
    @Value("${xiaozhi.upload-path:uploads}")
    private String uploadPath;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RuntimePathConfig runtimePathConfig;

    @Resource
    private ServerAddressProvider serverAddressProvider;

    private List<String> protectedPrefixes = List.of();

    private volatile String signingKey;

    @PostConstruct
    void initPrefixes() {
        List<String> prefixes = new ArrayList<>();
        if (protectedPrefixesConfig != null && !protectedPrefixesConfig.isBlank()) {
            for (String prefix : protectedPrefixesConfig.split(",")) {
                addPrefix(prefixes, prefix);
            }
        } else {
            addPrefix(prefixes, runtimePathConfig.getAudioDir());
            addPrefix(prefixes, uploadPath);
        }
        protectedPrefixes = List.copyOf(prefixes);
    }

    private static void addPrefix(List<String> target, String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        String prefix = raw.trim().replace('\\', '/');
        if (prefix.startsWith("/")) {
            prefix = prefix.substring(1);
        }
        if (!prefix.endsWith("/")) {
            prefix = prefix + "/";
        }
        if (!target.contains(prefix)) {
            target.add(prefix);
        }
    }

    /**
     * 是否是受保护的本地路径：云端返回的完整 URL 由对象存储自己签名，不在此处理
     */
    public boolean isProtected(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String path = value.startsWith("/") ? value.substring(1) : value;
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return false;
        }
        return protectedPrefixes.stream().anyMatch(path::startsWith);
    }

    /**
     * 受保护路径追加时效签名，其余值原样返回
     */
    public String sign(String rawValue) {
        String value = toStoredPath(rawValue);
        if (!isProtected(value)) {
            return rawValue;
        }
        String path = stripSignature(value);
        long expireAt = DateUtils.instant().plus(Duration.ofHours(urlTtlHours)).getEpochSecond();
        return path + (path.indexOf('?') >= 0 ? "&" : "?")
                + EXPIRE_PARAM + "=" + expireAt
                + "&" + SIGNATURE_PARAM + "=" + sign(relativePath(path), expireAt);
    }

    /**
     * 归一为可持久化的相对路径：指向本机受保护目录的完整 URL 剥掉协议与主机名，其余原样返回。
     * <p>
     * 上传接口下发的是带主机名的完整地址，直接入库会把部署地址写死进数据，
     * 且此后既签不上名也过不了校验，所以入库前统一在这里还原成相对路径。
     * <p>
     * 判据是「主机名等于本机对外地址」，不能只看路径前缀：对象存储的键同样以 audio/、uploads/ 开头，
     * 按前缀判断会把整条云地址截成一个假的本地相对路径，原地址就此丢失、云上对象再也定位不到。
     * 前端拿到的本地地址本来就是用 {@link ServerAddressProvider#getServerAddress()} 拼出来的，比得上。
     */
    public String toStoredPath(String value) {
        if (value == null || !(value.startsWith("http://") || value.startsWith("https://"))) {
            return value;
        }
        String base = serverAddressProvider.getServerAddress();
        if (base == null || base.isBlank() || !value.startsWith(base)) {
            return value;
        }
        String path = value.substring(base.length());
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        return isProtected(path) ? path : value;
    }

    /**
     * 去掉签名参数，还原可持久化的裸路径
     */
    public String stripSignature(String rawValue) {
        String value = toStoredPath(rawValue);
        if (value == null || value.indexOf('?') < 0 || !isProtected(value)) {
            return value;
        }
        int mark = value.indexOf('?');
        String query = value.substring(mark + 1);
        StringJoiner kept = new StringJoiner("&");
        for (String param : query.split("&")) {
            String name = param.contains("=") ? param.substring(0, param.indexOf('=')) : param;
            if (!EXPIRE_PARAM.equals(name) && !SIGNATURE_PARAM.equals(name)) {
                kept.add(param);
            }
        }
        String base = value.substring(0, mark);
        return kept.length() == 0 ? base : base + "?" + kept;
    }

    /**
     * 校验访问签名，path 为不带前导斜杠、不带查询串的相对路径。
     * 不在受保护目录下的路径直接放行。
     */
    public boolean verify(String path, String expire, String signature) {
        if (!isProtected(path)) {
            return true;
        }
        if (expire == null || signature == null) {
            return false;
        }
        long expireAt;
        try {
            expireAt = Long.parseLong(expire);
        } catch (NumberFormatException e) {
            return false;
        }
        if (DateUtils.instant().getEpochSecond() > expireAt) {
            return false;
        }
        byte[] expected = sign(relativePath(path), expireAt).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(signature.getBytes(StandardCharsets.UTF_8), expected);
    }

    /** 签名内容统一去掉前导斜杠，保证下发地址与请求地址算出同一个签名 */
    private static String relativePath(String path) {
        String value = path.startsWith("/") ? path.substring(1) : path;
        int mark = value.indexOf('?');
        return mark < 0 ? value : value.substring(0, mark);
    }

    private String sign(String path, long expireAt) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal((path + "|" + expireAt).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException("文件地址签名失败", e);
        }
    }

    /**
     * 密钥首次使用时写入 Redis，多实例竞争时以先写入的为准，重启后继续复用
     */
    private String signingKey() {
        String key = signingKey;
        if (key != null) {
            return key;
        }
        synchronized (this) {
            if (signingKey == null) {
                String candidate = UUID.randomUUID().toString().replace("-", "");
                Boolean created = stringRedisTemplate.opsForValue().setIfAbsent(SIGNING_KEY_REDIS_KEY, candidate);
                String stored = Boolean.TRUE.equals(created)
                        ? candidate
                        : stringRedisTemplate.opsForValue().get(SIGNING_KEY_REDIS_KEY);
                if (stored == null) {
                    throw new IllegalStateException("文件地址签名密钥初始化失败");
                }
                signingKey = stored;
            }
            return signingKey;
        }
    }
}
