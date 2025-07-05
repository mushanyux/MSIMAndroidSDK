package com.mushanyux.mushanim.interfaces;


import com.mushanyux.mushanim.entity.MSChannel;

public interface IGetChannelInfo {
    MSChannel onGetChannelInfo(String channelId, byte channelType, IChannelInfoListener iChannelInfoListener);
}
