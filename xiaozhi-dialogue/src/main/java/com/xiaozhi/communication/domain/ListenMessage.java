package com.xiaozhi.communication.domain;

import com.xiaozhi.enums.ListenMode;
import com.xiaozhi.enums.ListenState;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public final class ListenMessage extends Message {
    public ListenMessage(){
        super("listen");
    }

    private ListenState state;
    private ListenMode mode;
    private String text;
}
