package com.mushanyux.mushanim.protocol;

import com.mushanyux.mushanim.message.type.MSMsgType;

/**
 * 连接talk service确认消息
 */
public class MSConnectAckMsg extends MSBaseMsg {
    //客户端时间与服务器的差值，单位毫秒。
    public long timeDiff;
    //连接原因码
    public short reasonCode;
    // 服务端公钥
    public String serverKey;
    // 安全码
    public String salt;
    // 节点
    public int nodeId;
    public int serviceProtoVersion;
    public MSConnectAckMsg() {
        packetType = MSMsgType.CONNACK;
    }
}
