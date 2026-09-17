package com.xiaozhi.common.model.resp;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "文件上传结果")
public class FileUploadResp {

    @Schema(description = "原始文件名")
    private String fileName;

    @Schema(description = "存储用的新文件名")
    private String newFileName;

    @Schema(description = "文件内容 SHA-256 哈希")
    private String hash;

    @Schema(description = "文件访问地址")
    private String url;

    @Schema(description = "本地存储时的裸相对路径，云存储不返回该字段")
    private String relativePath;
}
