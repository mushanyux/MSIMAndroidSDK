package com.mushanyux.mushanim.interfaces;


import com.mushanyux.mushanim.entity.MSMsg;

import java.util.List;

public interface IGetOrSyncHistoryMsgBack {
    void onSyncing();
    void onResult(List<MSMsg> msgs);
}
