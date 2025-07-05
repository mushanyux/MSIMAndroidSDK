package com.mushanyux.mushanim;

import android.content.Context;
import android.text.TextUtils;

import com.mushanyux.mushanim.manager.CMDManager;
import com.mushanyux.mushanim.manager.ChannelManager;
import com.mushanyux.mushanim.manager.ChannelMembersManager;
import com.mushanyux.mushanim.manager.ConnectionManager;
import com.mushanyux.mushanim.manager.ConversationManager;
import com.mushanyux.mushanim.manager.MsgManager;
import com.mushanyux.mushanim.manager.ReminderManager;
import com.mushanyux.mushanim.manager.RobotManager;
import com.mushanyux.mushanim.utils.CryptoUtils;

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


    /**
     * 初始化IM
     *
     * @param context context
     * @param uid     用户ID
     * @param token   im token
     */
    public void init(Context context, String uid, String token) {
        if (context == null || TextUtils.isEmpty(uid) || TextUtils.isEmpty(token)) {
            throw new NullPointerException("context,uid and token cannot be null");
        }

        MSIMApplication.getInstance().closeDbHelper();
        MSIMApplication.getInstance().initContext(context);
        MSIMApplication.getInstance().setUid(uid);
        MSIMApplication.getInstance().setToken(token);
        // 初始化加密key
        CryptoUtils.getInstance().initKey();
        // 初始化默认消息类型
        getMsgManager().initNormalMsg();
        // 初始化数据库
        MSIMApplication.getInstance().getDbHelper();
        // todo 将上次发送消息中的队列标志为失败
    }

    // 获取消息管理
    public MsgManager getMsgManager() {
        return MsgManager.getInstance();
    }

    // 获取连接管理
    public ConnectionManager getConnectionManager() {
        return ConnectionManager.getInstance();
    }

    // 获取频道管理
    public ChannelManager getChannelManager() {
        return ChannelManager.getInstance();
    }

    // 获取最近会话管理
    public ConversationManager getConversationManager() {
        return ConversationManager.getInstance();
    }

    // 获取频道成员管理
    public ChannelMembersManager getChannelMembersManager() {
        return ChannelMembersManager.getInstance();
    }

    //获取提醒管理
    public ReminderManager getReminderManager() {
        return ReminderManager.getInstance();
    }

    // 获取cmd管理
    public CMDManager getCMDManager() {
        return CMDManager.getInstance();
    }

    public RobotManager getRobotManager() {
        return RobotManager.getInstance();
    }
}
