package com.xiaozhi.entity;

import java.io.Serial;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 用户第三方认证信息表
 * 支持一个用户绑定多个第三方平台(微信/QQ/支付宝等)
 *
 * @author Joey
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@Schema(description = "用户第三方认证信息")
public class SysUserAuth extends Base<SysUserAuth> {

    /**
     * serialVersionUID
     */
    @Serial
    private static final long serialVersionUID = -8923456789012345678L;

    /**
     * 主键ID
     */
    @Schema(description = "主键ID")
    private Long id;

    /**
     * 用户ID,关联sys_user.user_id
     */
    @Schema(description = "用户ID")
    private Integer userId;

    /**
     * 第三方平台的唯一标识(如微信openid)
     */
    @Schema(description = "第三方平台唯一标识")
    private String openId;

    /**
     * 微信unionid(用于同一主体的不同应用)
     */
    @Schema(description = "微信UnionID")
    private String unionId;

    /**
     * 平台标识: wechat/qq/alipay/apple等
     */
    @Schema(description = "平台标识")
    private String platform;

    /**
     * 第三方返回的原始JSON数据
     */
    @Schema(description = "第三方原始JSON数据")
    private String profile;

    /**
     * 创建时间
     */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private Date createTime;

    /**
     * 更新时间
     */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "更新时间")
    private Date updateTime;
}
