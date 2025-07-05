package com.mushanyux.mushanim.interfaces;


import com.mushanyux.mushanim.entity.MSChannelMember;

public interface IGetChannelMemberInfo {
    MSChannelMember onResult(String channelId, byte channelType, String uid, IChannelMemberInfoListener iChannelMemberInfoListener);
}
