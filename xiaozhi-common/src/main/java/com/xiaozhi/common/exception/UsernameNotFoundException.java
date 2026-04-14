package com.xiaozhi.common.exception;

/**
 * 用户名没有找到异常
 * 
 * @author Joey
 */
public class UsernameNotFoundException extends RuntimeException {
  public UsernameNotFoundException() {
  }

  public UsernameNotFoundException(String msg) {
    super(msg);
  }
}