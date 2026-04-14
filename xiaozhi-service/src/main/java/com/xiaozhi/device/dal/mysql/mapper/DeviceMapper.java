package com.xiaozhi.device.dal.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaozhi.common.model.bo.VerifyCodeBO;
import com.xiaozhi.common.model.resp.DeviceResp;
import com.xiaozhi.device.dal.mysql.dataobject.DeviceDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DeviceMapper extends BaseMapper<DeviceDO> {

    IPage<DeviceResp> selectPageResp(Page<DeviceResp> page,
                                     @Param("deviceId") String deviceId,
                                     @Param("deviceName") String deviceName,
                                     @Param("roleName") String roleName,
                                     @Param("state") String state,
                                     @Param("roleId") Integer roleId,
                                     @Param("userId") Integer userId);

    DeviceResp selectRespById(@Param("deviceId") String deviceId,
                              @Param("userId") Integer userId);

    VerifyCodeBO selectValidCode(@Param("code") String code,
                                 @Param("deviceId") String deviceId,
                                 @Param("sessionId") String sessionId);

    int insertVerifyCode(@Param("deviceId") String deviceId,
                         @Param("sessionId") String sessionId,
                         @Param("type") String type,
                         @Param("code") String code);

    int updateCodeAudioPath(@Param("deviceId") String deviceId,
                            @Param("sessionId") String sessionId,
                            @Param("code") String code,
                            @Param("audioPath") String audioPath);
}
