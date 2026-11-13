package com.xiaozhi.dialogue.event;

/**
 * 对话为什么被中止。收尾怎么做只看它，设备报文里带的原因文本只记日志
 */
public enum AbortReason {

    /** 用户插话，服务端识别确认了要打断。新一轮的识别流已经在跑，不能关流、不切回聆听 */
    BARGE_IN,

    /** 设备报文要求中止（按键打断、唤醒词打断等） */
    DEVICE_ABORT,

    /** 设备要断开 */
    DEVICE_GOODBYE,

    /** 设备发来文字输入，先停掉正在说的 */
    TEXT_INPUT
}
