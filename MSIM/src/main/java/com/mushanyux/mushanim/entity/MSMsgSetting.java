package com.mushanyux.mushanim.entity;

import android.os.Parcel;
import android.os.Parcelable;

public class MSMsgSetting implements Parcelable {
    // 消息是否回执
    public int receipt;
    // 是否开启top
    public int topic;
    // 是否流消息
    public int stream;

    public MSMsgSetting() {
    }

    protected MSMsgSetting(Parcel in) {
        receipt = in.readInt();
        topic = in.readInt();
        stream = in.readInt();
    }

    public static final Creator<MSMsgSetting> CREATOR = new Creator<MSMsgSetting>() {
        @Override
        public MSMsgSetting createFromParcel(Parcel in) {
            return new MSMsgSetting(in);
        }

        @Override
        public MSMsgSetting[] newArray(int size) {
            return new MSMsgSetting[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(receipt);
        dest.writeInt(topic);
        dest.writeInt(stream);
    }
}
