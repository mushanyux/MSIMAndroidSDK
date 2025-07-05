package com.mushanyux.mushanim.msgmodel;

import android.os.Parcel;
import android.text.TextUtils;

import com.mushanyux.mushanim.message.type.MSMsgContentType;

import org.json.JSONException;
import org.json.JSONObject;

public class MSTextContent extends MSMessageContent {

    public MSTextContent(String content) {
        this.content = content;
        this.type = MSMsgContentType.MS_TEXT;
    }

    // 无参构造必须提供
    public MSTextContent() {
        this.type = MSMsgContentType.MS_TEXT;
    }

    @Override
    public JSONObject encodeMsg() {
        JSONObject jsonObject = new JSONObject();
        try {
            if (!TextUtils.isEmpty(content))
                jsonObject.put("content", content);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return jsonObject;
    }

    @Override
    public MSMessageContent decodeMsg(JSONObject jsonObject) {
        if (jsonObject != null) {
            if (jsonObject.has("content"))
                this.content = jsonObject.optString("content");
        }
        return this;
    }

    @Override
    public String getSearchableWord() {
        return content;
    }

    @Override
    public String getDisplayContent() {
        return content;
    }

    protected MSTextContent(Parcel in) {
        super(in);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
    }


    public static final Creator<MSTextContent> CREATOR = new Creator<MSTextContent>() {
        @Override
        public MSTextContent createFromParcel(Parcel in) {
            return new MSTextContent(in);
        }

        @Override
        public MSTextContent[] newArray(int size) {
            return new MSTextContent[size];
        }
    };
}
