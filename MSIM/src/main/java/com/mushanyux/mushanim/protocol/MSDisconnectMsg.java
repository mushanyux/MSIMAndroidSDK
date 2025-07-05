package com.mushanyux.mushanim.protocol;


import com.mushanyux.mushanim.message.type.MSMsgType;

public class MSDisconnectMsg extends MSBaseMsg {
    public byte reasonCode;
    public String reason;

    public MSDisconnectMsg() {
        packetType = MSMsgType.DISCONNECT;
    }
}
