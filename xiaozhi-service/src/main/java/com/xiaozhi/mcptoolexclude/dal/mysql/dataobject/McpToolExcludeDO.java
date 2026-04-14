package com.xiaozhi.mcptoolexclude.dal.mysql.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaozhi.common.model.dataobject.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_mcp_tool_exclude")
public class McpToolExcludeDO extends BaseDO {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String excludeType;
    private String bindType;
    private String bindCode;
    private String bindKey;
    private String excludeTools;
}
