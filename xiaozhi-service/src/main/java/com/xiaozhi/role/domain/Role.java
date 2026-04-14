package com.xiaozhi.role.domain;

import com.xiaozhi.role.domain.vo.AudioConfig;
import com.xiaozhi.role.domain.vo.LlmConfig;
import com.xiaozhi.role.domain.vo.MemoryStrategy;
import com.xiaozhi.role.domain.vo.VoiceConfig;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


/**
 * Role 聚合根。
 * <p>
 * 职责：持有角色配置（LLM / 语音 / VAD / 记忆策略），
 * 通过行为方法修改状态，收集领域信号供 Repository 发布事件。
 */
@Getter
public class Role {

    /** 领域信号 */
    public enum DomainSignal { UPDATED }

    // --- Identity ---
    private Integer roleId;

    // --- Basic info ---
    private Integer userId;
    private String avatar;
    private String roleName;
    private String roleDesc;
    private String state;
    private boolean isDefault;

    // --- Value objects (grouping flat DB columns) ---
    private LlmConfig llmConfig;
    private VoiceConfig voiceConfig;
    private AudioConfig audioConfig;
    private MemoryStrategy memoryStrategy;

    // --- Timestamps ---
    private final LocalDateTime createTime;
    private LocalDateTime updateTime;

    private final List<DomainSignal> signals = new ArrayList<>();

    /** 从持久层重建聚合根（Repository 专用） */
    public Role(Integer roleId, Integer userId, String avatar, String roleName, String roleDesc,
                String state, boolean isDefault,
                LlmConfig llmConfig, VoiceConfig voiceConfig,
                AudioConfig audioConfig, MemoryStrategy memoryStrategy,
                LocalDateTime createTime, LocalDateTime updateTime) {
        this.roleId = roleId;
        this.userId = userId;
        this.avatar = avatar;
        this.roleName = roleName;
        this.roleDesc = roleDesc;
        this.state = state;
        this.isDefault = isDefault;
        this.llmConfig = llmConfig != null ? llmConfig : LlmConfig.defaults();
        this.voiceConfig = voiceConfig != null ? voiceConfig : VoiceConfig.defaults();
        this.audioConfig = audioConfig != null ? audioConfig : AudioConfig.defaults();
        this.memoryStrategy = memoryStrategy != null ? memoryStrategy : MemoryStrategy.defaults();
        this.createTime = createTime;
        this.updateTime = updateTime;
    }

    /** 工厂方法：创建新角色 */
    public static Role newRole(Integer userId, String roleName, String roleDesc, String avatar,
                               LlmConfig llmConfig, VoiceConfig voiceConfig,
                               AudioConfig audioConfig, MemoryStrategy memoryStrategy,
                               boolean isDefault) {
        Role role = new Role(null, userId, avatar, roleName, roleDesc, "1", isDefault,
                llmConfig, voiceConfig, audioConfig, memoryStrategy,
                null, null);
        role.signals.add(DomainSignal.UPDATED);
        return role;
    }

    // ===================== 行为方法 =====================

    /** 将此角色设为默认角色（Repository 负责重置同用户其他角色） */
    public void setAsDefault() {
        this.isDefault = true;
        signals.add(DomainSignal.UPDATED);
    }

    /** 清除默认标记 */
    public void clearDefault() {
        this.isDefault = false;
    }

    /** 更新可编辑字段及配置值对象 */
    public void update(String roleName, String roleDesc, String avatar,
                       LlmConfig llmConfig, VoiceConfig voiceConfig,
                       AudioConfig audioConfig, MemoryStrategy memoryStrategy,
                       Boolean isDefault) {
        if (roleName != null && !roleName.isBlank()) this.roleName = roleName;
        if (roleDesc != null) this.roleDesc = roleDesc;
        if (avatar != null) this.avatar = avatar;
        if (llmConfig != null) this.llmConfig = llmConfig;
        if (voiceConfig != null) this.voiceConfig = voiceConfig;
        if (audioConfig != null) this.audioConfig = audioConfig;
        if (memoryStrategy != null) this.memoryStrategy = memoryStrategy;
        if (isDefault != null) this.isDefault = isDefault;
        signals.add(DomainSignal.UPDATED);
    }

    /** void setRoleId — 仅允许 Repository 在 insert 后回填自增 ID */
    public void assignId(Integer roleId) {
        if (this.roleId != null) throw new IllegalStateException("roleId 已设置，不允许覆盖");
        this.roleId = roleId;
    }

    /** 提取并清空领域信号，由 Repository.save() 调用 */
    public List<DomainSignal> pullSignals() {
        List<DomainSignal> result = List.copyOf(signals);
        signals.clear();
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Role r)) return false;
        return Objects.equals(roleId, r.roleId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(roleId);
    }
}
