package com.mushanyux.mushanim.manager;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.mushanyux.mushanim.MSIM;
import com.mushanyux.mushanim.MSIMApplication;
import com.mushanyux.mushanim.db.ConversationDbManager;
import com.mushanyux.mushanim.db.MsgDbManager;
import com.mushanyux.mushanim.db.MSDBColumns;
import com.mushanyux.mushanim.entity.MSChannel;
import com.mushanyux.mushanim.entity.MSChannelType;
import com.mushanyux.mushanim.entity.MSConversationMsg;
import com.mushanyux.mushanim.entity.MSMentionInfo;
import com.mushanyux.mushanim.entity.MSMessageGroupByDate;
import com.mushanyux.mushanim.entity.MSMessageSearchResult;
import com.mushanyux.mushanim.entity.MSMsg;
import com.mushanyux.mushanim.entity.MSMsgExtra;
import com.mushanyux.mushanim.entity.MSMsgReaction;
import com.mushanyux.mushanim.entity.MSMsgSetting;
import com.mushanyux.mushanim.entity.MSSendOptions;
import com.mushanyux.mushanim.entity.MSSyncExtraMsg;
import com.mushanyux.mushanim.entity.MSSyncMsg;
import com.mushanyux.mushanim.entity.MSSyncMsgReaction;
import com.mushanyux.mushanim.entity.MSSyncRecent;
import com.mushanyux.mushanim.entity.MSUIConversationMsg;
import com.mushanyux.mushanim.interfaces.IClearMsgListener;
import com.mushanyux.mushanim.interfaces.IDeleteMsgListener;
import com.mushanyux.mushanim.interfaces.IGetOrSyncHistoryMsgBack;
import com.mushanyux.mushanim.interfaces.IMessageStoreBeforeIntercept;
import com.mushanyux.mushanim.interfaces.INewMsgListener;
import com.mushanyux.mushanim.interfaces.IRefreshMsg;
import com.mushanyux.mushanim.interfaces.ISendACK;
import com.mushanyux.mushanim.interfaces.ISendMsgCallBackListener;
import com.mushanyux.mushanim.interfaces.ISyncChannelMsgBack;
import com.mushanyux.mushanim.interfaces.ISyncChannelMsgListener;
import com.mushanyux.mushanim.interfaces.ISyncOfflineMsgBack;
import com.mushanyux.mushanim.interfaces.ISyncOfflineMsgListener;
import com.mushanyux.mushanim.interfaces.IUploadAttacResultListener;
import com.mushanyux.mushanim.interfaces.IUploadAttachmentListener;
import com.mushanyux.mushanim.interfaces.IUploadMsgExtraListener;
import com.mushanyux.mushanim.message.MessageHandler;
import com.mushanyux.mushanim.message.MSConnection;
import com.mushanyux.mushanim.message.type.MSMsgContentType;
import com.mushanyux.mushanim.message.type.MSSendMsgResult;
import com.mushanyux.mushanim.msgmodel.MSFormatErrorContent;
import com.mushanyux.mushanim.msgmodel.MSImageContent;
import com.mushanyux.mushanim.msgmodel.MSMessageContent;
import com.mushanyux.mushanim.msgmodel.MSMsgEntity;
import com.mushanyux.mushanim.msgmodel.MSReply;
import com.mushanyux.mushanim.msgmodel.MSTextContent;
import com.mushanyux.mushanim.msgmodel.MSVideoContent;
import com.mushanyux.mushanim.msgmodel.MSVoiceContent;
import com.mushanyux.mushanim.utils.DateUtils;
import com.mushanyux.mushanim.utils.MSCommonUtils;
import com.mushanyux.mushanim.utils.MSLoggerUtils;
import com.mushanyux.mushanim.utils.MSTypeUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MsgManager extends BaseManager {
    private final String TAG = "MsgManager";

    private MsgManager() {
    }

    private static class MsgManagerBinder {
        static final MsgManager msgManager = new MsgManager();
    }

    public static MsgManager getInstance() {
        return MsgManagerBinder.msgManager;
    }

    private final long msOrderSeqFactor = 1000L;
    // 消息修改
    private ConcurrentHashMap<String, IRefreshMsg> refreshMsgListenerMap;
    // 监听发送消息回调
    private ConcurrentHashMap<String, ISendMsgCallBackListener> sendMsgCallBackListenerHashMap;
    // 删除消息监听
    private ConcurrentHashMap<String, IDeleteMsgListener> deleteMsgListenerMap;
    // 发送消息ack监听
    private ConcurrentHashMap<String, ISendACK> sendAckListenerMap;
    // 新消息监听
    private ConcurrentHashMap<String, INewMsgListener> newMsgListenerMap;
    // 清空消息
    private ConcurrentHashMap<String, IClearMsgListener> clearMsgMap;
    // 上传文件附件
    private IUploadAttachmentListener iUploadAttachmentListener;
    // 同步离线消息
    private ISyncOfflineMsgListener iOfflineMsgListener;
    // 同步channel内消息
    private ISyncChannelMsgListener iSyncChannelMsgListener;

    // 消息存库拦截器
    private IMessageStoreBeforeIntercept messageStoreBeforeIntercept;
    // 自定义消息model
    private List<Class<? extends MSMessageContent>> customContentMsgList;
    // 上传消息扩展
    private IUploadMsgExtraListener iUploadMsgExtraListener;
    private Timer checkMsgNeedUploadTimer;

    // 初始化默认消息model
    public void initNormalMsg() {
        if (customContentMsgList == null) {
            customContentMsgList = new ArrayList<>();
            customContentMsgList.add(MSTextContent.class);
            customContentMsgList.add(MSImageContent.class);
            customContentMsgList.add(MSVideoContent.class);
            customContentMsgList.add(MSVoiceContent.class);
        }
    }

    /**
     * 注册消息module
     *
     * @param contentMsg 消息
     */
    public void registerContentMsg(Class<? extends MSMessageContent> contentMsg) {
        if (MSCommonUtils.isEmpty(customContentMsgList))
            initNormalMsg();
        try {
            boolean isAdd = true;
            for (int i = 0, size = customContentMsgList.size(); i < size; i++) {
                if (customContentMsgList.get(i).getDeclaredConstructor().newInstance().type == contentMsg.newInstance().type) {
                    isAdd = false;
                    break;
                }
            }
            if (isAdd)
                customContentMsgList.add(contentMsg);
        } catch (IllegalAccessException | InstantiationException | NoSuchMethodException |
                 InvocationTargetException e) {
            MSLoggerUtils.getInstance().e(TAG, "registerContentMsg error " + e.getLocalizedMessage());
        }

    }

    // 通过json获取消息model
    public MSMessageContent getMsgContentModel(JSONObject jsonObject) {
        int type = jsonObject.optInt("type");
        MSMessageContent messageContent = getMsgContentModel(type, jsonObject);
        return messageContent;
    }

    public MSMessageContent getMsgContentModel(String jsonStr) {
        if (TextUtils.isEmpty(jsonStr)) {
            return new MSFormatErrorContent();
        }
        JSONObject jsonObject = null;
        try {
            jsonObject = new JSONObject(jsonStr);
        } catch (JSONException e) {
            MSLoggerUtils.getInstance().e(TAG, "getMsgContentModel The parameter is not a JSON");
        }
        if (jsonObject == null) {
            return new MSFormatErrorContent();
        }
        return getMsgContentModel(jsonObject);
    }

    public MSMessageContent getMsgContentModel(int contentType, JSONObject jsonObject) {
        if (jsonObject == null) jsonObject = new JSONObject();
        MSMessageContent baseContentMsgModel = getContentMsgModel(contentType, jsonObject);
        if (baseContentMsgModel == null) {
            baseContentMsgModel = new MSMessageContent();
        }
        //解析@成员列表
        if (jsonObject.has("mention")) {
            JSONObject tempJson = jsonObject.optJSONObject("mention");
            if (tempJson != null) {
                //是否@所有人
                if (tempJson.has("all"))
                    baseContentMsgModel.mentionAll = tempJson.optInt("all");
                JSONArray uidList = tempJson.optJSONArray("uids");

                if (uidList != null && uidList.length() > 0) {
                    MSMentionInfo mentionInfo = new MSMentionInfo();
                    List<String> mentionInfoUIDs = new ArrayList<>();
                    for (int i = 0, size = uidList.length(); i < size; i++) {
                        String uid = uidList.optString(i);
                        if (uid.equals(MSIMApplication.getInstance().getUid())) {
                            mentionInfo.isMentionMe = true;
                        }
                        mentionInfoUIDs.add(uid);
                    }
                    mentionInfo.uids = mentionInfoUIDs;
                    if (baseContentMsgModel.mentionAll == 1) {
                        mentionInfo.isMentionMe = true;
                    }
                    baseContentMsgModel.mentionInfo = mentionInfo;
                }
            }
        }

        if (jsonObject.has("from_uid"))
            baseContentMsgModel.fromUID = jsonObject.optString("from_uid");
        if (jsonObject.has("flame"))
            baseContentMsgModel.flame = jsonObject.optInt("flame");
        if (jsonObject.has("flame_second"))
            baseContentMsgModel.flameSecond = jsonObject.optInt("flame_second");
        if (jsonObject.has("robot_id"))
            baseContentMsgModel.robotID = jsonObject.optString("robot_id");

        //判断消息中是否包含回复情况
        if (jsonObject.has("reply")) {
            baseContentMsgModel.reply = new MSReply();
            JSONObject replyJson = jsonObject.optJSONObject("reply");
            if (replyJson != null) {
                baseContentMsgModel.reply = baseContentMsgModel.reply.decodeMsg(replyJson);
            }
        }

        if (jsonObject.has("entities")) {
            JSONArray jsonArray = jsonObject.optJSONArray("entities");
            if (jsonArray != null && jsonArray.length() > 0) {
                List<MSMsgEntity> list = new ArrayList<>();
                for (int i = 0; i < jsonArray.length(); i++) {
                    MSMsgEntity entity = new MSMsgEntity();
                    JSONObject jo = jsonArray.optJSONObject(i);
                    entity.type = jo.optString("type");
                    entity.offset = jo.optInt("offset");
                    entity.length = jo.optInt("length");
                    entity.value = jo.optString("value");
                    list.add(entity);
                }
                baseContentMsgModel.entities = list;
            }
        }
        return baseContentMsgModel;
    }

    /**
     * 将json消息转成对于的消息model
     *
     * @param type       content type
     * @param jsonObject content json
     * @return model
     */
    private MSMessageContent getContentMsgModel(int type, JSONObject jsonObject) {
        Class<? extends MSMessageContent> baseMsg = null;
        if (customContentMsgList != null && !customContentMsgList.isEmpty()) {
            try {
                for (int i = 0, size = customContentMsgList.size(); i < size; i++) {
                    if (customContentMsgList.get(i).getDeclaredConstructor().newInstance().type == type) {
                        baseMsg = customContentMsgList.get(i);
                        break;
                    }
                }
            } catch (IllegalAccessException | InstantiationException | NoSuchMethodException |
                     InvocationTargetException e) {
                MSLoggerUtils.getInstance().e(TAG, "getContentMsgModel error" + e.getLocalizedMessage());
                return null;
            }
        }
        try {
            // 注册的消息model必须提供无参的构造方法
            if (baseMsg != null) {
                return baseMsg.newInstance().decodeMsg(jsonObject);
            }
        } catch (IllegalAccessException | InstantiationException e) {
            MSLoggerUtils.getInstance().e(TAG, "getContentMsgModel decodeMsg error");
            return null;
        }
        return null;
    }

    private long getOrNearbyMsgSeq(long orderSeq) {
        if (orderSeq % msOrderSeqFactor == 0) {
            return orderSeq / msOrderSeqFactor;
        }
        return (orderSeq - orderSeq % msOrderSeqFactor) / msOrderSeqFactor;
    }

    /**
     * 查询或同步某个频道消息
     *
     * @param channelId                频道ID
     * @param channelType              频道类型
     * @param oldestOrderSeq           最后一次消息大orderSeq 第一次进入聊天传入0
     * @param contain                  是否包含 oldestOrderSeq 这条消息
     * @param pullMode                 拉取模式 0:向下拉取 1:向上拉取
     * @param aroundMsgOrderSeq        查询此消息附近消息
     * @param limit                    每次获取数量
     * @param iGetOrSyncHistoryMsgBack 请求返还
     */
    public void getOrSyncHistoryMessages(String channelId, byte channelType, long oldestOrderSeq, boolean contain, int pullMode, int limit, long aroundMsgOrderSeq, final IGetOrSyncHistoryMsgBack iGetOrSyncHistoryMsgBack) {
        new Thread(() -> {
            int tempPullMode = pullMode;
            long tempOldestOrderSeq = oldestOrderSeq;
            boolean tempContain = contain;
            if (aroundMsgOrderSeq != 0) {
                long maxMsgSeq =
                        MsgDbManager.getInstance().queryMaxMessageSeqNotDeletedWithChannel(channelId, channelType);
                long aroundMsgSeq = getOrNearbyMsgSeq(aroundMsgOrderSeq);

                if (maxMsgSeq >= aroundMsgSeq && maxMsgSeq - aroundMsgSeq <= limit) {
                    // 显示最后一页数据
                    tempOldestOrderSeq = getMaxOrderSeqWithChannel(channelId, channelType);
                    if (tempOldestOrderSeq < aroundMsgOrderSeq) {
                        tempOldestOrderSeq = aroundMsgOrderSeq;
                    }
                    tempContain = true;
                    tempPullMode = 0;
                } else {
                    long minOrderSeq = MsgDbManager.getInstance().queryOrderSeq(channelId, channelType, aroundMsgOrderSeq, 3);
                    if (minOrderSeq == 0) {
                        tempOldestOrderSeq = aroundMsgOrderSeq;
                    } else {
                        if (minOrderSeq + limit < aroundMsgOrderSeq) {
                            if (aroundMsgOrderSeq % msOrderSeqFactor == 0) {
                                tempOldestOrderSeq = (aroundMsgOrderSeq / msOrderSeqFactor - 3) * msOrderSeqFactor;
                            } else
                                tempOldestOrderSeq = aroundMsgOrderSeq - 3;
                        } else {
                            // todo 这里只会查询3条数据  oldestOrderSeq = minOrderSeq
                            long startOrderSeq = MsgDbManager.getInstance().queryOrderSeq(channelId, channelType, aroundMsgOrderSeq, limit);
                            if (startOrderSeq == 0) {
                                tempOldestOrderSeq = aroundMsgOrderSeq;
                            } else
                                tempOldestOrderSeq = startOrderSeq;
                        }
                    }
                    tempPullMode = 1;
                    tempContain = true;
                }
            }
            MsgDbManager.getInstance().queryOrSyncHistoryMessages(channelId, channelType, tempOldestOrderSeq, tempContain, tempPullMode, limit, iGetOrSyncHistoryMsgBack);
        }).start();
    }

    public List<MSMsg> getAll() {
        return MsgDbManager.getInstance().queryAll();
    }

    public List<MSMsg> getWithFromUID(String channelID, byte channelType, String fromUID, long oldestOrderSeq, int limit) {
        return MsgDbManager.getInstance().queryWithFromUID(channelID, channelType, fromUID, oldestOrderSeq, limit);
    }

    /**
     * 批量删除消息
     *
     * @param clientMsgNos 消息编号集合
     */
    public void deleteWithClientMsgNos(List<String> clientMsgNos) {
        if (MSCommonUtils.isEmpty(clientMsgNos)) return;
        List<MSMsg> list = new ArrayList<>();
        try {
            MSIMApplication.getInstance().getDbHelper().getDb()
                    .beginTransaction();
            for (int i = 0, size = clientMsgNos.size(); i < size; i++) {
                MSMsg msg = MsgDbManager.getInstance().deleteWithClientMsgNo(clientMsgNos.get(i));
                if (msg != null) {
                    list.add(msg);
                }
            }
            MSIMApplication.getInstance().getDbHelper().getDb()
                    .setTransactionSuccessful();
        } catch (Exception ignored) {
        } finally {
            if (MSIMApplication.getInstance().getDbHelper().getDb().inTransaction()) {
                MSIMApplication.getInstance().getDbHelper().getDb()
                        .endTransaction();
            }
        }
        List<MSMsg> deleteMsgList = new ArrayList<>();
        for (int i = 0, size = list.size(); i < size; i++) {
            setDeleteMsg(list.get(i));
            boolean isAdd = true;
            for (int j = 0, len = deleteMsgList.size(); j < len; j++) {
                if (deleteMsgList.get(j).channelID.equals(list.get(i).channelID)
                        && deleteMsgList.get(j).channelType == list.get(i).channelType) {
                    isAdd = false;
                    break;
                }
            }
            if (isAdd) deleteMsgList.add(list.get(i));
        }
        List<MSUIConversationMsg> uiMsgList = new ArrayList<>();
        for (int i = 0, size = deleteMsgList.size(); i < size; i++) {
            MSMsg msg = MsgDbManager.getInstance().queryMaxOrderSeqMsgWithChannel(deleteMsgList.get(i).channelID, deleteMsgList.get(i).channelType);
            if (msg != null) {
                MSUIConversationMsg uiMsg = MSIM.getInstance().getConversationManager().updateWithMSMsg(msg);
                if (uiMsg != null) {
                    uiMsgList.add(uiMsg);
//                    MSIM.getInstance().getConversationManager().setOnRefreshMsg(uiMsg, i == deleteMsgList.size()
//                            - 1, "deleteWithClientMsgNOList");
                }
            }
        }
        MSIM.getInstance().getConversationManager().setOnRefreshMsg(uiMsgList, "deleteWithClientMsgNOList");
    }

    public List<MSMsg> getExpireMessages(int limit) {
        long time = DateUtils.getInstance().getCurrentSeconds();
        return MsgDbManager.getInstance().queryExpireMessages(time, limit);
    }

    /**
     * 删除某条消息
     *
     * @param client_seq 客户端序列号
     */
    public boolean deleteWithClientSeq(long client_seq) {
        return MsgDbManager.getInstance().deleteWithClientSeq(client_seq);
    }

    /**
     * 查询某条消息所在行
     *
     * @param channelID   频道ID
     * @param channelType 频道类型
     * @param clientMsgNo 客户端消息ID
     * @return int
     */
    public int getRowNoWithOrderSeq(String channelID, byte channelType, String clientMsgNo) {
        MSMsg msg = MsgDbManager.getInstance().queryWithClientMsgNo(clientMsgNo);
        return MsgDbManager.getInstance().queryRowNoWithOrderSeq(channelID, channelType, msg == null ? 0 : msg.orderSeq);
    }

    public int getRowNoWithMessageID(String channelID, byte channelType, String messageID) {
        MSMsg msg = MsgDbManager.getInstance().queryWithMessageID(messageID, false);
        return MsgDbManager.getInstance().queryRowNoWithOrderSeq(channelID, channelType, msg == null ? 0 : msg.orderSeq);
    }

    public void deleteWithClientMsgNO(String clientMsgNo) {
        MSMsg msg = MsgDbManager.getInstance().deleteWithClientMsgNo(clientMsgNo);
        if (msg != null) {
            setDeleteMsg(msg);
            MSConversationMsg conversationMsg = MSIM.getInstance().getConversationManager().getWithChannel(msg.channelID, msg.channelType);
            if (conversationMsg != null && conversationMsg.lastClientMsgNO.equals(clientMsgNo)) {
                MSMsg tempMsg = MsgDbManager.getInstance().queryMaxOrderSeqMsgWithChannel(msg.channelID, msg.channelType);
                if (tempMsg != null) {
                    MSUIConversationMsg uiMsg = ConversationDbManager.getInstance().insertOrUpdateWithMsg(tempMsg, 0);
                    MSIM.getInstance().getConversationManager().setOnRefreshMsg(uiMsg, "deleteWithClientMsgNO");
                }
            }
        }
    }


    public boolean deleteWithMessageID(String messageID) {
        return MsgDbManager.getInstance().deleteWithMessageID(messageID);
    }

    public MSMsg getWithMessageID(String messageID) {
        return MsgDbManager.getInstance().queryWithMessageID(messageID, true);
    }

    public List<MSMsg> getWithMessageIDs(List<String> msgIds) {
        return MsgDbManager.getInstance().queryWithMsgIds(msgIds);
    }

    public int isDeletedMsg(JSONObject jsonObject) {
        int isDelete = 0;
        //消息可见数组
        if (jsonObject != null && jsonObject.has("visibles")) {
            boolean isIncludeLoginUser = false;
            JSONArray jsonArray = jsonObject.optJSONArray("visibles");
            if (jsonArray != null && jsonArray.length() > 0) {
                for (int i = 0, size = jsonArray.length(); i < size; i++) {
                    if (jsonArray.optString(i).equals(MSIMApplication.getInstance().getUid())) {
                        isIncludeLoginUser = true;
                        break;
                    }
                }
            }
            isDelete = isIncludeLoginUser ? 0 : 1;
        }
        return isDelete;
    }

    public List<MSMsg> getWithFlame() {
        return MsgDbManager.getInstance().queryWithFlame();
    }

    public long getMessageOrderSeq(long messageSeq, String channelID, byte channelType) {
        if (messageSeq == 0) {
            long tempOrderSeq = MsgDbManager.getInstance().queryMaxOrderSeqWithChannel(channelID, channelType);
            return tempOrderSeq + 1;
        }
        return messageSeq * msOrderSeqFactor;
    }

    public long getMessageSeq(long messageOrderSeq) {
        if (messageOrderSeq % msOrderSeqFactor == 0) {
            return messageOrderSeq / msOrderSeqFactor;
        }
        return 0;
    }

    public long getReliableMessageSeq(long messageOrderSeq) {
        return messageOrderSeq / msOrderSeqFactor;
    }

    /**
     * use getMaxReactionSeqWithChannel
     *
     * @param channelID   channelId
     * @param channelType channelType
     * @return channel reaction max seq version
     */
    @Deprecated
    public long getMaxSeqWithChannel(String channelID, byte channelType) {
        return MsgDbManager.getInstance().getMaxReactionSeqWithChannel(channelID, channelType);
    }

    public long getMaxReactionSeqWithChannel(String channelID, byte channelType) {
        return MsgDbManager.getInstance().getMaxReactionSeqWithChannel(channelID, channelType);
    }


    public void saveMessageReactions(List<MSSyncMsgReaction> list) {
        if (MSCommonUtils.isEmpty(list)) return;
        List<MSMsgReaction> reactionList = new ArrayList<>();
        List<String> msgIds = new ArrayList<>();
        for (int i = 0, size = list.size(); i < size; i++) {
            MSMsgReaction reaction = new MSMsgReaction();
            reaction.messageID = list.get(i).message_id;
            reaction.channelID = list.get(i).channel_id;
            reaction.channelType = list.get(i).channel_type;
            reaction.uid = list.get(i).uid;
            reaction.name = list.get(i).name;
            reaction.seq = list.get(i).seq;
            reaction.emoji = list.get(i).emoji;
            reaction.isDeleted = list.get(i).is_deleted;
            reaction.createdAt = list.get(i).created_at;
            msgIds.add(list.get(i).message_id);
            reactionList.add(reaction);
        }
        saveMsgReactions(reactionList);
        List<MSMsg> msgList = MsgDbManager.getInstance().queryWithMsgIds(msgIds);
        getMsgReactionsAndRefreshMsg(msgIds, msgList);
    }

    public int getMaxMessageSeq() {
        return MsgDbManager.getInstance().queryMaxMessageSeqWithChannel();
    }

    public int getMaxMessageSeqWithChannel(String channelID, byte channelType) {
        return MsgDbManager.getInstance().queryMaxMessageSeqWithChannel(channelID, channelType);
    }

    public int getMaxOrderSeqWithChannel(String channelID, byte channelType) {
        return MsgDbManager.getInstance().queryMaxMessageOrderSeqWithChannel(channelID, channelType);
    }

    public int getMinMessageSeqWithChannel(String channelID, byte channelType) {
        return MsgDbManager.getInstance().queryMinMessageSeqWithChannel(channelID, channelType);
    }


    public List<MSMsgReaction> getMsgReactions(String messageID) {
        List<String> ids = new ArrayList<>();
        ids.add(messageID);
        return MsgDbManager.getInstance().queryMsgReactionWithMsgIds(ids);
    }

    private void getMsgReactionsAndRefreshMsg(List<String> messageIds, List<MSMsg> updatedMsgList) {
        List<MSMsgReaction> reactionList = MsgDbManager.getInstance().queryMsgReactionWithMsgIds(messageIds);
        for (int i = 0, size = updatedMsgList.size(); i < size; i++) {
            for (int j = 0, len = reactionList.size(); j < len; j++) {
                if (updatedMsgList.get(i).messageID.equals(reactionList.get(j).messageID)) {
                    if (updatedMsgList.get(i).reactionList == null)
                        updatedMsgList.get(i).reactionList = new ArrayList<>();
                    updatedMsgList.get(i).reactionList.add(reactionList.get(j));
                }
            }
            setRefreshMsg(updatedMsgList.get(i), i == updatedMsgList.size() - 1);
        }
    }


    public synchronized long getClientSeq() {
        return MsgDbManager.getInstance().queryMaxMessageSeqWithChannel();
    }

    /**
     * 修改消息的扩展字段
     *
     * @param clientMsgNo 客户端ID
     * @param hashExtra   扩展字段
     */
    public boolean updateLocalExtraWithClientMsgNO(String clientMsgNo, HashMap<String, Object> hashExtra) {
        if (hashExtra != null) {
            JSONObject jsonObject = new JSONObject();
            for (String key : hashExtra.keySet()) {
                try {
                    jsonObject.put(key, hashExtra.get(key));
                } catch (JSONException e) {
                    MSLoggerUtils.getInstance().e(TAG, "updateLocalExtraWithClientMsgNO local_extra is not a JSON");
                }
            }
            return MsgDbManager.getInstance().updateFieldWithClientMsgNo(clientMsgNo, MSDBColumns.MSMessageColumns.extra, jsonObject.toString(), true);
        }

        return false;
    }

    /**
     * 查询按日期分组的消息数量
     *
     * @param channelID   频道ID
     * @param channelType 频道类型
     * @return List<MSMessageGroupByDate>
     */
    public List<MSMessageGroupByDate> getMessageGroupByDateWithChannel(String channelID, byte channelType) {
        return MsgDbManager.getInstance().queryMessageGroupByDateWithChannel(channelID, channelType);
    }

    public void clearAll() {
        MsgDbManager.getInstance().clearEmpty();
    }

    public void saveMsg(MSMsg msg) {
        int refreshType = 0;
        if (!TextUtils.isEmpty(msg.clientMsgNO)) {
            MSMsg tempMsg = MsgDbManager.getInstance().queryWithClientMsgNo(msg.clientMsgNO);
            if (tempMsg != null) {
                refreshType = 1;
            }
        }
        if (msg.orderSeq == 0) {
            long tempOrderSeq = getMessageOrderSeq(0, msg.channelID, msg.channelType);
            msg.orderSeq = tempOrderSeq + 1;
        }
        msg.clientSeq = MsgDbManager.getInstance().insert(msg);
        if (refreshType == 0)
            pushNewMsg(msg);
        else setRefreshMsg(msg, true);
    }

    /**
     * 本地插入一条消息并更新会话记录表且未读消息数量加一
     *
     * @param msMsg      消息对象
     * @param addRedDots 是否显示红点
     */
    public void saveAndUpdateConversationMsg(MSMsg msMsg, boolean addRedDots) {
        int refreshType = 0;
        if (!TextUtils.isEmpty(msMsg.clientMsgNO)) {
            MSMsg tempMsg = MsgDbManager.getInstance().queryWithClientMsgNo(msMsg.clientMsgNO);
            if (tempMsg != null) {
                refreshType = 1;
            }
        }
        if (msMsg.orderSeq == 0) {
            long tempOrderSeq = getMessageOrderSeq(0, msMsg.channelID, msMsg.channelType);
            msMsg.orderSeq = tempOrderSeq + 1;
        }
        msMsg.clientSeq = MsgDbManager.getInstance().insert(msMsg);
        if (refreshType == 0)
            pushNewMsg(msMsg);
        else setRefreshMsg(msMsg, true);
        MSUIConversationMsg msg = ConversationDbManager.getInstance().insertOrUpdateWithMsg(msMsg, addRedDots ? 1 : 0);
        MSIM.getInstance().getConversationManager().setOnRefreshMsg(msg, "insertAndUpdateConversationMsg");
    }

    /**
     * 查询某个频道的固定类型消息
     *
     * @param channelID      频道ID
     * @param channelType    频道列席
     * @param oldestOrderSeq 最后一次消息大orderSeq
     * @param limit          每次获取数量
     * @param contentTypes   消息内容类型
     * @return List<MSMsg>
     */
    public List<MSMsg> searchMsgWithChannelAndContentTypes(String channelID, byte channelType, long oldestOrderSeq, int limit, int[] contentTypes) {
        return MsgDbManager.getInstance().searchWithChannelAndContentTypes(channelID, channelType, oldestOrderSeq, limit, contentTypes);
    }

    /**
     * 搜索某个频道到消息
     *
     * @param searchKey   关键字
     * @param channelID   频道ID
     * @param channelType 频道类型
     * @return List<MSMsg>
     */
    public List<MSMsg> searchWithChannel(String searchKey, String channelID, byte channelType) {
        return MsgDbManager.getInstance().searchWithChannel(searchKey, channelID, channelType);
    }

    public List<MSMessageSearchResult> search(String searchKey) {
        return MsgDbManager.getInstance().search(searchKey);
    }

    /**
     * 修改语音是否已读
     *
     * @param clientMsgNo 客户端ID
     * @param isReaded    1：已读
     */
    public boolean updateVoiceReadStatus(String clientMsgNo, int isReaded, boolean isRefreshUI) {
        return MsgDbManager.getInstance().updateFieldWithClientMsgNo(clientMsgNo, MSDBColumns.MSMessageColumns.voice_status, String.valueOf(isReaded), isRefreshUI);
    }

    /**
     * 清空某个会话信息
     *
     * @param channelId   频道ID
     * @param channelType 频道类型
     */
    public boolean clearWithChannel(String channelId, byte channelType) {
        boolean result = MsgDbManager.getInstance().deleteWithChannel(channelId, channelType);
        if (result) {
            if (clearMsgMap != null && !clearMsgMap.isEmpty()) {
                runOnMainThread(() -> {
                    for (Map.Entry<String, IClearMsgListener> entry : clearMsgMap.entrySet()) {
                        entry.getValue().clear(channelId, channelType, "");
                    }
                });

            }
        }
        return result;
    }

    public boolean clearWithChannelAndFromUID(String channelId, byte channelType, String fromUID) {
        boolean result = MsgDbManager.getInstance().deleteWithChannelAndFromUID(channelId, channelType, fromUID);
        if (result) {
            if (clearMsgMap != null && !clearMsgMap.isEmpty()) {
                runOnMainThread(() -> {
                    for (Map.Entry<String, IClearMsgListener> entry : clearMsgMap.entrySet()) {
                        entry.getValue().clear(channelId, channelType, fromUID);
                    }
                });

            }
        }
        return result;
    }


    public boolean updateContentAndRefresh(String clientMsgNo, String content, boolean isRefreshUI) {
        return MsgDbManager.getInstance().updateFieldWithClientMsgNo(clientMsgNo, MSDBColumns.MSMessageColumns.content, content, isRefreshUI);
    }


    public boolean updateContentAndRefresh(String clientMsgNo, MSMessageContent model, boolean isRefreshUI) {
        JSONObject jsonObject = model.encodeMsg();
        try {
            if (jsonObject == null) {
                jsonObject = new JSONObject();
            }
            jsonObject.put("type", model.type);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return updateContentAndRefresh(clientMsgNo, jsonObject.toString(), isRefreshUI);
    }


    public void updateViewedAt(int viewed, long viewedAt, String clientMsgNo) {
        MsgDbManager.getInstance().updateViewedAt(viewed, viewedAt, clientMsgNo);
    }

    /**
     * 获取某个类型的聊天数据
     *
     * @param type            消息类型
     * @param oldestClientSeq 最后一次消息客户端ID
     * @param limit           数量
     * @return list
     */
    public List<MSMsg> getWithContentType(int type, long oldestClientSeq, int limit) {
        return MsgDbManager.getInstance().queryWithContentType(type, oldestClientSeq, limit);
    }

    public void saveAndUpdateConversationMsg(MSMsg msg) {
        int refreshType = 0;
        if (!TextUtils.isEmpty(msg.clientMsgNO)) {
            MSMsg tempMsg = MsgDbManager.getInstance().queryWithClientMsgNo(msg.clientMsgNO);
            if (tempMsg != null) {
                refreshType = 1;
            }
        }
        if (msg.orderSeq == 0) {
            long tempOrderSeq = getMessageOrderSeq(0, msg.channelID, msg.channelType);
            msg.orderSeq = tempOrderSeq + 1;
        }
        MsgDbManager.getInstance().insert(msg);
        if (refreshType == 0)
            pushNewMsg(msg);
        else setRefreshMsg(msg, true);
        ConversationDbManager.getInstance().insertOrUpdateWithMsg(msg, 0);
    }


    public long getMsgExtraMaxVersionWithChannel(String channelID, byte channelType) {
        return MsgDbManager.getInstance().queryMsgExtraMaxVersionWithChannel(channelID, channelType);
    }

    public MSMsg getWithClientMsgNO(String clientMsgNo) {
        return MsgDbManager.getInstance().queryWithClientMsgNo(clientMsgNo);
    }


    public void saveRemoteExtraMsg(MSChannel channel, List<MSSyncExtraMsg> list) {
        if (MSCommonUtils.isEmpty(list)) return;
        List<MSMsgExtra> extraList = new ArrayList<>();
        List<String> messageIds = new ArrayList<>();
        List<String> deleteMsgIds = new ArrayList<>();
        for (int i = 0, size = list.size(); i < size; i++) {
            if (TextUtils.isEmpty(list.get(i).message_id)) {
                continue;
            }
            MSMsgExtra extra = MSSyncExtraMsg2MSMsgExtra(channel.channelID, channel.channelType, list.get(i));
            extraList.add(extra);
            messageIds.add(list.get(i).message_id);
            if (extra.isMutualDeleted == 1) {
                deleteMsgIds.add(list.get(i).message_id);
            }
        }
        List<MSMsg> updatedMsgList = MsgDbManager.getInstance().insertOrReplaceExtra(extraList);
        if (!deleteMsgIds.isEmpty()) {
            boolean isSuccess = MsgDbManager.getInstance().deleteWithMessageIDs(deleteMsgIds);
            if (!isSuccess) {
                MSLoggerUtils.getInstance().e(TAG, "saveRemoteExtraMsg delete message error");
            }
            String deletedMsgId = "";
            MSConversationMsg conversationMsg = ConversationDbManager.getInstance().queryWithChannel(channel.channelID, channel.channelType);
            if (conversationMsg != null && !TextUtils.isEmpty(conversationMsg.lastClientMsgNO)) {
                MSMsg msg = getWithClientMsgNO(conversationMsg.lastClientMsgNO);
                if (msg != null && !TextUtils.isEmpty(msg.messageID) && msg.messageSeq != 0) {
                    for (String msgId : deleteMsgIds) {
                        if (msg.messageID.equals(msgId)) {
                            deletedMsgId = msgId;
                            break;
                        }
                    }
                }
            }
            if (!TextUtils.isEmpty(deletedMsgId) && conversationMsg != null) {
                int rowNo = MSIM.getInstance().getMsgManager().getRowNoWithMessageID(channel.channelID, channel.channelType, deletedMsgId);
                if (rowNo < conversationMsg.unreadCount) {
                    conversationMsg.unreadCount--;
                }
                MSIM.getInstance().getConversationManager().updateWithMsg(conversationMsg);
                MSUIConversationMsg msuiConversationMsg = MSIM.getInstance().getConversationManager().getUIConversationMsg(channel.channelID, channel.channelType);
                MSIM.getInstance().getConversationManager().setOnRefreshMsg(msuiConversationMsg, TAG + " saveRemoteExtraMsg");
            }
        }
        getMsgReactionsAndRefreshMsg(messageIds, updatedMsgList);
    }

    public void addOnSyncOfflineMsgListener(ISyncOfflineMsgListener iOfflineMsgListener) {
        this.iOfflineMsgListener = iOfflineMsgListener;
    }

    //添加删除消息监听
    public void addOnDeleteMsgListener(String key, IDeleteMsgListener iDeleteMsgListener) {
        if (iDeleteMsgListener == null || TextUtils.isEmpty(key)) return;
        if (deleteMsgListenerMap == null) deleteMsgListenerMap = new ConcurrentHashMap<>();
        deleteMsgListenerMap.put(key, iDeleteMsgListener);
    }

    public void removeDeleteMsgListener(String key) {
        if (TextUtils.isEmpty(key)) return;
        if (deleteMsgListenerMap != null) deleteMsgListenerMap.remove(key);
    }

    //设置删除消息
    public void setDeleteMsg(MSMsg msg) {
        if (deleteMsgListenerMap != null && !deleteMsgListenerMap.isEmpty()) {
            runOnMainThread(() -> {
                for (Map.Entry<String, IDeleteMsgListener> entry : deleteMsgListenerMap.entrySet()) {
                    entry.getValue().onDeleteMsg(msg);
                }
            });
        }
    }


    void saveMsgReactions(List<MSMsgReaction> list) {
        MsgDbManager.getInstance().insertMsgReactions(list);
    }


    public void setSyncOfflineMsg(ISyncOfflineMsgBack iSyncOfflineMsgBack) {
        syncOfflineMsg(iSyncOfflineMsgBack);
    }

    private void syncOfflineMsg(ISyncOfflineMsgBack iSyncOfflineMsgBack) {
        if (iOfflineMsgListener != null) {
            runOnMainThread(() -> {
                long max_message_seq = getMaxMessageSeq();
                iOfflineMsgListener.getOfflineMsgs(max_message_seq, (isEnd, list) -> {
                    //保存同步消息
                    saveSyncMsg(list);
                    if (isEnd) {
                        iSyncOfflineMsgBack.onBack(isEnd, null);
                    } else {
                        syncOfflineMsg(iSyncOfflineMsgBack);
                    }
                });
            });
        } else iSyncOfflineMsgBack.onBack(true, null);
    }


    public void setSendMsgCallback(MSMsg msg) {
        if (sendMsgCallBackListenerHashMap != null && !sendMsgCallBackListenerHashMap.isEmpty()) {
            runOnMainThread(() -> {
                for (Map.Entry<String, ISendMsgCallBackListener> entry : sendMsgCallBackListenerHashMap.entrySet()) {
                    entry.getValue().onInsertMsg(msg);
                }
            });
        }
    }

    public void addOnSendMsgCallback(String key, ISendMsgCallBackListener iSendMsgCallBackListener) {
        if (TextUtils.isEmpty(key)) return;
        if (sendMsgCallBackListenerHashMap == null) {
            sendMsgCallBackListenerHashMap = new ConcurrentHashMap<>();
        }
        sendMsgCallBackListenerHashMap.put(key, iSendMsgCallBackListener);
    }

    public void removeSendMsgCallBack(String key) {
        if (sendMsgCallBackListenerHashMap != null) {
            sendMsgCallBackListenerHashMap.remove(key);
        }
    }


    //监听同步频道消息
    public void addOnSyncChannelMsgListener(ISyncChannelMsgListener listener) {
        this.iSyncChannelMsgListener = listener;
    }

    public void setSyncChannelMsgListener(String channelID, byte channelType, long startMessageSeq, long endMessageSeq, int limit, int pullMode, ISyncChannelMsgBack iSyncChannelMsgBack) {
        if (this.iSyncChannelMsgListener != null) {
            runOnMainThread(() -> iSyncChannelMsgListener.syncChannelMsgs(channelID, channelType, startMessageSeq, endMessageSeq, limit, pullMode, syncChannelMsg -> {
                if (syncChannelMsg != null && MSCommonUtils.isNotEmpty(syncChannelMsg.messages)) {
                    saveSyncChannelMSGs(syncChannelMsg.messages);
                }
                iSyncChannelMsgBack.onBack(syncChannelMsg);
            }));
        }
    }

    public void saveSyncChannelMSGs(List<MSSyncRecent> list) {
        if (MSCommonUtils.isEmpty(list)) return;
        List<MSMsg> msgList = new ArrayList<>();
        List<MSMsgExtra> msgExtraList = new ArrayList<>();
        List<MSMsgReaction> reactionList = new ArrayList<>();
        List<String> msgIds = new ArrayList<>();
        for (int j = 0, len = list.size(); j < len; j++) {
            MSMsg msMsg = MSSyncRecent2MSMsg(list.get(j));
            if (msMsg.type == MSMsgContentType.MS_INSIDE_MSG) {
                continue;
            }
            msgList.add(msMsg);
            if (!TextUtils.isEmpty(msMsg.messageID)) {
                msgIds.add(msMsg.messageID);
            }
            if (list.get(j).message_extra != null) {
                MSMsgExtra extra = MSSyncExtraMsg2MSMsgExtra(msMsg.channelID, msMsg.channelType, list.get(j).message_extra);
                msgExtraList.add(extra);
            }
            if (MSCommonUtils.isNotEmpty(msMsg.reactionList)) {
                reactionList.addAll(msMsg.reactionList);
            }
        }
        if (MSCommonUtils.isNotEmpty(msgExtraList)) {
            MsgDbManager.getInstance().insertOrReplaceExtra(msgExtraList);
        }
        if (MSCommonUtils.isNotEmpty(msgList)) {
            MsgDbManager.getInstance().insertMsgs(msgList);
        }
        if (MSCommonUtils.isNotEmpty(reactionList)) {
            MsgDbManager.getInstance().insertMsgReactions(reactionList);
        }
        List<MSMsg> saveList = MsgDbManager.getInstance().queryWithMsgIds(msgIds);
        getMsgReactionsAndRefreshMsg(msgIds, saveList);
    }

    public void addOnSendMsgAckListener(String key, ISendACK iSendACKListener) {
        if (iSendACKListener == null || TextUtils.isEmpty(key)) return;
        if (sendAckListenerMap == null) sendAckListenerMap = new ConcurrentHashMap<>();
        sendAckListenerMap.put(key, iSendACKListener);
    }

    public void setSendMsgAck(MSMsg msg) {
        if (sendAckListenerMap != null && !sendAckListenerMap.isEmpty()) {
            runOnMainThread(() -> {
                for (Map.Entry<String, ISendACK> entry : sendAckListenerMap.entrySet()) {
                    entry.getValue().msgACK(msg);
                }
            });

        }
    }

    public void removeSendMsgAckListener(String key) {
        if (!TextUtils.isEmpty(key) && sendAckListenerMap != null) {
            sendAckListenerMap.remove(key);
        }
    }

    public void addOnUploadAttachListener(IUploadAttachmentListener iUploadAttachmentListener) {
        this.iUploadAttachmentListener = iUploadAttachmentListener;
    }

    public void setUploadAttachment(MSMsg msg, IUploadAttacResultListener resultListener) {
        if (iUploadAttachmentListener != null) {
            runOnMainThread(() -> {
                iUploadAttachmentListener.onUploadAttachmentListener(msg, resultListener);
            });
        }
    }

    public void addMessageStoreBeforeIntercept(IMessageStoreBeforeIntercept iMessageStoreBeforeInterceptListener) {
        messageStoreBeforeIntercept = iMessageStoreBeforeInterceptListener;
    }

    public boolean setMessageStoreBeforeIntercept(MSMsg msg) {
        return messageStoreBeforeIntercept == null || messageStoreBeforeIntercept.isSaveMsg(msg);
    }

    //添加消息修改
    public void addOnRefreshMsgListener(String key, IRefreshMsg listener) {
        if (TextUtils.isEmpty(key) || listener == null) return;
        if (refreshMsgListenerMap == null) refreshMsgListenerMap = new ConcurrentHashMap<>();
        refreshMsgListenerMap.put(key, listener);
    }


    public void removeRefreshMsgListener(String key) {
        if (!TextUtils.isEmpty(key) && refreshMsgListenerMap != null) {
            refreshMsgListenerMap.remove(key);
        }
    }

    public void setRefreshMsg(MSMsg msg, boolean left) {
        if (refreshMsgListenerMap != null && !refreshMsgListenerMap.isEmpty()) {
            runOnMainThread(() -> {
                for (Map.Entry<String, IRefreshMsg> entry : refreshMsgListenerMap.entrySet()) {
                    entry.getValue().onRefresh(msg, left);
                }
            });

        }
    }

    public void addOnNewMsgListener(String key, INewMsgListener iNewMsgListener) {
        if (TextUtils.isEmpty(key) || iNewMsgListener == null) return;
        if (newMsgListenerMap == null)
            newMsgListenerMap = new ConcurrentHashMap<>();
        newMsgListenerMap.put(key, iNewMsgListener);
    }

    public void removeNewMsgListener(String key) {
        if (TextUtils.isEmpty(key)) return;
        if (newMsgListenerMap != null) newMsgListenerMap.remove(key);
    }

    public void addOnClearMsgListener(String key, IClearMsgListener iClearMsgListener) {
        if (TextUtils.isEmpty(key) || iClearMsgListener == null) return;
        if (clearMsgMap == null) clearMsgMap = new ConcurrentHashMap<>();
        clearMsgMap.put(key, iClearMsgListener);
    }

    public void removeClearMsg(String key) {
        if (TextUtils.isEmpty(key)) return;
        if (clearMsgMap != null) clearMsgMap.remove(key);
    }


    MSMsgExtra MSSyncExtraMsg2MSMsgExtra(String channelID, byte channelType, MSSyncExtraMsg extraMsg) {
        MSMsgExtra extra = new MSMsgExtra();
        extra.channelID = channelID;
        extra.channelType = channelType;
        extra.unreadCount = extraMsg.unread_count;
        extra.readedCount = extraMsg.readed_count;
        extra.readed = extraMsg.readed;
        extra.messageID = extraMsg.message_id;
        extra.isMutualDeleted = extraMsg.is_mutual_deleted;
        extra.isPinned = extraMsg.is_pinned;
        extra.extraVersion = extraMsg.extra_version;
        extra.revoke = extraMsg.revoke;
        extra.revoker = extraMsg.revoker;
        extra.needUpload = 0;
        if (extraMsg.content_edit != null) {
            JSONObject jsonObject = new JSONObject(extraMsg.content_edit);
            extra.contentEdit = jsonObject.toString();
        }

        extra.editedAt = extraMsg.edited_at;
        return extra;
    }

    MSMsg MSSyncRecent2MSMsg(MSSyncRecent msSyncRecent) {
        MSMsg msg = new MSMsg();
        msg.channelID = msSyncRecent.channel_id;
        msg.channelType = msSyncRecent.channel_type;
        msg.messageID = msSyncRecent.message_id;
        msg.messageSeq = msSyncRecent.message_seq;
        msg.clientMsgNO = msSyncRecent.client_msg_no;
        msg.fromUID = msSyncRecent.from_uid;
        msg.timestamp = msSyncRecent.timestamp;
        msg.orderSeq = msg.messageSeq * msOrderSeqFactor;
        msg.voiceStatus = msSyncRecent.voice_status;
        msg.isDeleted = msSyncRecent.is_deleted;
        msg.status = MSSendMsgResult.send_success;
        msg.remoteExtra = new MSMsgExtra();
        msg.remoteExtra.revoke = msSyncRecent.revoke;
        msg.remoteExtra.revoker = msSyncRecent.revoker;
        msg.remoteExtra.unreadCount = msSyncRecent.unread_count;
        msg.remoteExtra.readedCount = msSyncRecent.readed_count;
        msg.remoteExtra.readed = msSyncRecent.readed;
        msg.expireTime = msSyncRecent.expire;
        msg.expireTimestamp = msg.expireTime + msg.timestamp;
        // msg.reactionList = msSyncRecent.reactions;
        // msg.receipt = msSyncRecent.receipt;
        msg.remoteExtra.extraVersion = msSyncRecent.extra_version;
        //处理消息设置
        byte[] setting = MSTypeUtils.getInstance().intToByte(msSyncRecent.setting);
        msg.setting = MSTypeUtils.getInstance().getMsgSetting(setting[0]);
        //如果是单聊先将channelId改成发送者ID
        if (!TextUtils.isEmpty(msg.channelID)
                && !TextUtils.isEmpty(msg.fromUID)
                && msg.channelType == MSChannelType.PERSONAL
                && msg.channelID.equals(MSIMApplication.getInstance().getUid())) {
            msg.channelID = msg.fromUID;
        }

        if (msSyncRecent.payload != null) {
            JSONObject jsonObject = new JSONObject(msSyncRecent.payload);
            msg.content = jsonObject.toString();
        }
        // 处理消息回应
        if (MSCommonUtils.isNotEmpty(msSyncRecent.reactions)) {
            msg.reactionList = getMsgReaction(msSyncRecent);
        }
        msg = MessageHandler.getInstance().parsingMsg(msg);
        return msg;
    }

    private List<MSMsgReaction> getMsgReaction(MSSyncRecent msSyncRecent) {
        List<MSMsgReaction> list = new ArrayList<>();
        for (int i = 0, size = msSyncRecent.reactions.size(); i < size; i++) {
            MSMsgReaction reaction = new MSMsgReaction();
            reaction.channelID = msSyncRecent.reactions.get(i).channel_id;
            reaction.channelType = msSyncRecent.reactions.get(i).channel_type;
            reaction.uid = msSyncRecent.reactions.get(i).uid;
            reaction.name = msSyncRecent.reactions.get(i).name;
            reaction.emoji = msSyncRecent.reactions.get(i).emoji;
            reaction.seq = msSyncRecent.reactions.get(i).seq;
            reaction.isDeleted = msSyncRecent.reactions.get(i).is_deleted;
            reaction.messageID = msSyncRecent.reactions.get(i).message_id;
            reaction.createdAt = msSyncRecent.reactions.get(i).created_at;
            list.add(reaction);
        }
        return list;
    }

    public void saveSyncMsg(List<MSSyncMsg> msSyncMsgs) {
        if (MSCommonUtils.isEmpty(msSyncMsgs)) return;
        for (int i = 0, size = msSyncMsgs.size(); i < size; i++) {
            msSyncMsgs.get(i).msMsg = MessageHandler.getInstance().parsingMsg(msSyncMsgs.get(i).msMsg);
            if (msSyncMsgs.get(i).msMsg.timestamp != 0)
                msSyncMsgs.get(i).msMsg.orderSeq = msSyncMsgs.get(i).msMsg.timestamp;
            else
                msSyncMsgs.get(i).msMsg.orderSeq = getMessageOrderSeq(msSyncMsgs.get(i).msMsg.messageSeq, msSyncMsgs.get(i).msMsg.channelID, msSyncMsgs.get(i).msMsg.channelType);
        }
        MessageHandler.getInstance().saveSyncMsg(msSyncMsgs);
    }


    public void updateMsgEdit(String msgID, String channelID, byte channelType, String content) {
        MSMsgExtra msMsgExtra = MsgDbManager.getInstance().queryMsgExtraWithMsgID(msgID);
        if (msMsgExtra == null) {
            msMsgExtra = new MSMsgExtra();
        }
        msMsgExtra.messageID = msgID;
        msMsgExtra.channelID = channelID;
        msMsgExtra.channelType = channelType;
        msMsgExtra.editedAt = DateUtils.getInstance().getCurrentSeconds();
        msMsgExtra.contentEdit = content;
        msMsgExtra.needUpload = 1;
        List<MSMsgExtra> list = new ArrayList<>();
        list.add(msMsgExtra);
        List<MSMsg> msMsgList = MsgDbManager.getInstance().insertOrReplaceExtra(list);
        List<String> messageIds = new ArrayList<>();
        messageIds.add(msgID);
        if (MSCommonUtils.isNotEmpty(msMsgList)) {
            getMsgReactionsAndRefreshMsg(messageIds, msMsgList);
            setUploadMsgExtra(msMsgExtra);
        }
    }

    private synchronized void startCheckTimer() {
        if (checkMsgNeedUploadTimer == null) {
            checkMsgNeedUploadTimer = new Timer();
        }
        checkMsgNeedUploadTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                List<MSMsgExtra> list = MsgDbManager.getInstance().queryMsgExtraWithNeedUpload(1);
                if (MSCommonUtils.isNotEmpty(list)) {
                    for (MSMsgExtra extra : list) {
                        if (iUploadMsgExtraListener != null) {
                            iUploadMsgExtraListener.onUpload(extra);
                        }
                    }
                } else {
                    checkMsgNeedUploadTimer.cancel();
                    checkMsgNeedUploadTimer.purge();
                    checkMsgNeedUploadTimer = null;
                }
            }
        }, 1000 * 5, 1000 * 5);
    }

    private void setUploadMsgExtra(MSMsgExtra extra) {
        if (iUploadMsgExtraListener != null) {
            iUploadMsgExtraListener.onUpload(extra);
        }
        startCheckTimer();
    }

    public void addOnUploadMsgExtraListener(IUploadMsgExtraListener iUploadMsgExtraListener) {
        this.iUploadMsgExtraListener = iUploadMsgExtraListener;
    }

    public void pushNewMsg(List<MSMsg> msMsgList) {
        if (newMsgListenerMap != null && !newMsgListenerMap.isEmpty()) {
            runOnMainThread(() -> {
                for (Map.Entry<String, INewMsgListener> entry : newMsgListenerMap.entrySet()) {
                    entry.getValue().newMsg(msMsgList);
                }
            });
        }
    }

    /**
     * push新消息
     *
     * @param msg 消息
     */
    public void pushNewMsg(MSMsg msg) {
        if (msg == null) return;
        List<MSMsg> msgs = new ArrayList<>();
        msgs.add(msg);
        pushNewMsg(msgs);
    }

    /**
     * Deprecated 后续版本将会移除
     *
     * @param messageContent 消息体
     * @param channelID      频道ID
     * @param channelType    频道类型
     */
    @Deprecated
    public void sendMessage(MSMessageContent messageContent, String channelID, byte channelType) {
        send(messageContent, new MSChannel(channelID, channelType));
    }

    /**
     * Deprecated 后续版本将会移除
     *
     * @param messageContent 消息体
     * @param setting        消息设置
     * @param channelID      频道ID
     * @param channelType    频道类型
     */
    @Deprecated
    public void sendMessage(MSMessageContent messageContent, MSMsgSetting setting, String channelID, byte channelType) {
        MSSendOptions options = new MSSendOptions();
        options.setting = setting;
        MSChannel channel = MSIM.getInstance().getChannelManager().getChannel(channelID, channelType);
        if (channel == null) {
            channel = new MSChannel(channelID, channelType);
        }
        sendWithOptions(messageContent, channel, options);
    }

    /**
     * 发送消息
     *
     * @param msg 消息对象
     */
    public void sendMessage(@NonNull MSMsg msg) {
        MSConnection.getInstance().sendMessage(msg);
    }

    /**
     * 发送消息
     *
     * @param contentModel 消息体
     * @param channel      频道
     */
    public void send(@NonNull MSMessageContent contentModel, @NonNull MSChannel channel) {
        sendWithOptions(contentModel, channel, new MSSendOptions());
    }

    /**
     * 发送消息
     *
     * @param contentModel 消息体
     * @param channel      频道
     * @param options      高级设置
     */
    public void sendWithOptions(@NonNull MSMessageContent contentModel, @NonNull MSChannel channel, @NonNull MSSendOptions options) {
        final MSMsg msMsg = new MSMsg();
        msMsg.type = contentModel.type;
        msMsg.channelID = channel.channelID;
        msMsg.channelType = channel.channelType;
        msMsg.baseContentMsgModel = contentModel;
        msMsg.flame = options.flame;
        msMsg.flameSecond = options.flameSecond;
        msMsg.expireTime = options.expire;
        if (!TextUtils.isEmpty(options.topicID)) {
            msMsg.topicID = options.topicID;
        }
        if (!TextUtils.isEmpty(options.robotID)) {
            msMsg.robotID = options.robotID;
        }
        msMsg.setting = options.setting;
        msMsg.header = options.header;
        sendMessage(msMsg);
    }

    public String createClientMsgNO() {
        String deviceId = MSIM.getInstance().getDeviceID();
        if (TextUtils.isEmpty(deviceId)) {
            deviceId = "unknown";
        }
        String uuid = UUID.randomUUID().toString().replaceAll("-", "");
        return uuid + "_" + deviceId + "_1";
    }
}
