package com.xiaozhi.entity;

import java.io.Serial;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 用户表
 * 
 * @author Joey
 * 
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties({ "password" })
@Schema(description = "用户信息")
public class SysUser extends Base<SysUser> {

    /**
     * serialVersionUID
     */
    @Serial
    private static final long serialVersionUID = -3406166342385856305L;

    /**
     * 用户名
     */
    @Schema(description = "用户名")
    private String username;

    /**
     * 密码
     */
    @Schema(description = "密码")
    private String password;

    /**
     * 微信openid
     */
    private String wxOpenId;

    /**
     * 微信unionid
     */
    private String wxUnionId;

    /**
     * 姓名
     */
    @Schema(description = "姓名")
    private String name;

    /**
     * 对话次数
     */
    @Schema(description = "对话次数")
    private Integer totalMessage;

    /**
     * 参加人数
     */
    @Schema(description = "参加人数")
    private Integer aliveNumber;

    /**
     * 总设备数
     */
    @Schema(description = "总设备数")
    private Integer totalDevice;

    /**
     * 头像
     */
    @Schema(description = "头像")
    private String avatar;

    /**
     * 用户状态 0、被禁用，1、正常使用
     */
    @Schema(description = "用户状态 0、被禁用，1、正常使用")
    private String state;

    /**
     * 用户类型 0、普通管理（拥有标准权限），1、超级管理（拥有所有权限）
     */
    @Schema(description = "用户类型 0、普通管理，1、超级管理")
    private String isAdmin;

    /**
     * 角色权限
     */
    @Schema(description = "角色ID")
    private Integer roleId;

    /**
     * 手机号
     */
    @Schema(description = "手机号")
    private String tel;

    /**
     * 邮箱
     */
    @Schema(description = "邮箱")
    private String email;

    /**
     * 上次登录IP
     */
    @Schema(description = "上次登录IP")
    private String loginIp;

    /**
     * 验证码
     */
    @Schema(description = "验证码")
    private String code;

    /**
     * 上次登录时间
     */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "上次登录时间")
    private Date loginTime;
}
