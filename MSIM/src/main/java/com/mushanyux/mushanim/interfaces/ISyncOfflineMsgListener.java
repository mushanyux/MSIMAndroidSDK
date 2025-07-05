package com.mushanyux.mushanim.interfaces;

public interface ISyncOfflineMsgListener {
    void getOfflineMsgs(long max_message_seq, ISyncOfflineMsgBack iSyncOfflineMsgBack);
}
