package com.mushanyux.mushanim.msgmodel;

import android.os.Parcel;
import android.os.Parcelable;

import com.mushanyux.mushanim.message.type.MSMsgContentType;
import com.mushanyux.mushanim.utils.MSLoggerUtils;

import org.json.JSONException;
import org.json.JSONObject;


public class MSImageContent extends MSMediaMessageContent {
    private final String TAG = "MSImageContent";
    public int width;
    public int height;

    public MSImageContent(String localPath) {
        this.localPath = localPath;
        this.type = MSMsgContentType.MS_IMAGE;
    }

    // 无参构造必须提供
    public MSImageContent() {
        this.type = MSMsgContentType.MS_IMAGE;
    }

    @Override
    public JSONObject encodeMsg() {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("url", url);
            jsonObject.put("width", width);
            jsonObject.put("height", height);
            jsonObject.put("localPath", localPath);
        } catch (JSONException e) {
            MSLoggerUtils.getInstance().e(TAG, "encodeMsg error");
        }
        return jsonObject;
    }

    @Override
    public MSMessageContent decodeMsg(JSONObject jsonObject) {
        if (jsonObject.has("url"))
            this.url = jsonObject.optString("url");
        if (jsonObject.has("localPath"))
            this.localPath = jsonObject.optString("localPath");
        if (jsonObject.has("height"))
            this.height = jsonObject.optInt("height");
        if (jsonObject.has("width"))
            this.width = jsonObject.optInt("width");
        return this;
    }


    protected MSImageContent(Parcel in) {
        super(in);
        width = in.readInt();
        height = in.readInt();
        url = in.readString();
        localPath = in.readString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeInt(width);
        dest.writeInt(height);
        dest.writeString(url);
        dest.writeString(localPath);
    }


    public static final Parcelable.Creator<MSImageContent> CREATOR = new Parcelable.Creator<MSImageContent>() {
        @Override
        public MSImageContent createFromParcel(Parcel in) {
            return new MSImageContent(in);
        }

        @Override
        public MSImageContent[] newArray(int size) {
            return new MSImageContent[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public String getDisplayContent() {
        return "[图片]";
    }

    @Override
    public String getSearchableWord() {
        return "[图片]";
    }
}
