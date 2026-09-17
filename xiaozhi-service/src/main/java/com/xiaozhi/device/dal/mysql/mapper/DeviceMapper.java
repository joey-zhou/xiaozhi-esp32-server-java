package com.xiaozhi.device.dal.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaozhi.device.dal.mysql.dataobject.DeviceDO;
import com.xiaozhi.device.model.DeviceProjection;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DeviceMapper extends BaseMapper<DeviceDO> {

    IPage<DeviceProjection> selectPage(Page<DeviceProjection> page,
                                       @Param("deviceId") String deviceId,
                                       @Param("deviceName") String deviceName,
                                       @Param("roleName") String roleName,
                                       @Param("state") String state,
                                       @Param("roleId") Integer roleId,
                                       @Param("userId") Integer userId);
}
