package com.mushanyux.mushanim.entity;

import android.os.Parcel;
import android.os.Parcelable;

public class MSMessageSearchResult implements Parcelable {
    //消息对应的频道信息
    public MSChannel msChannel;
    //包含关键字的信息
    public String searchableWord;
    //条数
    public int messageCount;

    public MSMessageSearchResult() {
    }

    protected MSMessageSearchResult(Parcel in) {
        msChannel = in.readParcelable(MSChannel.class.getClassLoader());
        searchableWord = in.readString();
        messageCount = in.readInt();
    }

    public static final Creator<MSMessageSearchResult> CREATOR = new Creator<MSMessageSearchResult>() {
        @Override
        public MSMessageSearchResult createFromParcel(Parcel in) {
            return new MSMessageSearchResult(in);
        }

        @Override
        public MSMessageSearchResult[] newArray(int size) {
            return new MSMessageSearchResult[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(msChannel, flags);
        dest.writeString(searchableWord);
        dest.writeInt(messageCount);
    }
}
