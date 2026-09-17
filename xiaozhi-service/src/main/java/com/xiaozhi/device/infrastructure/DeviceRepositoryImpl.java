package com.xiaozhi.device.infrastructure;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.xiaozhi.common.CacheHelper;
import com.xiaozhi.common.config.CacheNames;
import com.xiaozhi.common.model.bo.DeviceBO;
import com.xiaozhi.common.model.bo.VerifyCodeBO;
import com.xiaozhi.device.convert.DeviceConvert;
import com.xiaozhi.device.dal.mysql.dataobject.DeviceDO;
import com.xiaozhi.device.dal.mysql.mapper.DeviceMapper;
import com.xiaozhi.device.domain.Device;
import com.xiaozhi.device.domain.repository.DeviceRepository;
import com.xiaozhi.device.domain.vo.VerifyCode;
import com.xiaozhi.device.infrastructure.convert.DeviceConverter;
import com.xiaozhi.device.support.DeviceCacheKeys;
import com.xiaozhi.event.DeviceRoleChangedEvent;
import com.xiaozhi.event.DeviceSessionClosedEvent;
import com.xiaozhi.event.DeviceUpdatedEvent;
import com.xiaozhi.verifycode.service.VerifyCodeService;
import jakarta.annotation.Resource;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
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
    private DeviceConvert deviceConvert;

    @Resource
    private VerifyCodeService verifyCodeService;

    @Resource
    private ApplicationEventPublisher eventPublisher;

    @Resource
    private CacheManager cacheManager;

    @Resource
    private CacheHelper cacheHelper;

    @Override
    public Optional<Device> findById(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) return Optional.empty();
        String cacheKey = DeviceCacheKeys.of(deviceId);
        Cache cache = cacheManager.getCache(CacheNames.DEVICE);
        DeviceBO cached = cacheHelper.getWithLock(
                "device:" + cacheKey,
                () -> cache == null ? null : cache.get(cacheKey, DeviceBO.class),
                () -> {
                    DeviceDO dataObject = deviceMapper.selectById(deviceId);
                    if (dataObject == null) {
                        return null;
                    }
                    DeviceBO bo = deviceConvert.toBO(dataObject);
                    if (cache != null) {
                        cache.put(cacheKey, bo);
                    }
                    return bo;
                }
        );
        return Optional.ofNullable(cached)
                .map(this::toDeviceDO)
                .map(deviceConverter::toDomain);
    }

    @Override
    public Optional<VerifyCode> findVerifyCode(String code, String deviceId, String sessionId) {
        VerifyCodeBO bo = verifyCodeService.findValid(code, deviceId, sessionId);
        return Optional.ofNullable(bo).map(deviceConverter::toVerifyCode);
    }

    @Override
    public Optional<VerifyCode> findVerifyCodeByCode(String code) {
        VerifyCodeBO bo = verifyCodeService.findValidByCode(code);
        return Optional.ofNullable(bo).map(deviceConverter::toVerifyCode);
    }

    @Override
    public void invalidateVerifyCodes(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) return;
        verifyCodeService.deleteByDeviceId(deviceId);
    }

    @Override
    @Transactional
    public void save(Device device) {
        DeviceDO dataObject = deviceConverter.toDataObject(device);
        // 设备注册一台只发生一次，之后的上下线上报、改角色、同步都走这里，更新才是常态，
        // 所以先更新：命中就一次写完，匹配不到行才说明是新设备。
        // 反过来先插入的话，每次更新都要先撞一次主键冲突，多一次往返，
        // 且失败的 insert 会先拿到该行共享锁、紧接着的 update 再升级成排他锁，并发同一设备时容易死锁。
        if (deviceMapper.updateById(dataObject) == 0) {
            try {
                deviceMapper.insert(dataObject);
            } catch (DuplicateKeyException e) {
                // 并发首次注册，另一条已经插进去了。insert 的自动填充刚把 createTime 写成本次时间，
                // 清空后 updateById 才会按 NOT_NULL 策略跳过该列，不覆盖设备原有的创建时间
                dataObject.setCreateTime(null);
                deviceMapper.updateById(dataObject);
            }
        }
        // 自动填充把本次写入的时间戳塞回了 DO，回填给聚合根，写接口出参不用再查一遍设备表
        device.markPersisted(dataObject.getCreateTime(), dataObject.getUpdateTime());
        evictCache(device.getDeviceId());

        DeviceBO bo = deviceConverter.toBO(device);
        device.pullSignals().forEach(signal -> {
            switch (signal) {
                case UPDATED -> eventPublisher.publishEvent(new DeviceUpdatedEvent(this, bo));
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
        Cache cache = cacheManager.getCache(CacheNames.DEVICE);
        if (cache != null) {
            cache.evict(DeviceCacheKeys.of(deviceId));
        }
    }

    /** BO 快照 → DO（重建聚合根用） */
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
