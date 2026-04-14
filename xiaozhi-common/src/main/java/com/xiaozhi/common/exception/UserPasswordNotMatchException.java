package com.xiaozhi.common.exception;

/**
 * 密码错误异常
 * 
 * @author Joey
 */

public class UserPasswordNotMatchException extends RuntimeException {
  public UserPasswordNotMatchException() {
  }

  public UserPasswordNotMatchException(String msg) {
    super(msg);
  }
}