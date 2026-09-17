package com.xiaozhi.summary.dal.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaozhi.common.model.bo.SummaryBO;
import com.xiaozhi.summary.dal.mysql.dataobject.SummaryDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SummaryMapper extends BaseMapper<SummaryDO> {

    /** 指定设备只查这一台，否则查该用户名下全部设备；每条带设备名与角色名 */
    IPage<SummaryBO> selectPage(Page<SummaryBO> page,
                                @Param("deviceId") String deviceId,
                                @Param("userId") Integer userId,
                                @Param("roleId") Integer roleId);
}
