package com.xiaozhi.dialogue.runtime;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 一轮用户语音的落盘结果。
 * <p>
 * 写 WAV 与上传对象存储都排到会话串行队列上异步执行，不再压在 STT 终稿到 LLM 请求之间，
 * 首字延迟里不含这段磁盘与网络 I/O。
 * <p>
 * 本轮消息落库同样排在这条队列上，且一定排在音频任务之后（音频在 STT 出终稿时入队，
 * 落库在 LLM 完成时入队），所以落库读到的必然是已回填的值，既不用等待也不会丢路径。
 * 每轮一个实例，路径随实例走，不会串到上一轮或下一轮；轮次结束即可回收，不需要注册表。
 * <p>
 * 取舍：进程关闭时队列里没跑完的落盘/上传任务随虚拟线程一起丢弃，与对话落库、AI 回复录音一致——
 * 补一层落盘重放的代价远大于丢一条录音路径。
 */
public final class UserSpeechAudio {

    /** 本地 WAV 文件路径，构造时即确定，不含 I/O */
    private final Path localPath;

    /** 持久化路径：本地为相对路径，云存储为完整 URL。落盘未完成或本轮没有音频时为 null。
     *  始终以原始字符串保存，不能经 Path.of 转换——否则 URL 的 "//" 会被规整成 "/" */
    private volatile String storedPath;

    /** 音频时长（秒），未算出为 -1。必须在上传前用本地文件算好，上传后本地文件会被删除 */
    private volatile double duration = -1;

    private final CompletableFuture<Void> saved = new CompletableFuture<>();

    public UserSpeechAudio(Path localPath) {
        this.localPath = localPath;
    }

    public Path localPath() {
        return localPath;
    }

    public String storedPath() {
        return storedPath;
    }

    public double duration() {
        return duration;
    }

    /**
     * 回填落盘结果并放行等待方。失败与"本轮没有音频"也必须调用，否则等待方只能干等到超时。
     *
     * @param storedPath 持久化路径，null 表示没有音频或落盘失败
     * @param duration   音频时长（秒），未知传 -1
     */
    public void complete(String storedPath, double duration) {
        this.storedPath = storedPath;
        this.duration = duration;
        saved.complete(null);
    }

    /**
     * 等落盘完成后取持久化路径，超时退回本地路径。
     * 供声纹注册这类只拿它当样本引用的调用方使用——拿不到云地址也好过一直等着拖慢整轮回复。
     */
    public String awaitStoredPath(Duration timeout) {
        try {
            saved.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            return storedPath;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            // 超时或落盘任务异常，下面按本地路径兜底
        }
        return localPath != null ? localPath.toString() : null;
    }
}
