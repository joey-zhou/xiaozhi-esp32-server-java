package com.xiaozhi.dao;

import java.util.List;

import com.xiaozhi.entity.SysConfig;

/**
 * 模型 数据层
 * 
 * @author Joey
 * 
 */
public interface ConfigMapper {
    int add(SysConfig config);

    int update(SysConfig config);

    int resetDefault(SysConfig config);

    List<SysConfig> query(SysConfig config);

    SysConfig selectConfigById(Integer configId);
}
