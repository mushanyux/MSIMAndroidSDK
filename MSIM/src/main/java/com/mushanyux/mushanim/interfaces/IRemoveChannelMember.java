package com.mushanyux.mushanim.interfaces;


import com.mushanyux.mushanim.entity.MSChannelMember;

import java.util.List;

public interface IRemoveChannelMember {
    void onRemoveMembers(List<MSChannelMember> list);
}
