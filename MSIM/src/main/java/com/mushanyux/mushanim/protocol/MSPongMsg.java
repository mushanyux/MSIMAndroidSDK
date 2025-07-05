package com.mushanyux.mushanim.protocol;

import com.mushanyux.mushanim.message.type.MSMsgType;

public class MSPongMsg extends MSBaseMsg {
    public MSPongMsg() {
        packetType = MSMsgType.PONG;
    }
}
