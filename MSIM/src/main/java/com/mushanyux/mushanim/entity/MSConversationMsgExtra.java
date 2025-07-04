package com.mushanyux.mushanim.entity;

import androidx.annotation.NonNull;

public class MSConversationMsgExtra {
    public String channelID;
    public byte channelType;
    public long browseTo;
    public long keepMessageSeq;
    public int keepOffsetY;
    public String draft;
    public long version;
    public long draftUpdatedAt;

    @NonNull
    @Override
    public String toString() {
        return "MSConversationMsgExtra{" +
                "channelID='" + channelID + '\'' +
                ", channelType=" + channelType +
                ", browseTo=" + browseTo +
                ", keepMessageSeq=" + keepMessageSeq +
                ", keepOffsetY=" + keepOffsetY +
                ", draft='" + draft + '\'' +
                ", version=" + version +
                ", draftUpdatedAt=" + draftUpdatedAt +
                '}';
    }
}
