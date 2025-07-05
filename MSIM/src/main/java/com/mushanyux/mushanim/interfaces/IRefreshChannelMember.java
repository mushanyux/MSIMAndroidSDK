package com.mushanyux.mushanim.interfaces;


import com.mushanyux.mushanim.entity.MSChannelMember;

public interface IRefreshChannelMember {
    void onRefresh(MSChannelMember channelMember, boolean isEnd);
}
