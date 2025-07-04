package com.mushanyux.mushanim.entity;

import java.util.List;

public class MSSyncChannelMsg {
    public long min_message_seq;
    public long max_message_seq;
    public int more;
    public List<MSSyncRecent> messages;
}
