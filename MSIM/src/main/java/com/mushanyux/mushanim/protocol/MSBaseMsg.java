package com.mushanyux.mushanim.protocol;

import com.mushanyux.mushanim.message.type.MSMsgType;

/**
 * @see MSMsgType 对应packetType类型
 */
public class MSBaseMsg {
    //报文类型
    public short packetType;
    //标示位（目前为固定值）
    public short flag;
    //报文剩余长度
    public int remainingLength;
    //是否持久化[是否保存在数据库]
    public boolean no_persist;
    //是否显示红点
    public boolean red_dot = true;
    //是否只显示一次
    public boolean sync_once;

    public int getFixedHeaderLength() {
        return 2;
    }
}
