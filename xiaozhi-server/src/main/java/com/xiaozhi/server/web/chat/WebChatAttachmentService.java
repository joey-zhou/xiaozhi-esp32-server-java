package com.xiaozhi.server.web.chat;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.Scheduler;
import com.github.benmanes.caffeine.cache.Ticker;
import com.xiaozhi.common.exception.OperationFailedException;
import com.xiaozhi.common.exception.ResourceNotFoundException;
import com.xiaozhi.common.model.bo.MessageAttachmentBO;
import com.xiaozhi.storage.ImageUploadPolicy;
import com.xiaozhi.storage.service.StorageServiceFactory;
import com.xiaozhi.utils.DateUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

/**
 * 网页聊天收下、还没随消息发出去的附件：先存进对象存储，发消息时凭 ID 取走，过期没发的删文件。
 * 进程内状态，以 server 单实例为前提
 */
@Slf4j
@Service
public class WebChatAttachmentService {

    /** 收下到发出去的最长间隔 */
    static final Duration PENDING_TTL = Duration.ofMinutes(30);

    private static final long MAX_PENDING = 10_000;

    private static final int MAX_NAME_LENGTH = 100;

    /** @param path 对象存储路径 */
    public record Pending(String id, Integer userId, String kind, String name, String mimeType, String path) {
    }

    @Resource
    private StorageServiceFactory storageServiceFactory;
    @Resource
    private ImageUploadPolicy imageUploadPolicy;

    private final Cache<String, Pending> pending;

    public WebChatAttachmentService() {
        this(Ticker.systemTicker(), ForkJoinPool.commonPool());
    }

    WebChatAttachmentService(Ticker ticker, Executor executor) {
        this.pending = Caffeine.newBuilder()
                .ticker(ticker)
                .executor(executor)
                // 没人读写时也按时过期
                .scheduler(Scheduler.systemScheduler())
                .expireAfterWrite(PENDING_TTL)
                .maximumSize(MAX_PENDING)
                .removalListener((String id, Pending attachment, RemovalCause cause) -> {
                    if (cause.wasEvicted() && attachment != null) {
                        remove(attachment);
                    }
                })
                .build();
    }

    /**
     * 校验并存下一张图
     *
     * @throws IllegalArgumentException 不是合规的图片
     */
    public Pending hold(Integer userId, MultipartFile file) {
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("图片读取失败", e);
        }
        String mimeType = imageUploadPolicy.mimeTypeOf(content);
        String relativePath = "chat/" + DateUtils.today().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String fileName = UUID.randomUUID().toString().replace("-", "") + extensionOf(mimeType);
        String path;
        try {
            path = storageServiceFactory.getStorageService().upload(new ByteArrayInputStream(content), content.length,
                    relativePath, fileName);
        } catch (IOException e) {
            throw new OperationFailedException("图片上传失败，请稍后重试", e);
        }
        Pending held = new Pending(UUID.randomUUID().toString(), userId, MessageAttachmentBO.KIND_IMAGE,
                displayName(file.getOriginalFilename(), fileName), mimeType, path);
        pending.put(held.id(), held);
        return held;
    }

    /**
     * 取走当前用户收下的附件，之后由调用方落库或 {@link #discard}；有一个不在就一个都不取
     *
     * @throws ResourceNotFoundException 有附件过期、已发过或不是自己的
     */
    public List<Pending> take(Integer userId, List<String> attachmentIds) {
        for (String id : attachmentIds) {
            Pending held = pending.getIfPresent(id);
            if (held == null || !Objects.equals(held.userId(), userId)) {
                throw new ResourceNotFoundException("附件已过期或不存在，请重新添加");
            }
        }
        List<Pending> taken = new ArrayList<>();
        for (String id : attachmentIds) {
            Pending held = pending.asMap().remove(id);
            if (held == null) {
                // 被并发的另一个请求取走了
                discard(taken);
                throw new ResourceNotFoundException("附件已过期或不存在，请重新添加");
            }
            taken.add(held);
        }
        return taken;
    }

    /** @throws OperationFailedException 存储里读不到 */
    public byte[] read(Pending attachment) {
        byte[] content = storageServiceFactory.downloadFrom(attachment.path());
        if (content == null) {
            throw new OperationFailedException("附件读取失败，请重新添加");
        }
        return content;
    }

    /** 这一轮没聊成，删掉取走的附件 */
    public void discard(List<Pending> attachments) {
        attachments.forEach(this::remove);
    }

    /** 立刻处理到期的附件，测试用 */
    void cleanUp() {
        pending.cleanUp();
    }

    private void remove(Pending attachment) {
        try {
            storageServiceFactory.removeFrom(attachment.path());
        } catch (RuntimeException e) {
            log.warn("删除没发出去的聊天附件失败: {}", attachment.path(), e);
        }
    }

    private static String extensionOf(String mimeType) {
        return "." + mimeType.substring(mimeType.indexOf('/') + 1).replace("jpeg", "jpg");
    }

    /** 去掉目录只留文件名 */
    private static String displayName(String originalFilename, String fallback) {
        if (!StringUtils.hasText(originalFilename)) {
            return fallback;
        }
        String name = StringUtils.getFilename(originalFilename.replace('\\', '/'));
        if (!StringUtils.hasText(name)) {
            return fallback;
        }
        return name.length() > MAX_NAME_LENGTH ? name.substring(name.length() - MAX_NAME_LENGTH) : name;
    }
}
