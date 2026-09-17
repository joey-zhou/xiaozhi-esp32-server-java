package com.xiaozhi.common.model.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "设备批量更新结果")
public class DeviceBatchUpdateResp {

    @Schema(description = "更新成功的设备数")
    private int successCount;

    @Schema(description = "本次提交的设备总数")
    private int totalCount;
}
