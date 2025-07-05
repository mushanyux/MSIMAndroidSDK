package com.mushanyux.mushanim.interfaces;

public interface IGetChannelMemberList {
    void request(String channelId, byte channelType, String searchKey, int page, int limit, IChannelMemberListResult listResult);
}
