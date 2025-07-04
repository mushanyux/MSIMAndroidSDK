package com.mushanyux.mushanim.entity;

import java.util.List;

public class MSSyncChat {
    public long cmd_version;
    public List<MSSyncCmd> cmds;
    public String uid;
    public List<MSSyncConvMsg> conversations;
    public List<MSChannelState> channel_status;
}
