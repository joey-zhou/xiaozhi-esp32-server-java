package com.xiaozhi.server.web.chat.convert;

import com.xiaozhi.common.model.ChatToken;
import com.xiaozhi.common.model.resp.ChatTokenResp;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface WebChatConvert {

    ChatTokenResp toResp(ChatToken token);
}
