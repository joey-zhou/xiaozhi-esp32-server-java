package com.xiaozhi.communication.domain;

import com.xiaozhi.enums.ListenMode;
import com.xiaozhi.enums.ListenState;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data

public final class ListenMessage extends Message {
    public ListenMessage(){
        super("listen");
    }

    private ListenState state;
    private ListenMode mode;
    private String text;

    // 显式添加 getter 方法
    public ListenState getState() {
        return state;
    }

    public ListenMode getMode() {
        return mode;
    }

    public String getText() {
        return text;
    }
}
