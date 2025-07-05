package com.mushanyux.mushanim.interfaces;


import com.mushanyux.mushanim.entity.MSMsg;

import java.util.List;

public interface INewMsgListener {
    void newMsg(List<MSMsg> msgs);
}
