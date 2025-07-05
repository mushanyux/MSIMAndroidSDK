package com.mushanyux.mushanim.interfaces;


import com.mushanyux.mushanim.protocol.MSConnectAckMsg;
import com.mushanyux.mushanim.protocol.MSDisconnectMsg;
import com.mushanyux.mushanim.protocol.MSPongMsg;
import com.mushanyux.mushanim.protocol.MSSendAckMsg;

public interface IReceivedMsgListener {
    /**
     * 登录状态消息
     */
    void loginStatusMsg(MSConnectAckMsg connectAckMsg);

    /**
     * 心跳消息
     */
    void pongMsg(MSPongMsg pongMsg);

    /**
     * 被踢消息
     */
    void kickMsg(MSDisconnectMsg disconnectMsg);

    /**
     * 发送消息状态消息
     *
     * @param sendAckMsg ack
     */
    void sendAckMsg(MSSendAckMsg sendAckMsg);

    /**
     * 重连
     */
    void reconnect();
}
