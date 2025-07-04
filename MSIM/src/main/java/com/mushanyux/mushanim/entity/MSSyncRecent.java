package com.mushanyux.mushanim.entity;

import java.util.List;
import java.util.Map;

public class MSSyncRecent {
    public String message_id;
    public int message_seq;
    public String client_msg_no;
    public String from_uid;
    public String channel_id;
    public byte channel_type;
    public long timestamp;
    public int voice_status;
    public int is_deleted;
    public int revoke;
    public String revoker;
    public long extra_version;
    public int unread_count;
    public int readed_count;
    public int readed;
    public int receipt;
    public int setting;
    public int expire;
    public Map payload;
    public String signal_payload;
    public List<MSSyncMsgReaction> reactions;
    public MSSyncExtraMsg message_extra;
}
