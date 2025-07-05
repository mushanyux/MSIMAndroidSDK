package com.mushanyux.mushanim.interfaces;


import com.mushanyux.mushanim.entity.MSChannelMember;

import java.util.List;

public interface IChannelMemberListResult {
    void onResult(List<MSChannelMember> list);
}
