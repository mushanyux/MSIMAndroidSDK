package com.mushanyux.mushanim.interfaces;

import com.mushanyux.mushanim.entity.MSUIConversationMsg;

import java.util.List;

public interface IRefreshConversationMsgList {
    void onRefresh(List<MSUIConversationMsg> list);
}
