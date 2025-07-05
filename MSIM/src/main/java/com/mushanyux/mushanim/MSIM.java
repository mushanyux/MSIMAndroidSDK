package com.mushanyux.mushanim;

import android.content.Context;
import android.text.TextUtils;

public class MSIM {
    private final String Version = "V1.0.0";

    private MSIM() {}

    private static class MSIMBinder {
        static final MSIM im = new MSIM();
    }

    public static MSIM getInstance() {
        return MSIMBinder.im;
    }

    private boolean isDebug = false;
    private boolean isWriteLog = false;
    private String deviceId = "";

    public boolean isDebug() {
        return isDebug;
    }

    public boolean isWriteLog() {
        return isWriteLog;
    }

    public String getDeviceID(){
        return deviceId;
    }

    public void setWriteLog(boolean isWriteLog) {
        this.isWriteLog = isWriteLog;
    }

    public void setDebug(boolean isDebug) {
        this.isDebug = isDebug;
    }

    //设置文件目录
    public void setFileCacheDir(String fileDir) {
        MSIMApplication.getInstance().setFileCacheDir(fileDir);
    }

    public String getVersion() {
        return Version;
    }
    public void setDeviceId(String deviceID){
        this.deviceId = deviceID;
    }

}
