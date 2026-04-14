package com.xiaozhi.common.model.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "权限树节点")
public class PermissionTreeResp extends PermissionResp {

    @Schema(description = "子权限")
    private List<PermissionTreeResp> children;
}
