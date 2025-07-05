package com.mushanyux.mushanim.interfaces;


import com.mushanyux.mushanim.entity.MSMsg;

public interface IMessageStoreBeforeIntercept {
    boolean isSaveMsg(MSMsg msg);
}
