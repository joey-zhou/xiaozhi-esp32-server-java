package com.xiaozhi.common.config;

import com.xiaozhi.utils.AudioUtils;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

/**
 * 运行时模型与原生库目录配置。
 * 默认保持当前相对路径约定，生产环境可改为绝对路径降低对工作目录的依赖。
 * <p>
 * 本地存储的物理落点由 {@code data-dir} 决定，而入库的存储键（如 {@code audio/2026-01-01/.../x.wav}）
 * 始终是相对的——键要用来拼 URL 和做受保护目录判定，一旦写成绝对路径就既签不上名也匹配不上静态资源映射。
 * server 与 dialogue 在各自目录下启动时，只要把 data-dir 指向同一个绝对路径，两边就能读到同一份文件。
 */
@Configuration
@ConfigurationProperties(prefix = "xiaozhi.runtime")
@Data
public class RuntimePathConfig {

    /** 本地原生库目录 */
    private String nativeLibDir = "lib";

    /** Vosk 模型目录 */
    private String voskModelDir = "models/vosk-model";

    /** Sherpa-ONNX TTS 模型根目录 */
    private String ttsModelsDir = "models/tts";

    /**
     * 本地存储的数据根目录，留空表示进程工作目录（与历史行为一致）。
     * <p>
     * 只影响文件实际落在哪里，不影响入库的存储键。多进程部署时 server 与各 dialogue 必须配成同一个绝对路径。
     */
    private String dataDir = "";

    /** 音频输出目录（对话录音、TTS 输出），须以 / 结尾 */
    private String audioDir = "audio/";

    /** 音乐文件目录 */
    private String musicDir = "uploads/music";

    /** 头像目录 */
    private String avatarDir = "avatar";

    @PostConstruct
    void initStaticPaths() {
        AudioUtils.AUDIO_PATH = audioDir;
    }

    public Path resolveNativeLibDir() {
        return Path.of(nativeLibDir).toAbsolutePath().normalize();
    }

    public Path resolveVoskModelDir() {
        return Path.of(voskModelDir).toAbsolutePath().normalize();
    }

    public Path resolveTtsModelsDir() {
        return Path.of(ttsModelsDir).toAbsolutePath().normalize();
    }

    /** 本地存储的数据根目录绝对路径 */
    public Path resolveDataDir() {
        return Path.of(dataDir == null || dataDir.isBlank() ? "." : dataDir).toAbsolutePath().normalize();
    }

    /**
     * 把存储键解析成本机实际文件位置。键是相对路径；已经是绝对路径的原样返回，
     * 兼容历史上直接以绝对路径入库的值。
     */
    public Path resolveStorageKey(String key) {
        Path path = Path.of(key);
        return path.isAbsolute() ? path.normalize() : resolveDataDir().resolve(path).normalize();
    }

    public Path resolveAudioDir() {
        return resolveStorageKey(audioDir);
    }

    public Path resolveMusicDir() {
        return Path.of(musicDir).toAbsolutePath().normalize();
    }

    public Path resolveAvatarDir() {
        return Path.of(avatarDir).toAbsolutePath().normalize();
    }
}
