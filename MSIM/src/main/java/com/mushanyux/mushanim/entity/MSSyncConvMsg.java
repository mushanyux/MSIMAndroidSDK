package com.mushanyux.mushanim.entity;

import java.util.List;

public class MSSyncConvMsg {
    public String channel_id;
    public byte channel_type;
    public String last_client_msg_no;
    public long last_msg_seq;
    public int offset_msg_seq;
    public long timestamp;
    public int unread;
    public long version;
    public List<MSSyncRecent> recents;
}
