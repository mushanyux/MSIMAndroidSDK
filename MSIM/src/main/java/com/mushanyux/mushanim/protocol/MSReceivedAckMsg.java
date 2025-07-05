package com.mushanyux.mushanim.protocol;


import com.mushanyux.mushanim.message.type.MSMsgType;

public class MSReceivedAckMsg extends MSBaseMsg {
    //服务端的消息ID(全局唯一)
    public String messageID;
    //序列号
    public int messageSeq;
    public MSReceivedAckMsg() {
        packetType = MSMsgType.REVACK;
        remainingLength = 8;//序列号
    }
}
