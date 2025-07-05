package com.mushanyux.mushanim.msgmodel;

import android.os.Parcel;
import android.os.Parcelable;

import com.mushanyux.mushanim.entity.MSMentionInfo;

import org.json.JSONObject;

import java.util.List;

public class MSMessageContent implements Parcelable {
    //内容
    public String content;
    /**
     * Deprecated 后续版本将删除该字段
     */
    @Deprecated
    public String fromUID;
    /**
     * Deprecated 后续版本将删除该字段
     */
    @Deprecated
    public String fromName;
    //消息内容类型
    public int type;
    //是否@所有人
    public int mentionAll;
    //@成员列表
    public MSMentionInfo mentionInfo;
    //回复对象
    public MSReply reply;
    //搜索关键字
    public String searchableWord;
    //最近会话提示文字
    private String displayContent;
    /**
     * Deprecated 后续版本将删除该字段
     */
    @Deprecated
    public String robotID;
    /**
     * Deprecated 后续版本将删除该字段
     */
    @Deprecated
    public int flame;
    /**
     * Deprecated 后续版本将删除该字段
     */
    @Deprecated
    public int flameSecond;
    /**
     * Deprecated 后续版本将删除该字段
     */
    @Deprecated
    public String topicID;
    public List<MSMsgEntity> entities;

    public MSMessageContent() {
    }

    protected MSMessageContent(Parcel in) {
        content = in.readString();
        fromUID = in.readString();
        fromName = in.readString();
        type = in.readInt();

        mentionAll = in.readInt();
        mentionInfo = in.readParcelable(MSMentionInfo.class.getClassLoader());
        searchableWord = in.readString();
        displayContent = in.readString();
        reply = in.readParcelable(MSReply.class.getClassLoader());
        robotID = in.readString();
        entities = in.createTypedArrayList(MSMsgEntity.CREATOR);
        flame = in.readInt();
        flameSecond = in.readInt();
        topicID = in.readString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(content);
        dest.writeString(fromUID);
        dest.writeString(fromName);
        dest.writeInt(type);
        dest.writeInt(mentionAll);
        dest.writeParcelable(mentionInfo, flags);
        dest.writeString(searchableWord);
        dest.writeString(displayContent);
        dest.writeParcelable(reply, flags);
        dest.writeString(robotID);
        dest.writeTypedList(entities);
        dest.writeInt(flame);
        dest.writeInt(flameSecond);
        dest.writeString(topicID);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<MSMessageContent> CREATOR = new Creator<MSMessageContent>() {
        @Override
        public MSMessageContent createFromParcel(Parcel in) {
            return new MSMessageContent(in);
        }

        @Override
        public MSMessageContent[] newArray(int size) {
            return new MSMessageContent[size];
        }
    };

    public JSONObject encodeMsg() {
        return new JSONObject();
    }

    public MSMessageContent decodeMsg(JSONObject jsonObject) {
        return this;
    }

    // 搜索本类型消息的关键字
    public String getSearchableWord() {
        return content;
    }

    // 需显示的文字
    public String getDisplayContent() {
        return displayContent;
    }
}
