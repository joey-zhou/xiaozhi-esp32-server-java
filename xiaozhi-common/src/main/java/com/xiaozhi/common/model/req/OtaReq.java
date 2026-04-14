package com.xiaozhi.common.model.req;

import lombok.Data;

/**
 * OTA 请求数据，由 Controller 从 HTTP 请求解析后传入 AppService。
 */
@Data
public class OtaReq {

    /** 设备ID（MAC 地址） */
    private String deviceId;

    /** 芯片型号 */
    private String chipModelName;

    /** 固件版本 */
    private String version;

    /** WiFi 名称 */
    private String wifiName;

    /** 设备类型 */
    private String type;

    /** 客户端 IP */
    private String ip;

    /** 地理位置（由 AppService 通过 IP 解析填充） */
    private String location;
}
