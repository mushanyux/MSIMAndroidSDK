package com.mushanyux.mushanim.protocol;


import com.mushanyux.mushanim.message.type.MSMsgType;

public class MSPingMsg extends MSBaseMsg {
    public MSPingMsg() {
        packetType = MSMsgType.PING;
    }
}
