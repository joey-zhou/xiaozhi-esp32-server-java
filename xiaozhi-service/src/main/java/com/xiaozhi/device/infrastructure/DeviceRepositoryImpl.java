package com.xiaozhi.device.infrastructure;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.xiaozhi.common.CacheHelper;
import com.xiaozhi.common.model.bo.DeviceBO;
import com.xiaozhi.common.model.bo.VerifyCodeBO;
import com.xiaozhi.device.dal.mysql.dataobject.DeviceDO;
import com.xiaozhi.device.dal.mysql.mapper.DeviceMapper;
import com.xiaozhi.device.domain.Device;
import com.xiaozhi.device.domain.repository.DeviceRepository;
import com.xiaozhi.device.domain.vo.VerifyCode;
import com.xiaozhi.device.infrastructure.convert.DeviceConverter;
import com.xiaozhi.device.service.DeviceService;
import com.xiaozhi.event.DeviceOnlineEvent;
import com.xiaozhi.event.DeviceRoleChangedEvent;
import com.xiaozhi.event.DeviceSessionClosedEvent;
import com.xiaozhi.event.DeviceUpdatedEvent;
import jakarta.annotation.Resource;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;

/**
 * Device 聚合根仓储实现。
 * <p>
 * 封装 MyBatis-Plus Mapper，负责：
 * <ul>
 *   <li>DO ↔ 聚合根转换</li>
 *   <li>缓存失效</li>
 *   <li>聚合根信号 → Spring ApplicationEvent 发布</li>
 * </ul>
 */
@Repository
public class DeviceRepositoryImpl implements DeviceRepository {

    @Resource
    private DeviceMapper deviceMapper;

    @Resource
    private DeviceConverter deviceConverter;

    @Resource
    private ApplicationEventPublisher eventPublisher;

    @Resource
    private CacheManager cacheManager;

    @Resource
    private CacheHelper cacheHelper;

    @Override
    public Optional<Device> findById(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) return Optional.empty();
        String cacheKey = deviceId.replace(":", "-");
        Cache cache = cacheManager.getCache(DeviceService.CACHE_NAME);
        DeviceBO cached = cacheHelper.getWithLock(
                "device:" + cacheKey,
                () -> cache == null ? null : cache.get(cacheKey, DeviceBO.class),
                () -> null
        );
        if (cached != null) {
            return Optional.of(deviceConverter.toDomain(toDeviceDO(cached)));
        }
        return Optional.ofNullable(deviceMapper.selectById(deviceId))
                .map(deviceConverter::toDomain);
    }

    @Override
    public Optional<VerifyCode> findVerifyCode(String code, String deviceId, String sessionId) {
        VerifyCodeBO bo = deviceMapper.selectValidCode(code, deviceId, sessionId);
        return Optional.ofNullable(bo).map(deviceConverter::toVerifyCode);
    }

    @Override
    @Transactional
    public void save(Device device) {
        DeviceDO dataObject = deviceConverter.toDataObject(device);
        DeviceDO existing = deviceMapper.selectById(device.getDeviceId());
        if (existing == null) {
            deviceMapper.insert(dataObject);
        } else {
            deviceMapper.updateById(dataObject);
        }
        evictCache(device.getDeviceId());

        DeviceBO bo = deviceConverter.toBO(device);
        device.pullSignals().forEach(signal -> {
            switch (signal) {
                case UPDATED -> eventPublisher.publishEvent(new DeviceUpdatedEvent(this, bo));
                case ONLINE -> eventPublisher.publishEvent(new DeviceOnlineEvent(this, device.getDeviceId()));
                case ROLE_CHANGED -> eventPublisher.publishEvent(new DeviceRoleChangedEvent(this, device.getDeviceId()));
                case SESSION_CLOSED -> eventPublisher.publishEvent(new DeviceSessionClosedEvent(this, device.getDeviceId()));
            }
        });
    }

    @Override
    @Transactional
    public void delete(String deviceId) {
        deviceMapper.delete(new LambdaUpdateWrapper<DeviceDO>()
                .eq(DeviceDO::getDeviceId, deviceId));
        evictCache(deviceId);
        eventPublisher.publishEvent(new DeviceSessionClosedEvent(this, deviceId));
    }

    @Override
    public void updateState(String deviceId, String state) {
        if (deviceId == null || deviceId.isBlank() || state == null) return;
        deviceMapper.update(null, new LambdaUpdateWrapper<DeviceDO>()
                .eq(DeviceDO::getDeviceId, deviceId)
                .set(DeviceDO::getState, state));
        evictCache(deviceId);
    }

    @Override
    public int batchUpdateState(Set<String> deviceIds, String state) {
        if (deviceIds == null || deviceIds.isEmpty() || state == null) return 0;
        int updated = deviceMapper.update(null, new LambdaUpdateWrapper<DeviceDO>()
                .in(DeviceDO::getDeviceId, deviceIds)
                .set(DeviceDO::getState, state));
        deviceIds.forEach(this::evictCache);
        return updated;
    }

    private void evictCache(String deviceId) {
        Cache cache = cacheManager.getCache(DeviceService.CACHE_NAME);
        if (cache != null) {
            cache.evict(deviceId.replace(":", "-"));
        }
    }

    /** BO 快照 → DO（仅用于缓存命中路径的聚合根重建） */
    private DeviceDO toDeviceDO(DeviceBO bo) {
        DeviceDO d = new DeviceDO();
        d.setDeviceId(bo.getDeviceId());
        d.setDeviceName(bo.getDeviceName());
        d.setUserId(bo.getUserId());
        d.setRoleId(bo.getRoleId());
        d.setMcpList(bo.getMcpList());
        d.setIp(bo.getIp());
        d.setLocation(bo.getLocation());
        d.setWifiName(bo.getWifiName());
        d.setChipModelName(bo.getChipModelName());
        d.setType(bo.getType());
        d.setVersion(bo.getVersion());
        d.setState(bo.getState());
        d.setCreateTime(bo.getCreateTime());
        d.setUpdateTime(bo.getUpdateTime());
        return d;
    }
}
