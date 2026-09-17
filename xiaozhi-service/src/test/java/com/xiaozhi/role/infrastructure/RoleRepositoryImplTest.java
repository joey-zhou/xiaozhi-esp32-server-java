package com.xiaozhi.role.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.xiaozhi.event.RoleUpdatedEvent;
import com.xiaozhi.role.dal.mysql.dataobject.RoleDO;
import com.xiaozhi.role.dal.mysql.mapper.RoleMapper;
import com.xiaozhi.role.domain.Role;
import com.xiaozhi.role.domain.vo.AudioConfig;
import com.xiaozhi.role.domain.vo.LlmConfig;
import com.xiaozhi.role.domain.vo.MemoryStrategy;
import com.xiaozhi.role.domain.vo.VoiceConfig;
import com.xiaozhi.role.infrastructure.convert.RoleConverter;
import com.xiaozhi.support.MybatisPlusTestHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 钉住「唯一默认角色」不变式、缓存失效与事件发布：
 * save 时批量降级同用户的其它默认角色并逐个失效其缓存，delete 对不存在的角色是空操作，
 * 只有 UPDATED 信号才会发布 RoleUpdatedEvent。
 */
@ExtendWith(MockitoExtension.class)
class RoleRepositoryImplTest {

    @BeforeAll
    static void initTableInfo() {
        MybatisPlusTestHelper.initTableInfo(RoleDO.class);
    }

    @Mock
    private RoleMapper roleMapper;

    @Mock
    private RoleConverter roleConverter;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache cache;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private RoleRepositoryImpl roleRepository;

    @Test
    void saveNewDefaultRoleDemotesOnlyCurrentDefaultRolesAndEvictsTheirCache() {
        Role role = newDefaultRole(7);
        RoleDO dataObject = newRoleDO(10, 7);
        when(roleConverter.toDataObject(role)).thenReturn(dataObject);
        when(cacheManager.getCache(anyString())).thenReturn(cache);

        RoleDO oldDefault = newRoleDO(3, 7);
        when(roleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(oldDefault));

        roleRepository.save(role);

        ArgumentCaptor<LambdaUpdateWrapper<RoleDO>> updateCaptor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(roleMapper).update(eq(null), updateCaptor.capture());
        assertThat(updateCaptor.getValue().getSqlSet()).contains("isDefault=");

        verify(cache).evictIfPresent("3");
        verify(cache).evict("3");
        verify(cache).evictIfPresent("10");
        verify(cache).evict("10");
        verify(eventPublisher).publishEvent(any(RoleUpdatedEvent.class));
    }

    @Test
    void saveDefaultRoleSkipsResetWhenNoOtherDefaultRoleExists() {
        Role role = newDefaultRole(7);
        RoleDO dataObject = newRoleDO(10, 7);
        when(roleConverter.toDataObject(role)).thenReturn(dataObject);
        when(cacheManager.getCache(anyString())).thenReturn(cache);
        when(roleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        roleRepository.save(role);

        verify(roleMapper, never()).update(any(), any());
    }

    @Test
    void deleteNoOpsWhenRoleNotFound() {
        when(roleMapper.selectById(99)).thenReturn(null);

        roleRepository.delete(99);

        verifyNoInteractions(eventPublisher);
        verify(roleMapper, never()).delete(any());
    }

    @Test
    void deleteEvictsCacheAndPublishesEvent() {
        RoleDO existing = newRoleDO(5, 7);
        when(roleMapper.selectById(5)).thenReturn(existing);
        when(cacheManager.getCache(anyString())).thenReturn(cache);

        roleRepository.delete(5);

        verify(roleMapper).delete(any(LambdaUpdateWrapper.class));
        verify(cache).evictIfPresent("5");
        verify(cache).evict("5");
        verify(eventPublisher).publishEvent(any(RoleUpdatedEvent.class));
    }

    /** 用 newRole 工厂方法构造：只有它才会往聚合根上挂 UPDATED 信号，直接调重建用构造函数不会。 */
    private static Role newDefaultRole(Integer userId) {
        return Role.newRole(userId, "role", "desc", null,
                LlmConfig.defaults(), VoiceConfig.defaults(), AudioConfig.defaults(), MemoryStrategy.defaults(),
                true, null);
    }

    private static RoleDO newRoleDO(Integer roleId, Integer userId) {
        RoleDO d = new RoleDO();
        d.setRoleId(roleId);
        d.setUserId(userId);
        d.setIsDefault("1");
        return d;
    }
}
