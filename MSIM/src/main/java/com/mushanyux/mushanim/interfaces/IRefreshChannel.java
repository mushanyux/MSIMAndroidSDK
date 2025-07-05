package com.mushanyux.mushanim.interfaces;


import com.mushanyux.mushanim.entity.MSChannel;

public interface IRefreshChannel {
    void onRefreshChannel(MSChannel channel, boolean isEnd);
}
