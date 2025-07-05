package com.mushanyux.mushanim.msgmodel;

import android.os.Parcel;

public abstract class MSMediaMessageContent extends MSMessageContent {
    public String localPath;//本地地址
    public String url;//网络地址

    public MSMediaMessageContent() {
    }

    protected MSMediaMessageContent(Parcel in) {
        super(in);
    }
}
