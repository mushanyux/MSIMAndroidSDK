package com.mushanyux.mushanim.entity;


import androidx.annotation.NonNull;

import com.mushanyux.mushanim.utils.DateUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;

public class MSConversationMsg {
    //频道id
    public String channelID;
    //频道类型
    public byte channelType;
    //最后一条消息本地ID
    public String lastClientMsgNO;
    //是否删除
    public int isDeleted;
    //服务器同步版本号
    public long version;
    //最后一条消息时间
    public long lastMsgTimestamp;
    //未读消息数量
    public int unreadCount;
    //最后一条消息序号
    public long lastMsgSeq;
    //扩展字段
    public HashMap localExtraMap;
    public MSConversationMsgExtra msgExtra;
    public String parentChannelID;
    public byte parentChannelType;

    public MSConversationMsg() {
        this.lastMsgTimestamp = DateUtils.getInstance().getCurrentSeconds();
    }

    public String getLocalExtraString() {
        String extras = "";
        if (localExtraMap != null) {
            JSONObject jsonObject = new JSONObject();
            for (Object key : localExtraMap.keySet()) {
                try {
                    jsonObject.put(key.toString(), localExtraMap.get(key));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            extras = jsonObject.toString();
        }
        return extras;
    }

    @NonNull
    @Override
    public String toString() {
        return "MSConversationMsg{" +
                ", channelID='" + channelID + '\'' +
                ", channelType=" + channelType +
                ", lastClientMsgNO='" + lastClientMsgNO + '\'' +
                ", isDeleted=" + isDeleted +
                ", version=" + version +
                ", lastMsgTimestamp=" + lastMsgTimestamp +
                ", lastMsgSeq=" + lastMsgSeq +
                ", unreadCount=" + unreadCount +
                ", localExtraMap=" + localExtraMap +
                '}';
    }
}
