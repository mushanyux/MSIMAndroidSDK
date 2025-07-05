package com.mushanyux.mushanim.interfaces;

public interface ISyncMsgReaction {
    void onSyncMsgReaction(String channelID, byte channelType, long maxSeq);
}
