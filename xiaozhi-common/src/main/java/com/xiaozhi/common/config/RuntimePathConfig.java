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

    public Path resolveAudioDir() {
        return Path.of(audioDir).toAbsolutePath().normalize();
    }

    public Path resolveMusicDir() {
        return Path.of(musicDir).toAbsolutePath().normalize();
    }

    public Path resolveAvatarDir() {
        return Path.of(avatarDir).toAbsolutePath().normalize();
    }
}
