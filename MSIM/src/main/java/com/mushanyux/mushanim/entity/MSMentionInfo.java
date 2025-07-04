package com.mushanyux.mushanim.entity;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.List;

public class MSMentionInfo implements Parcelable {

    public boolean isMentionMe;
    public List<String> uids;

    public MSMentionInfo() {
    }

    protected MSMentionInfo(Parcel in) {
        isMentionMe = in.readByte() != 0;
        uids = in.createStringArrayList();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeByte((byte) (isMentionMe ? 1 : 0));
        dest.writeStringList(uids);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<MSMentionInfo> CREATOR = new Creator<MSMentionInfo>() {
        @Override
        public MSMentionInfo createFromParcel(Parcel in) {
            return new MSMentionInfo(in);
        }

        @Override
        public MSMentionInfo[] newArray(int size) {
            return new MSMentionInfo[size];
        }
    };
}
