package com.mushanyux.mushanim.interfaces;

import com.mushanyux.mushanim.entity.MSChannelMember;

import java.util.List;

public interface IGetChannelMemberListResult {
    public void onResult(List<MSChannelMember> list, boolean isRemote);
}
