package com.xiaozhi.service;

import java.util.List;

import com.xiaozhi.entity.SysDevice;

/**
 * 设备查询/更新
 * 
 * @author Joey
 * 
 */
public interface SysDeviceService {

  /**
   * 添加设备
   * 
   * @param device
   * @return
   */
  public int add(SysDevice device);

  /**
   * 查询设备信息
   * 
   * @param device
   * @return
   */
  public List<SysDevice> query(SysDevice device);

  /**
   * 查询验证码
   */
  public SysDevice queryVerifyCode(SysDevice device);

  /**
   * 查询并生成验证码
   */
  public SysDevice generateCode(SysDevice device);

  /**
   * 关系设备验证码语音路径
   */
  public int updateCode(SysDevice device);

  /**
   * 更新设备信息
   * 
   * @param device
   * @return
   */
  public int update(SysDevice device);

  /**
   * 删除设备
   * 
   * @param device
   * @return
   */
  public int delete(SysDevice device);

}