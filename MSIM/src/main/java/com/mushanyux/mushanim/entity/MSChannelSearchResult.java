package com.mushanyux.mushanim.entity;

import android.os.Parcel;
import android.os.Parcelable;

public class MSChannelSearchResult implements Parcelable {
    //频道信息
    public MSChannel msChannel;
    //包含的成员名称
    public String containMemberName;

    public MSChannelSearchResult() {
    }

    protected MSChannelSearchResult(Parcel in) {
        msChannel = in.readParcelable(MSChannel.class.getClassLoader());
        containMemberName = in.readString();
    }

    public static final Creator<MSChannelSearchResult> CREATOR = new Creator<MSChannelSearchResult>() {
        @Override
        public MSChannelSearchResult createFromParcel(Parcel in) {
            return new MSChannelSearchResult(in);
        }

        @Override
        public MSChannelSearchResult[] newArray(int size) {
            return new MSChannelSearchResult[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(msChannel, flags);
        dest.writeString(containMemberName);
    }
}
