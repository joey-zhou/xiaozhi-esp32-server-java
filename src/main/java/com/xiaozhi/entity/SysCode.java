package com.xiaozhi.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;

/**
 * 验证码表
 *
 * @author Joey
 *
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "验证码信息")
@Entity
@Table(name = "sys_code")
public class SysCode implements java.io.Serializable {

    /**
     * 主键 ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "主键 ID")
    private Integer codeId;

    /**
     * 验证码
     */
    @Column(nullable = false, length = 100)
    @Schema(description = "验证码")
    private String code;

    /**
     * 设备类型
     */
    @Column(length = 50)
    @Schema(description = "设备类型")
    private String type;

    /**
     * 邮箱
     */
    @Column(length = 100)
    @Schema(description = "邮箱")
    private String email;

    /**
     * 设备 ID
     */
    @Column(length = 30)
    @Schema(description = "设备 ID")
    private String deviceId;

    /**
     * 会话 ID
     */
    @Column(length = 100)
    @Schema(description = "会话 ID")
    private String sessionId;

    /**
     * 语音文件路径
     */
    @Column(columnDefinition = "text")
    @Schema(description = "语音文件路径")
    private String audioPath;

    /**
     * 创建时间
     */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private Date createTime;
}
