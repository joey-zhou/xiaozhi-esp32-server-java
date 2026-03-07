package com.xiaozhi.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serial;
import java.util.Date;

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
@Entity
@Table(name = "sys_user")
public class SysUser extends Base<SysUser> {

    /**
     * serialVersionUID
     */
    @Serial
    private static final long serialVersionUID = -3406166342385856305L;

    /**
     * 用户 ID，主键
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "用户 ID", example = "1")
    private Integer userId;

    /**
     * 用户名
     */
    @Column(nullable = false, length = 50)
    @Schema(description = "用户名，用于登录", example = "admin")
    private String username;

    /**
     * 密码
     */
    @Column(nullable = false, length = 255)
    @Schema(description = "加密后的密码", example = "$2a$10$encrypted...")
    private String password;

    /**
     * 微信openid
     */
    @Column(length = 100)
    @Schema(description = "微信openid，用于微信登录", example = "oABC123...")
    private String wxOpenId;

    /**
     * 微信unionid
     */
    @Column(length = 100)
    @Schema(description = "微信unionid，用于微信开放平台统一账号", example = "uABC123...")
    private String wxUnionId;

    /**
     * 姓名
     */
    @Column(length = 100)
    @Schema(description = "用户姓名/昵称", example = "张三")
    private String name;

    /*
     * Token限制
     */
    @Schema(description = "Token使用限制数量", example = "10")
    private Integer tokenLimit;

    /*
     * Token提醒开关
     */
    @Schema(description = "Token不足时是否提醒（1-开启，0-关闭）", example = "1", allowableValues = {"0", "1"})
    private String tokenNotify;

    /**
     * 对话次数
     */
    @Schema(description = "用户累计对话次数", example = "100")
    private Integer totalMessage;

    /**
     * 参加人数
     */
    @Schema(description = "参与的活动人数", example = "5")
    private Integer aliveNumber;

    /**
     * 总设备数
     */
    @Schema(description = "用户拥有的设备总数", example = "3")
    private Integer totalDevice;

    /**
     * 头像
     */
    @Column(length = 100)
    @Schema(description = "用户头像URL", example = "https://example.com/avatar.jpg")
    private String avatar;

    /**
     * 用户状态 0、被禁用，1、正常使用
     */
    @Column(length = 1)
    @Schema(description = "用户状态：0-禁用，1-正常", example = "1", allowableValues = {"0", "1"})
    private String state;

    /**
     * 用户类型 0、普通管理（拥有标准权限），1、超级管理（拥有所有权限）
     */
    @Column(length = 1)
    @Schema(description = "用户类型：0-普通用户，1-超级管理员", example = "0", allowableValues = {"0", "1"})
    private String isAdmin;

    /**
     * 角色权限
     */
    @Column(nullable = false)
    @Schema(description = "用户角色ID", example = "2")
    private Integer roleId;

    /**
     * 手机号
     */
    @Column(length = 100)
    @Schema(description = "手机号，用于验证码登录", example = "13800138000")
    private String tel;

    /**
     * 邮箱
     */
    @Column(length = 100)
    @Schema(description = "邮箱地址，用于接收验证码和通知", example = "user@example.com")
    private String email;

    /**
     * 上次登录IP
     */
    @Column(length = 100)
    @Schema(description = "用户上次登录的IP地址", example = "192.168.1.100")
    private String loginIp;



    /**
     * 上次登录时间
     */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "用户上次登录时间", example = "2024-12-01 10:30:00")
    private Date loginTime;

    /**
     * 验证码（非数据库字段，用于业务逻辑）
     */
    @Transient
    @Schema(description = "验证码（临时字段，用于验证）", example = "123456")
    private String code;
}
