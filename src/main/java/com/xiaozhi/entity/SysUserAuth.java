package com.xiaozhi.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 用户第三方认证信息表
 * 支持一个用户绑定多个第三方平台 (微信/QQ/支付宝等)
 *
 * @author Joey
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "用户第三方认证信息")
@Entity
@Table(name = "sys_user_auth")
@DynamicUpdate
@DynamicInsert
public class SysUserAuth extends Base<SysUserAuth> {

    /**
     * serialVersionUID
     */
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键 ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "主键 ID")
    private Long id;

    /**
     * 用户 ID，关联 sys_user.user_id
     */
    @Column( nullable = false)
    @Schema(description = "用户 ID")
    private Integer userId;

    /**
     * 第三方平台的唯一标识 (如微信 openid)
     */
    @Column(name="open_id", nullable = false, length = 100)
    @Schema(description = "第三方平台唯一标识")
    private String openId;

    /**
     * 微信 unionid(用于同一主体的不同应用)
     */
    @Column(length = 100)
    @Schema(description = "微信 UnionID")
    private String unionId;

    /**
     * 平台标识：wechat/qq/alipay/apple 等
     */
    @Column( nullable = false, length = 20)
    @Schema(description = "平台标识")
    private String platform;

    /**
     * 第三方返回的原始 JSON 数据
     */
    @Column( columnDefinition = "text")
    @Schema(description = "第三方原始 JSON 数据")
    private String profile;

    /**
     * 创建时间
     */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
