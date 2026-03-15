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
}