package com.mushanyux.mushanim.message.type;

import com.mushanyux.mushanim.protocol.MSSendMsg;
import com.mushanyux.mushanim.utils.DateUtils;

public class MSSendingMsg {
    // 消息
    public MSSendMsg msSendMsg;
    // 发送次数
    public int sendCount;
    // 发送时间
    public long sendTime;
    // 是否可重发本条消息
    public boolean isCanResend;

    public MSSendingMsg(int sendCount, MSSendMsg msSendMsg, boolean isCanResend) {
        this.sendCount = sendCount;
        this.msSendMsg = msSendMsg;
        this.isCanResend = isCanResend;
        this.sendTime = DateUtils.getInstance().getCurrentSeconds();
    }
}
