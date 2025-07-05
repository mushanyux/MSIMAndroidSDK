package com.mushanyux.mushanim.entity;


import android.text.TextUtils;

import com.mushanyux.mushanim.db.MsgDbManager;
import com.mushanyux.mushanim.db.ReminderDBManager;
import com.mushanyux.mushanim.manager.ChannelManager;

import java.util.HashMap;
import java.util.List;

public class MSUIConversationMsg {
    public long lastMsgSeq;
    public String clientMsgNo;
    //频道ID
    public String channelID;
    //频道类型
    public byte channelType;
    //最后一条消息时间
    public long lastMsgTimestamp;
    //消息频道
    private MSChannel msChannel;
    //消息正文
    private MSMsg msMsg;
    //未读消息数量
    public int unreadCount;
    public int isDeleted;
    private MSConversationMsgExtra remoteMsgExtra;
    //高亮内容[{type:1,text:'[有人@你]'}]
    private List<MSReminder> reminderList;
    //扩展字段
    public HashMap<String, Object> localExtraMap;
    public String parentChannelID;
    public byte parentChannelType;


    public MSMsg getMsMsg() {
        if (msMsg == null) {
            msMsg = MsgDbManager.getInstance().queryWithClientMsgNo(clientMsgNo);
            if (msMsg != null && msMsg.isDeleted == 1) msMsg = null;
        }
        return msMsg;
    }

    public void setMsMsg(MSMsg msMsg) {
        this.msMsg = msMsg;
    }

    public MSChannel getMsChannel() {
        if (msChannel == null) {
            msChannel = ChannelManager.getInstance().getChannel(channelID, channelType);
        }
        return msChannel;
    }

    public void setMsChannel(MSChannel msChannel) {
        this.msChannel = msChannel;
    }

    public List<MSReminder> getReminderList() {
        if (reminderList == null) {
            reminderList = ReminderDBManager.getInstance().queryWithChannelAndDone(channelID, channelType, 0);
        }

        return reminderList;
    }

    public void setReminderList(List<MSReminder> list) {
        this.reminderList = list;
    }

    public MSConversationMsgExtra getRemoteMsgExtra() {
        return remoteMsgExtra;
    }

    public void setRemoteMsgExtra(MSConversationMsgExtra extra) {
        this.remoteMsgExtra = extra;
    }

    public long getSortTime() {
        if (getRemoteMsgExtra() != null && !TextUtils.isEmpty(getRemoteMsgExtra().draft)) {
            return getRemoteMsgExtra().draftUpdatedAt;
        }
        return lastMsgTimestamp;
    }

}
