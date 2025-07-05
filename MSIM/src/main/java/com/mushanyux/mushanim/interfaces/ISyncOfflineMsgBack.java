package com.mushanyux.mushanim.interfaces;


import com.mushanyux.mushanim.entity.MSSyncMsg;

import java.util.List;

public interface ISyncOfflineMsgBack {
    void onBack(boolean isEnd, List<MSSyncMsg> list);
}
