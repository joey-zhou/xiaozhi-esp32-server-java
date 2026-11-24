package com.xiaozhi.common.model.bo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 用户消息的附件，存在 metadata 里；消息软删时文件保留 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageAttachmentBO {

    public static final String KIND_IMAGE = "image";

    /** 附件种类，取值见 KIND_* */
    private String kind;

    /** 用户看到的原文件名 */
    private String name;

    /** 对象存储路径 */
    private String path;
}
