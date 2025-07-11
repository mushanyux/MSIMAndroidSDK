package com.mushanyux.mushanim.msgmodel;

import android.os.Parcel;
import android.os.Parcelable;

import com.mushanyux.mushanim.message.type.MSMsgContentType;

import org.json.JSONException;
import org.json.JSONObject;

public class MSVoiceContent extends MSMediaMessageContent {
    public int timeTrad;
    public String waveform;

    public MSVoiceContent(String localPath, int timeTrad) {
        this.type = MSMsgContentType.MS_VOICE;
        this.timeTrad = timeTrad;
        this.localPath = localPath;
    }

    // 无参构造必须提供
    public MSVoiceContent() {
        this.type = MSMsgContentType.MS_VOICE;
    }

    @Override
    public JSONObject encodeMsg() {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("localPath", localPath);
            jsonObject.put("timeTrad", timeTrad);
            jsonObject.put("url", url);
            if (waveform != null)
                jsonObject.put("waveform", waveform);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return jsonObject;
    }

    @Override
    public MSMessageContent decodeMsg(JSONObject jsonObject) {
        if (jsonObject.has("timeTrad"))
            timeTrad = jsonObject.optInt("timeTrad");
        if (jsonObject.has("localPath"))
            localPath = jsonObject.optString("localPath");
        if (jsonObject.has("url"))
            url = jsonObject.optString("url");
        if (jsonObject.has("waveform"))
            waveform = jsonObject.optString("waveform");
        return this;
    }


    protected MSVoiceContent(Parcel in) {
        super(in);
        timeTrad = in.readInt();
        url = in.readString();
        localPath = in.readString();
        waveform = in.readString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeInt(timeTrad);
        dest.writeString(url);
        dest.writeString(localPath);
        dest.writeString(waveform);
    }


    public static final Parcelable.Creator<MSVoiceContent> CREATOR = new Parcelable.Creator<MSVoiceContent>() {
        @Override
        public MSVoiceContent createFromParcel(Parcel in) {
            return new MSVoiceContent(in);
        }

        @Override
        public MSVoiceContent[] newArray(int size) {
            return new MSVoiceContent[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public String getDisplayContent() {
        return "[语音]";
    }

    @Override
    public String getSearchableWord() {
        return "[语音]";
    }
}
