package com.xiaozhi.role;

import com.xiaozhi.common.model.req.RoleCreateReq;
import com.xiaozhi.common.model.req.RoleUpdateReq;
import com.xiaozhi.common.model.resp.RoleResp;
import com.xiaozhi.device.service.DeviceService;
import com.xiaozhi.role.convert.RoleConvert;
import org.mapstruct.factory.Mappers;
import com.xiaozhi.role.domain.Role;
import com.xiaozhi.role.domain.repository.RoleRepository;
import com.xiaozhi.role.domain.vo.AudioConfig;
import com.xiaozhi.role.domain.vo.LlmConfig;
import com.xiaozhi.role.domain.vo.VoiceConfig;
import com.xiaozhi.role.service.RoleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 钉住角色写接口的出参：字段全部来自落库后的聚合根，存完不再回查角色表。
 * <p>温度、topP、音调、语速、空闲超时这五个默认值原先只在 DO → BO 的转换器里补，
 * 创建时没填就会返回 null；默认值移进领域模型后，两条路径必须给出同一份取值。
 */
@ExtendWith(MockitoExtension.class)
class RoleAppServiceTest {

    private static final int ROLE_ID = 77;
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 1, 1, 8, 0);

    @Mock
    private RoleService roleService;

    @Mock
    private RoleRepository roleRepository;

    // 出参映射必须用真实的 MapStruct 实现：这些用例断言的就是 Resp 的每个字段，mock 掉等于什么都没测
    private final RoleConvert roleConvert = Mappers.getMapper(RoleConvert.class);

    @Mock
    private DeviceService deviceService;

    private RoleAppService roleAppService;

    @BeforeEach
    void setUp() {
        roleAppService = new RoleAppService();
        ReflectionTestUtils.setField(roleAppService, "roleService", roleService);
        ReflectionTestUtils.setField(roleAppService, "roleRepository", roleRepository);
        ReflectionTestUtils.setField(roleAppService, "roleConvert", roleConvert);
        ReflectionTestUtils.setField(roleAppService, "deviceService", deviceService);
    }

    @Test
    void createWithoutSamplingParamsFallsBackToTheDomainDefaults() {
        assignIdOnSave();

        RoleCreateReq req = new RoleCreateReq();
        req.setRoleName("小智");
        req.setModelId(5);

        RoleResp resp = roleAppService.create(req, 7);

        // 创建角色时不填这五项，返回的仍是库里那份默认值，与从 DO 读回时完全一致
        assertThat(resp.getTemperature()).isEqualTo(0.7d);
        assertThat(resp.getTopP()).isEqualTo(0.9d);
        assertThat(resp.getTtsPitch()).isEqualTo(1.0d);
        assertThat(resp.getTtsSpeed()).isEqualTo(1.0d);
        assertThat(resp.getInactiveTimeoutSeconds()).isEqualTo(60);
    }

    @Test
    void createReturnsEveryResponseFieldFromTheAggregate() {
        assignIdOnSave();

        RoleCreateReq req = new RoleCreateReq();
        req.setRoleName("小智");
        req.setRoleDesc("语音助手");
        req.setAvatar("avatar/role.png");
        req.setModelId(5);
        req.setTemperature(0.2);
        req.setTopP(0.5);
        req.setTtsId(3);
        req.setSttId(4);
        req.setVoiceName("xiaoyun");
        req.setTtsPitch(1.3);
        req.setTtsSpeed(0.8);
        req.setVadEnergyTh(0.1f);
        req.setVadSpeechTh(0.2f);
        req.setVadSilenceTh(0.3f);
        req.setVadSilenceMs(500);
        req.setInactiveTimeoutSeconds(120);
        req.setIsDefault("1");

        RoleResp resp = roleAppService.create(req, 7);

        assertThat(resp.getRoleId()).isEqualTo(ROLE_ID);
        assertThat(resp.getRoleName()).isEqualTo("小智");
        assertThat(resp.getRoleDesc()).isEqualTo("语音助手");
        assertThat(resp.getAvatar()).isEqualTo("avatar/role.png");
        assertThat(resp.getModelId()).isEqualTo(5);
        assertThat(resp.getTemperature()).isEqualTo(0.2);
        assertThat(resp.getTopP()).isEqualTo(0.5);
        assertThat(resp.getTtsId()).isEqualTo(3);
        assertThat(resp.getSttId()).isEqualTo(4);
        assertThat(resp.getVoiceName()).isEqualTo("xiaoyun");
        assertThat(resp.getTtsPitch()).isEqualTo(1.3);
        assertThat(resp.getTtsSpeed()).isEqualTo(0.8);
        assertThat(resp.getVadEnergyTh()).isEqualTo(0.1f);
        assertThat(resp.getVadSpeechTh()).isEqualTo(0.2f);
        assertThat(resp.getVadSilenceTh()).isEqualTo(0.3f);
        assertThat(resp.getVadSilenceMs()).isEqualTo(500);
        assertThat(resp.getInactiveTimeoutSeconds()).isEqualTo(120);
        assertThat(resp.getIsDefault()).isEqualTo("1");
        assertThat(resp.getState()).isEqualTo(Role.STATE_ENABLED);
        // JOIN 出来的展示列与时间戳本来就不在这个接口的返回里
        assertThat(resp.getModelName()).isNull();
        assertThat(resp.getModelProvider()).isNull();
        assertThat(resp.getTtsProvider()).isNull();
        assertThat(resp.getTotalDevice()).isNull();
        assertThat(resp.getCreateTime()).isNull();
        assertThat(resp.getUpdateTime()).isNull();
        // 出参全部来自聚合根：创建路径一次角色表都不读
        verify(roleService, never()).getBO(any());
    }

    @Test
    void updateKeepsFieldsTheRequestDidNotCarry() {
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(storedRole()));

        RoleUpdateReq req = new RoleUpdateReq();
        req.setRoleName("管家");

        RoleResp resp = roleAppService.update(ROLE_ID, req);

        assertThat(resp.getRoleId()).isEqualTo(ROLE_ID);
        assertThat(resp.getRoleName()).isEqualTo("管家");
        // 只带了名字的局部更新不能把其余字段抹成 null——库里那份要原样返回
        assertThat(resp.getModelId()).isEqualTo(5);
        assertThat(resp.getTemperature()).isEqualTo(0.2);
        assertThat(resp.getTopP()).isEqualTo(0.5);
        assertThat(resp.getTtsId()).isEqualTo(3);
        assertThat(resp.getSttId()).isEqualTo(4);
        assertThat(resp.getVoiceName()).isEqualTo("xiaoyun");
        assertThat(resp.getTtsPitch()).isEqualTo(1.3);
        assertThat(resp.getTtsSpeed()).isEqualTo(0.8);
        assertThat(resp.getVadSilenceMs()).isEqualTo(500);
        assertThat(resp.getInactiveTimeoutSeconds()).isEqualTo(120);
        assertThat(resp.getState()).isEqualTo(Role.STATE_ENABLED);
        assertThat(resp.getIsDefault()).isEqualTo("0");
        verify(roleService, never()).getBO(any());
    }

    @Test
    void updateAppliesPatchedFieldsAndStateChange() {
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(storedRole()));

        RoleUpdateReq req = new RoleUpdateReq();
        req.setTemperature(1.1);
        req.setIsDefault("1");
        req.setState(Role.STATE_DISABLED);

        RoleResp resp = roleAppService.update(ROLE_ID, req);

        assertThat(resp.getTemperature()).isEqualTo(1.1);
        assertThat(resp.getTopP()).isEqualTo(0.5);
        assertThat(resp.getIsDefault()).isEqualTo("1");
        assertThat(resp.getState()).isEqualTo(Role.STATE_DISABLED);
        // 传空数组是清空绑定，与「不传」区分开
    }

    @Test
    void updateOfALegacyRowWithNullColumnsReturnsTheSameDefaultsAsCreate() {
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(legacyRole()));

        RoleUpdateReq req = new RoleUpdateReq();
        req.setRoleName("管家");

        RoleResp resp = roleAppService.update(ROLE_ID, req);

        // 历史行这几列是 NULL，读回来补的默认值必须和创建角色时补的那份一模一样
        assertThat(resp.getTemperature()).isEqualTo(0.7d);
        assertThat(resp.getTopP()).isEqualTo(0.9d);
        assertThat(resp.getTtsPitch()).isEqualTo(1.0d);
        assertThat(resp.getTtsSpeed()).isEqualTo(1.0d);
        assertThat(resp.getInactiveTimeoutSeconds()).isEqualTo(60);
    }

    /** 模拟仓储 save()：insert 后回填自增主键 */
    private void assignIdOnSave() {
        doAnswer(invocation -> {
            invocation.getArgument(0, Role.class).assignId(ROLE_ID);
            return null;
        }).when(roleRepository).save(any(Role.class));
    }

    /** 库里已有的一个角色，字段全带值，用于钉住局部更新的出参一个字段都不少 */
    private static Role storedRole() {
        return new Role(ROLE_ID, 7, "avatar/role.png", "小智", "语音助手", Role.STATE_ENABLED,
                false, 120,
                new LlmConfig(5, 0.2, 0.5),
                new VoiceConfig(3, 4, "xiaoyun", 1.3, 0.8),
                new AudioConfig(0.1f, 0.2f, 0.3f, 500),
                CREATED_AT, CREATED_AT);
    }

    /** 建库早期落下的行：五个可空列都是 NULL */
    private static Role legacyRole() {
        return new Role(ROLE_ID, 7, null, "小智", null, Role.STATE_ENABLED, false, null,
                new LlmConfig(5, null, null),
                new VoiceConfig(3, null, null, null, null),
                new AudioConfig(null, null, null, null),
                CREATED_AT, CREATED_AT);
    }
}
