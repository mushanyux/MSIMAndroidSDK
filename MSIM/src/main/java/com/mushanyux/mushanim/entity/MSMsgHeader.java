package com.mushanyux.mushanim.entity;

import android.os.Parcel;
import android.os.Parcelable;

public class MSMsgHeader implements Parcelable {
    //是否持久化[是否不保存在数据库]
    public boolean noPersist;
    //对方是否显示红点
    public boolean redDot = true;
    //消息是否只同步一次
    public boolean syncOnce;

    MSMsgHeader() {

    }

    protected MSMsgHeader(Parcel in) {
        noPersist = in.readByte() != 0;
        redDot = in.readByte() != 0;
        syncOnce = in.readByte() != 0;
    }

    public static final Creator<MSMsgHeader> CREATOR = new Creator<MSMsgHeader>() {
        @Override
        public MSMsgHeader createFromParcel(Parcel in) {
            return new MSMsgHeader(in);
        }

        @Override
        public MSMsgHeader[] newArray(int size) {
            return new MSMsgHeader[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeByte((byte) (noPersist ? 1 : 0));
        parcel.writeByte((byte) (redDot ? 1 : 0));
        parcel.writeByte((byte) (syncOnce ? 1 : 0));
    }
}
