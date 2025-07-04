package com.mushanyux.mushanim;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.mushanyux.mushanim.db.MSDBHelper;
import com.mushanyux.mushanim.entity.MSSyncMsgMode;
import com.mushanyux.mushanim.utils.MSLoggerUtils;

import java.lang.ref.WeakReference;
import java.util.Objects;

public class MSIMApplication {
    private final String sharedName = "ms_account_config";
    //协议版本
    public byte protocolVersion = 4;

    private MSIMApplication() {
    }

    private static class MSApplicationBinder {
        static final MSIMApplication app = new MSIMApplication();
    }

    public static MSIMApplication getInstance() {
        return MSApplicationBinder.app;
    }

    private WeakReference<Context> mContext;

    public Context getContext() {
        if (mContext == null) {
            return null;
        }
        return mContext.get();
    }

    void initContext(Context context) {
        this.mContext = new WeakReference<>(context);
    }

    private String tempRSAPublicKey;

    private MSDBHelper mDbHelper;

    public boolean isCanConnect = true;

    private String fileDir = "msIM";

    private MSSyncMsgMode syncMsgMode;

    public MSSyncMsgMode getSyncMsgMode() {
        if (syncMsgMode == null) {
            syncMsgMode = MSSyncMsgMode.READ;
        }
        return syncMsgMode;
    }

    public void setSyncMsgMode(MSSyncMsgMode syncMsgMode) {
        this.syncMsgMode = syncMsgMode;
    }
    public String getUid() {
        if (mContext == null) {
            MSLoggerUtils.getInstance().e("The passed in context is null");
            return "";
        }
        String tempUid = "";
        if (TextUtils.isEmpty(tempUid)) {
            SharedPreferences setting = mContext.get().getSharedPreferences(
                    sharedName, Context.MODE_PRIVATE);
            tempUid = setting.getString("wk_UID", "");
        }
        return tempUid;
    }

    public void setUid(String uid) {
        if (mContext == null) return;
        // tempUid = uid;
        SharedPreferences setting = mContext.get().getSharedPreferences(
                sharedName, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = setting.edit();
        editor.putString("wk_UID", uid);
        editor.apply();
    }

    public String getToken() {
        if (mContext == null) return "";
        SharedPreferences setting = mContext.get().getSharedPreferences(
                sharedName, Context.MODE_PRIVATE);
        return setting.getString("wk_Token", "");
    }

    public void setToken(String token) {
        if (mContext == null) return;
        SharedPreferences setting = mContext.get().getSharedPreferences(
                sharedName, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = setting.edit();
        editor.putString("wk_Token", token);
        editor.apply();
    }
    public synchronized MSDBHelper getDbHelper() {
        if (mDbHelper == null) {
            String uid = getUid();
            if (!TextUtils.isEmpty(uid)) {
                mDbHelper = MSDBHelper.getInstance(mContext.get(), uid);
            } else {
                MSLoggerUtils.getInstance().e("get DbHelper uid is null");
            }
        }
        return mDbHelper;
    }

    public void setFileCacheDir(String fileDir) {
        this.fileDir = fileDir;
    }
    public String getFileCacheDir() {
        if (TextUtils.isEmpty(fileDir)) {
            fileDir = "msIM";
        }
        return Objects.requireNonNull(getContext().getExternalFilesDir(fileDir)).getAbsolutePath();
    }
}
