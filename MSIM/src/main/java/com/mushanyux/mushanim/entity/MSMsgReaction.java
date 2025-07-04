package com.mushanyux.mushanim.entity;

import android.os.Parcel;
import android.os.Parcelable;

public class MSMsgReaction implements Parcelable {
    public String messageID;
    public String channelID;
    public byte channelType;
    public String uid;
    public String name;
    public long seq;
    public String emoji;
    public int isDeleted;
    public String createdAt;

    public MSMsgReaction() {
    }

    protected MSMsgReaction(Parcel in) {
        messageID = in.readString();
        channelID = in.readString();
        channelType = in.readByte();
        uid = in.readString();
        name = in.readString();
        seq = in.readLong();
        emoji = in.readString();
        isDeleted = in.readInt();
        createdAt = in.readString();
    }

    public static final Creator<MSMsgReaction> CREATOR = new Creator<MSMsgReaction>() {
        @Override
        public MSMsgReaction createFromParcel(Parcel in) {
            return new MSMsgReaction(in);
        }

        @Override
        public MSMsgReaction[] newArray(int size) {
            return new MSMsgReaction[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(messageID);
        dest.writeString(channelID);
        dest.writeByte(channelType);
        dest.writeString(uid);
        dest.writeString(name);
        dest.writeLong(seq);
        dest.writeString(emoji);
        dest.writeInt(isDeleted);
        dest.writeString(createdAt);
    }
}
