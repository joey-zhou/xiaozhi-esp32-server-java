package com.xiaozhi.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;

/**
 * 智能体实体类
 *
 * @author Joey
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "智能体信息")
public class SysAgent extends SysConfig {

    /** 智能体 ID */
    @Transient
    @Schema(description = "智能体 ID")
    private Integer agentId;

    /** 智能体名称 */
    @Transient
    @Schema(description = "智能体名称")
    private String agentName;

    /** 平台智能体 ID */
    @Transient
    @Schema(description = "平台智能体 ID")
    private String botId;

    /** 智能体描述 */
    @Transient
    @Schema(description = "智能体描述")
    private String agentDesc;

    /** 图标 URL */
    @Transient
    @Schema(description = "图标 URL")
    private String iconUrl;

    /** 发布时间 */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Transient
    @Schema(description = "发布时间")
    private Date publishTime;
}
