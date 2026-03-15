package com.xiaozhi.dto.repository;

import java.time.LocalDateTime;

public interface SysUserWithStats {
    Integer getUserId();
    String getUsername();
    String getName();
    String getTel();
    String getEmail();
    String getAvatar();
    String getState();
    String getIsAdmin();
    String getLoginIp();
    LocalDateTime getLoginTime();
    LocalDateTime getCreateTime();
    Integer getTotalDevice();
    Integer getTotalMessage();
    Integer getAliveNumber();
    
    // 以下字段用于扩展，避免 Hibernate 映射错误
    String getWxOpenId();
    String getWxUnionId();
    String getPassword();
    Integer getRoleId();
}