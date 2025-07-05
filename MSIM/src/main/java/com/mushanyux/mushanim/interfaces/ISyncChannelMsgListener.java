package com.mushanyux.mushanim.interfaces;

public interface ISyncChannelMsgListener {
    void syncChannelMsgs(String channelID, byte channelType, long startMessageSeq, long endMessageSeq, int limit, int pullMode, ISyncChannelMsgBack iSyncChannelMsgBack);
}
