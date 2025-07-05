package com.mushanyux.mushanim.msgmodel;

import com.mushanyux.mushanim.message.type.MSMsgContentType;

public class MSFormatErrorContent extends MSMessageContent {
    public MSFormatErrorContent() {
        this.type = MSMsgContentType.MS_CONTENT_FORMAT_ERROR;
    }

    @Override
    public String getDisplayContent() {
        return "[消息格式错误]";
    }
}
