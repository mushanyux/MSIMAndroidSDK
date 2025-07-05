package com.mushanyux.mushanim.msgmodel;

import android.os.Parcel;
import android.os.Parcelable;

public class MSMsgEntity implements Parcelable {
    public int offset;
    public int length;
    public String type;
    public String value;

    public MSMsgEntity() {
    }

    protected MSMsgEntity(Parcel in) {
        offset = in.readInt();
        length = in.readInt();
        type = in.readString();
        value = in.readString();
    }

    public static final Creator<MSMsgEntity> CREATOR = new Creator<MSMsgEntity>() {
        @Override
        public MSMsgEntity createFromParcel(Parcel in) {
            return new MSMsgEntity(in);
        }

        @Override
        public MSMsgEntity[] newArray(int size) {
            return new MSMsgEntity[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeInt(offset);
        parcel.writeInt(length);
        parcel.writeString(type);
        parcel.writeString(value);
    }
}
