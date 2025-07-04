package com.mushanyux.mushanim;

import android.content.Context;
import android.text.TextUtils;

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

    public boolean isCanConnect = true;

    private String fileDir = "msIM";

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
