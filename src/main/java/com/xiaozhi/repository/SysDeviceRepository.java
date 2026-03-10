package com.xiaozhi.repository;

import com.xiaozhi.entity.SysDevice;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 设备数据访问层 - Spring Data JPA Repository
 *
 * @author Joey
 */
@Repository
public interface SysDeviceRepository extends JpaRepository<SysDevice, String>, JpaSpecificationExecutor<SysDevice> {

    /**
     * 根据设备 ID 查询设备
     *
     * @param deviceId 设备 ID
     * @return 设备信息
     */
    @Query(value = "SELECT * FROM sys_device WHERE device_id = :deviceId", nativeQuery = true)
    SysDevice findDeviceById(@Param("deviceId") String deviceId);

    /**
     * 查询设备列表 - 分页
     *
     * @param userId     用户 ID（可选）
     * @param deviceName 设备名称（可选）
     * @param state      设备状态（可选）
     * @param pageable   分页参数
     * @return 设备分页列表
     */
    @Query(value = "SELECT * FROM sys_device WHERE 1=1 " +
            "AND (:userId IS NULL OR user_id = :userId) " +
            "AND (:deviceName IS NULL OR :deviceName = '' OR device_name LIKE %:deviceName%) " +
            "AND (:state IS NULL OR :state = '' OR state = :state) " +
            "ORDER BY create_time DESC",
            countQuery = "SELECT COUNT(*) FROM sys_device WHERE 1=1 " +
                    "AND (:userId IS NULL OR user_id = :userId) " +
                    "AND (:deviceName IS NULL OR :deviceName = '' OR device_name LIKE %:deviceName%) " +
                    "AND (:state IS NULL OR :state = '' OR state = :state)",
            nativeQuery = true)
    Page<SysDevice> findDevices(
            @Param("userId") Integer userId,
            @Param("deviceName") String deviceName,
            @Param("state") String state,
            Pageable pageable);

    /**
     * 查询验证码
     *
     * @param deviceId 设备 ID
     * @param userId   用户 ID
     * @return 设备信息
     */
    @Query(value = "SELECT * FROM sys_device WHERE device_id = :deviceId AND user_id = :userId", nativeQuery = true)
    SysDevice findVerifyCode(@Param("deviceId") String deviceId, @Param("userId") Integer userId);

    /**
     * 更新设备验证码
     *
     * @param deviceId 设备 ID
     * @param code     验证码
     * @return 影响行数
     */
    @Modifying
    @Transactional
    @Query(value = "UPDATE sys_device SET code = :code WHERE device_id = :deviceId", nativeQuery = true)
    int updateCode(@Param("deviceId") String deviceId, @Param("code") String code);

    /**
     * 插入验证码到 sys_code 表
     *
     * @param deviceId 设备 ID
     * @param code     验证码
     * @return 影响行数
     */
    @Modifying
    @Transactional
    @Query(value = "INSERT INTO sys_code (device_id, code, create_time) VALUES (:deviceId, :code, NOW())", nativeQuery = true)
    int insertCode(@Param("deviceId") String deviceId, @Param("code") String code);

    /**
     * 批量更新所有设备状态（用于项目启动时重置设备状态）
     *
     * @param state 设备状态
     * @return 影响行数
     */

    /**
     * 批量更新设备用户和角色
     *
     * @param deviceIds 设备 ID 列表
     * @param userId    用户 ID
     * @param roleId    角色 ID
     * @return 影响行数
     */
    @Modifying
    @Query(value = "UPDATE sys_device SET user_id = :userId, role_id = :roleId " +
            "WHERE device_id IN :deviceIds",
            nativeQuery = true)
    int batchUpdateDevices(@Param("deviceIds") List<String> deviceIds,
                           @Param("userId") Integer userId,
                           @Param("roleId") Integer roleId);

    /**
     * 删除设备
     *
     * @param deviceId 设备 ID
     * @return 影响行数
     */
    @Modifying
    @Transactional
    @Query(value = "DELETE FROM sys_device WHERE device_id = :deviceId", nativeQuery = true)
    int deleteDevice(@Param("deviceId") String deviceId);

    default List<SysDevice> query(SysDevice query) {
        return this.findAll(Example.of(query));
    }

    default int update(SysDevice device) {
        save(device);
        return 1;
    }

    default SysDevice selectDeviceById(String deviceId) {
        return findDeviceById(deviceId);
    }

    default int add(SysDevice sysDevice) {
        save(sysDevice);
        return 1;
    }

    default int deleteDevice(SysDevice device) {
        return deleteDevice(device.getDeviceId());
    }

    default int generateCode(SysDevice device) {
        return insertCode(device.getDeviceId(), device.getCode());
    }

    default SysDevice queryVerifyCode(SysDevice device) {
        return findVerifyCode(device.getDeviceId(), device.getUserId());
    }

    default int updateCode(SysDevice device) {
        return updateCode(device.getDeviceId(), device.getCode());
    }
    @Modifying
    @Query(value = "update sys_device set state=:state where 1=1  "  ,nativeQuery = true)
    void updateAllDevicesByState(@Param(value = "state") String state);


}
