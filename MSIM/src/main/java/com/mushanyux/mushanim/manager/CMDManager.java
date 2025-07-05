package com.mushanyux.mushanim.manager;

import android.text.TextUtils;

import com.mushanyux.mushanim.MSIM;
import com.mushanyux.mushanim.MSIMApplication;
import com.mushanyux.mushanim.db.ConversationDbManager;
import com.mushanyux.mushanim.db.MsgDbManager;
import com.mushanyux.mushanim.db.MSDBColumns;
import com.mushanyux.mushanim.entity.MSCMD;
import com.mushanyux.mushanim.entity.MSCMDKeys;
import com.mushanyux.mushanim.entity.MSChannel;
import com.mushanyux.mushanim.entity.MSChannelType;
import com.mushanyux.mushanim.interfaces.ICMDListener;
import com.mushanyux.mushanim.utils.DateUtils;
import com.mushanyux.mushanim.utils.MSLoggerUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CMDManager extends BaseManager {
    private final String TAG = "CMDManager";

    private CMDManager() {
    }

    private static class CMDManagerBinder {
        static final CMDManager cmdManager = new CMDManager();
    }

    public static CMDManager getInstance() {
        return CMDManagerBinder.cmdManager;
    }

    private ConcurrentHashMap<String, ICMDListener> cmdListenerMap;

    public void handleCMD(JSONObject jsonObject, String channelID, byte channelType) {
        if (jsonObject == null) {
            jsonObject = new JSONObject();
        }
        try {
            if (!jsonObject.has("channel_id"))
                jsonObject.put("channel_id", channelID);
            if (!jsonObject.has("channel_type"))
                jsonObject.put("channel_type", channelType);
        } catch (JSONException e) {
            MSLoggerUtils.getInstance().e(TAG, "handleCMD put json error");
        }
        handleCMD(jsonObject);
    }

    public void handleCMD(JSONObject json) {
        if (json == null) return;
        //内部消息
        if (json.has("cmd")) {
            String cmd = json.optString("cmd");

            JSONObject jsonObject = null;
            if (json.has("param")) {
                jsonObject = json.optJSONObject("param");
            }
            if (jsonObject == null) {
                jsonObject = new JSONObject();
            }

            try {
                if (json.has("channel_id") && !jsonObject.has("channel_id")) {
                    jsonObject.put("channel_id", json.optString("channel_id"));
                }
                if (json.has("channel_type") && !jsonObject.has("channel_type")) {
                    jsonObject.put("channel_type", json.optString("channel_type"));
                }
            } catch (JSONException e) {
               MSLoggerUtils.getInstance().e(TAG,"handleCMD put json error");
            }
            if (cmd.equalsIgnoreCase(MSCMDKeys.ms_memberUpdate)) {
                //更新频道成员
                if (jsonObject.has("group_no")) {
                    String group_no = jsonObject.optString("group_no");
                    ChannelMembersManager.getInstance().setOnSyncChannelMembers(group_no, MSChannelType.GROUP);
                }
            } else if (cmd.equalsIgnoreCase(MSCMDKeys.ms_groupAvatarUpdate)) {
                //更新频道头像
                if (jsonObject.has("group_no")) {
                    String group_no = jsonObject.optString("group_no");
                    MSIM.getInstance().getChannelManager().setOnRefreshChannelAvatar(group_no, MSChannelType.GROUP);
                }
            } else if (cmd.equals(MSCMDKeys.ms_userAvatarUpdate)) {
                //个人头像更新
                if (jsonObject.has("uid")) {
                    String uid = jsonObject.optString("uid");
                    MSIM.getInstance().getChannelManager().setOnRefreshChannelAvatar(uid, MSChannelType.PERSONAL);
                }
            } else if (cmd.equalsIgnoreCase(MSCMDKeys.ms_channelUpdate)) {
                //频道修改
                if (jsonObject.has("channel_id") && jsonObject.has("channel_type")) {
                    String channelID = jsonObject.optString("channel_id");
                    byte channelType = (byte) jsonObject.optInt("channel_type");
                    MSIM.getInstance().getChannelManager().fetchChannelInfo(channelID, channelType);
                }
            } else if (cmd.equalsIgnoreCase(MSCMDKeys.ms_unreadClear)) {
                //清除消息红点
                if (jsonObject.has("channel_id") && jsonObject.has("channel_type")) {
                    String channelId = jsonObject.optString("channel_id");
                    int channelType = jsonObject.optInt("channel_type");
                    int unreadCount = jsonObject.optInt("unread");
                    MSIM.getInstance().getConversationManager().updateRedDot(channelId, (byte) channelType, unreadCount);
                }
            } else if (cmd.equalsIgnoreCase(MSCMDKeys.ms_voiceReaded)) {
                //语音已读
                if (jsonObject.has("message_id")) {
                    String messageId = jsonObject.optString("message_id");
                    MsgDbManager.getInstance().updateFieldWithMessageID(messageId, MSDBColumns.MSMessageColumns.voice_status, 1 + "");
                }
            } else if (cmd.equalsIgnoreCase(MSCMDKeys.ms_onlineStatus)) {
                //对方是否在线
//                int online = jsonObject.optInt("online");

                int online;
                String uid = jsonObject.optString("uid");
//                int device_flag = jsonObject.optInt("device_flag");
                int main_device_flag = jsonObject.optInt("main_device_flag");
                int all_offline = 0;
                if (jsonObject.has("all_offline")) all_offline = jsonObject.optInt("all_offline");
                online = all_offline == 1 ? 0 : 1;
                MSChannel msChannel = MSIM.getInstance().getChannelManager().getChannel(uid, MSChannelType.PERSONAL);
                if (msChannel != null) {
                    msChannel.online = online;
                    if (msChannel.online == 0) {
                        msChannel.lastOffline = DateUtils.getInstance().getCurrentSeconds();
                    }
//                    msChannel.allOffline = all_offline;
//                    msChannel.mainDeviceFlag = main_device_flag;
                    msChannel.deviceFlag = main_device_flag;
//                    msChannel.deviceFlag = device_flag;
                    MSIM.getInstance().getChannelManager().saveOrUpdateChannel(msChannel);
                }
            } else if (cmd.equals(MSCMDKeys.ms_message_erase)) {
                String erase_type = "";
                String from_uid = "";
                if (jsonObject.has("erase_type")) {
                    erase_type = jsonObject.optString("erase_type");
                }
                if (jsonObject.has("from_uid")) {
                    from_uid = jsonObject.optString("from_uid");
                }
                String channelID = jsonObject.optString("channel_id");
                byte channelType = (byte) jsonObject.optInt("channel_type");
                if (!TextUtils.isEmpty(erase_type)) {
                    if (erase_type.equals("all")) {
                        if (!TextUtils.isEmpty(channelID)) {
                            MSIM.getInstance().getMsgManager().clearWithChannel(channelID, channelType);
                        }
                    } else {
                        if (!TextUtils.isEmpty(from_uid)) {
                            MSIM.getInstance().getMsgManager().clearWithChannelAndFromUID(channelID, channelType, from_uid);
                        }
                    }
                }
            } else if (cmd.equals(MSCMDKeys.ms_conversation_delete)) {
                String channelID = jsonObject.optString("channel_id");
                byte channelType = (byte) jsonObject.optInt("channel_type");
                if (!TextUtils.isEmpty(channelID)) {
                    ConversationDbManager.getInstance().deleteWithChannel(channelID, channelType, 1);
                }
            }
            MSCMD mscmd = new MSCMD(cmd, jsonObject);
            MSLoggerUtils.getInstance().e("处理cmd："+cmd);
            pushCMDs(mscmd);
        }

    }

    /**
     * 处理cmd
     *
     * @param cmd   cmd
     * @param param 参数
     */
    public void handleCMD(String cmd, String param, String sign) {
        if (TextUtils.isEmpty(cmd)) return;
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("cmd", cmd);
            if (!TextUtils.isEmpty(param)) {
                JSONObject paramJson = new JSONObject(param);
                jsonObject.put("param", paramJson);
            }
            if (!TextUtils.isEmpty(sign)) {
                jsonObject.put("sign", sign);
            }

        } catch (JSONException e) {
           MSLoggerUtils.getInstance().e(TAG,"handleCMD put json error");
            return;
        }
        handleCMD(jsonObject);
    }

    public synchronized void addCmdListener(String key, ICMDListener icmdListener) {
        if (TextUtils.isEmpty(key) || icmdListener == null) return;
        if (cmdListenerMap == null) cmdListenerMap = new ConcurrentHashMap<>();
        cmdListenerMap.put(key, icmdListener);
    }

    public void removeCmdListener(String key) {
        if (TextUtils.isEmpty(key) || cmdListenerMap == null) return;
        cmdListenerMap.remove(key);
    }

    private void pushCMDs(MSCMD mscmd) {
        if (cmdListenerMap != null && !cmdListenerMap.isEmpty()) {
            runOnMainThread(() -> {
                for (Map.Entry<String, ICMDListener> entry : cmdListenerMap.entrySet()) {
                    entry.getValue().onMsg(mscmd);
                }
            });
        }
    }

    public void setRSAPublicKey(String key) {
        //  key = new String(MSAESEncryptUtils.base64Decode(key));
        MSIMApplication.getInstance().setRSAPublicKey(key);
    }

    public String getRSAPublicKey() {
        return MSIMApplication.getInstance().getRSAPublicKey();
    }
}
