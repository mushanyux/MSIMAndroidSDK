package com.mushanyux.mushanim.db;

import static com.mushanyux.mushanim.db.MSDBColumns.TABLE.channel;
import static com.mushanyux.mushanim.db.MSDBColumns.TABLE.message;
import static com.mushanyux.mushanim.db.MSDBColumns.TABLE.messageExtra;

import android.content.ContentValues;
import android.database.Cursor;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import com.mushanyux.mushanim.MSIM;
import com.mushanyux.mushanim.MSIMApplication;
import com.mushanyux.mushanim.entity.MSChannel;
import com.mushanyux.mushanim.entity.MSChannelMember;
import com.mushanyux.mushanim.entity.MSChannelType;
import com.mushanyux.mushanim.entity.MSMessageGroupByDate;
import com.mushanyux.mushanim.entity.MSMessageSearchResult;
import com.mushanyux.mushanim.entity.MSMsg;
import com.mushanyux.mushanim.entity.MSMsgExtra;
import com.mushanyux.mushanim.entity.MSMsgReaction;
import com.mushanyux.mushanim.entity.MSMsgSetting;
import com.mushanyux.mushanim.entity.MSSyncChannelMsg;
import com.mushanyux.mushanim.entity.MSSyncRecent;
import com.mushanyux.mushanim.interfaces.IGetOrSyncHistoryMsgBack;
import com.mushanyux.mushanim.manager.MsgManager;
import com.mushanyux.mushanim.message.type.MSSendMsgResult;
import com.mushanyux.mushanim.msgmodel.MSFormatErrorContent;
import com.mushanyux.mushanim.msgmodel.MSMessageContent;
import com.mushanyux.mushanim.utils.MSCommonUtils;
import com.mushanyux.mushanim.utils.MSLoggerUtils;
import com.mushanyux.mushanim.utils.MSTypeUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

public class MsgDbManager {
    private final String TAG = "MsgDbManager";
    private final String extraCols = "IFNULL(" + messageExtra + ".readed,0) as readed,IFNULL(" + messageExtra + ".readed_count,0) as readed_count,IFNULL(" + messageExtra + ".unread_count,0) as unread_count,IFNULL(" + messageExtra + ".revoke,0) as revoke,IFNULL(" + messageExtra + ".revoker,'') as revoker,IFNULL(" + messageExtra + ".extra_version,0) as extra_version,IFNULL(" + messageExtra + ".is_mutual_deleted,0) as is_mutual_deleted,IFNULL(" + messageExtra + ".content_edit,'') as content_edit,IFNULL(" + messageExtra + ".edited_at,0) as edited_at,IFNULL(" + messageExtra + ".is_pinned,0) as is_pinned";
    private final String messageCols = message + ".client_seq," + message + ".message_id," + message + ".message_seq," + message + ".channel_id," + message + ".channel_type," + message + ".timestamp," + message + ".from_uid," + message + ".type," + message + ".content," + message + ".status," + message + ".voice_status," + message + ".created_at," + message + ".updated_at," + message + ".searchable_word," + message + ".client_msg_no," + message + ".setting," + message + ".order_seq," + message + ".extra," + message + ".is_deleted," + message + ".flame," + message + ".flame_second," + message + ".viewed," + message + ".viewed_at," + message + ".expire_time," + message + ".expire_timestamp";

    private MsgDbManager() {
    }

    private static class MsgDbManagerBinder {
        static final MsgDbManager db = new MsgDbManager();
    }

    public static MsgDbManager getInstance() {
        return MsgDbManagerBinder.db;
    }

    private int requestCount;
    //    private int more = 1;
    private final HashMap<String, Long> channelMinMsgSeqs = new HashMap<>();

    public void queryOrSyncHistoryMessages(String channelId, byte channelType, long oldestOrderSeq, boolean contain, int pullMode, int limit, final IGetOrSyncHistoryMsgBack iGetOrSyncHistoryMsgBack) {
        //获取原始数据
        List<MSMsg> list = queryMessages(channelId, channelType, oldestOrderSeq, contain, pullMode, limit);
        //业务判断数据
        List<MSMsg> tempList = new ArrayList<>();
        for (int i = 0, size = list.size(); i < size; i++) {
            tempList.add(list.get(i));
        }

        //先通过message_seq排序
        if (!tempList.isEmpty())
            Collections.sort(tempList, (o1, o2) -> (o1.messageSeq - o2.messageSeq));
        //获取最大和最小messageSeq
        long minMessageSeq = 0;
        long maxMessageSeq = 0;
        for (int i = 0, size = tempList.size(); i < size; i++) {
            if (tempList.get(i).messageSeq != 0) {
                if (minMessageSeq == 0) minMessageSeq = tempList.get(i).messageSeq;
                if (tempList.get(i).messageSeq > maxMessageSeq)
                    maxMessageSeq = tempList.get(i).messageSeq;
                if (tempList.get(i).messageSeq < minMessageSeq)
                    minMessageSeq = tempList.get(i).messageSeq;
            }
        }
        //是否同步消息
        boolean isSyncMsg = false;
        long startMsgSeq = 0;
        long endMsgSeq = 0;
        //判断页与页之间是否连续
        long oldestMsgSeq;
        //如果获取到的messageSeq为0说明oldestOrderSeq这条消息是本地消息则获取他上一条或下一条消息的messageSeq做为判断
        if (oldestOrderSeq % 1000 != 0)
            oldestMsgSeq = queryMsgSeq(channelId, channelType, oldestOrderSeq, pullMode);
        else oldestMsgSeq = oldestOrderSeq / 1000;
        if (pullMode == 0) {
            //下拉获取消息 大->小
            if (maxMessageSeq != 0 && oldestMsgSeq != 0 && oldestMsgSeq - maxMessageSeq > 1) {
                isSyncMsg = true;
                if (contain) {
                    startMsgSeq = oldestMsgSeq;
                } else {
                    startMsgSeq = oldestMsgSeq - 1;
                }
                endMsgSeq = maxMessageSeq;
            }
        } else {
            //上拉获取消息 小->大
            if (minMessageSeq != 0 && oldestMsgSeq != 0 && minMessageSeq - oldestMsgSeq > 1) {
                isSyncMsg = true;
                if (contain) {
                    startMsgSeq = oldestMsgSeq;
                } else {
                    startMsgSeq = oldestMsgSeq + 1;
                }
                endMsgSeq = minMessageSeq;
            }
        }
        if (!isSyncMsg) {
            //判断当前页是否连续
            for (int i = 0, size = tempList.size(); i < size; i++) {
                int nextIndex = i + 1;
                if (nextIndex < tempList.size()) {
                    if (tempList.get(nextIndex).messageSeq != 0 && tempList.get(i).messageSeq != 0 &&
                            tempList.get(nextIndex).messageSeq - tempList.get(i).messageSeq > 1) {
                        //判断该条消息是否被删除
                        int num = getDeletedCount(tempList.get(i).messageSeq, tempList.get(nextIndex).messageSeq, channelId, channelType);
                        if (num < (tempList.get(nextIndex).messageSeq - tempList.get(i).messageSeq) - 1) {
                            isSyncMsg = true;
                            long max = tempList.get(nextIndex).messageSeq;
                            long min = tempList.get(i).messageSeq;
                            if (tempList.get(nextIndex).messageSeq < tempList.get(i).messageSeq) {
                                max = tempList.get(i).messageSeq;
                                min = tempList.get(nextIndex).messageSeq;
                            }
                            if (pullMode == 0) {
                                // 下拉
                                if (max > startMsgSeq) {
                                    startMsgSeq = max;
                                }
                                if (endMsgSeq == 0 || min < endMsgSeq) {
                                    endMsgSeq = min;
                                }
                            } else {
                                if (startMsgSeq == 0 || min < startMsgSeq) {
                                    startMsgSeq = min;
                                }
                                if (max > endMsgSeq) {
                                    endMsgSeq = max;
                                }
                            }
                        }
                    }
                }
            }
        }
        if (oldestOrderSeq == 0) {
            isSyncMsg = true;
            startMsgSeq = 0;
            endMsgSeq = 0;
        }
        String key = channelId + "_" + channelType;
        if (!isSyncMsg) {
            long minSeq = 1;
            if (channelMinMsgSeqs.containsKey(key)) {
                Object s = channelMinMsgSeqs.get(key);
                if (s != null) {
                    minSeq = Long.parseLong(s.toString());
                }
            }
            if (minMessageSeq == minSeq) {
                requestCount = 0;
//                more = 1;
                new Handler(Looper.getMainLooper()).post(() -> iGetOrSyncHistoryMsgBack.onResult(list));
                return;
            }
        }
        //计算最后一页后是否还存在消息
        if (!isSyncMsg && tempList.size() < limit) {
            isSyncMsg = true;
            if (contain) {
                startMsgSeq = oldestMsgSeq;
            } else {
                if (pullMode == 0) {
                    startMsgSeq = oldestMsgSeq - 1;
                } else {
                    startMsgSeq = oldestMsgSeq + 1;
                }
            }
            endMsgSeq = 0;
        }
        if (startMsgSeq == 0 && endMsgSeq == 0 && tempList.size() < limit) {
            isSyncMsg = true;
            endMsgSeq = oldestMsgSeq;
            startMsgSeq = 0;
        }
        if (isSyncMsg && requestCount < 5) {
            if (requestCount == 0) {
                new Handler(Looper.getMainLooper()).post(iGetOrSyncHistoryMsgBack::onSyncing);
            }
            //同步消息
            requestCount++;
            MsgManager.getInstance().setSyncChannelMsgListener(channelId, channelType, startMsgSeq, endMsgSeq, limit, pullMode, syncChannelMsg -> {
                if (syncChannelMsg != null) {
                    if (oldestMsgSeq == 0 || (syncChannelMsg.messages != null && syncChannelMsg.messages.size() < limit)) {
                        requestCount = 5;
                    }
                    queryOrSyncHistoryMessages(channelId, channelType, oldestOrderSeq, contain, pullMode, limit, iGetOrSyncHistoryMsgBack);
                } else {
                    requestCount = 0;
//                    more = 1;
                    new Handler(Looper.getMainLooper()).post(() -> iGetOrSyncHistoryMsgBack.onResult(list));
                }
            });
        } else {
            requestCount = 0;
//            more = 1;
            new Handler(Looper.getMainLooper()).post(() -> iGetOrSyncHistoryMsgBack.onResult(list));
        }
    }

    private long getMinSeq(MSSyncChannelMsg syncChannelMsg, List<MSMsg> tempList) {
        long minSeq = 0;
        if (MSCommonUtils.isNotEmpty(syncChannelMsg.messages)) {
            for (MSSyncRecent recent : syncChannelMsg.messages) {
                if (minSeq == 0) {
                    minSeq = recent.message_seq;
                } else {
                    minSeq = Math.min(minSeq, recent.message_seq);
                }
            }
        } else {
            if (MSCommonUtils.isNotEmpty(tempList)) {
                for (MSMsg msg : tempList) {
                    if (minSeq == 0) {
                        minSeq = msg.messageSeq;
                    } else {
                        minSeq = Math.min(minSeq, msg.messageSeq);
                    }
                }
            }
        }
        return minSeq;
    }

    public List<MSMsg> queryWithFlame() {
        String sql = "select * from " + message + " where " + MSDBColumns.MSMessageColumns.flame + "=1 and " + MSDBColumns.MSMessageColumns.is_deleted + "=0";
        List<MSMsg> list = new ArrayList<>();
        try (Cursor cursor = MSIMApplication.getInstance().getDbHelper().rawQuery(sql)) {
            if (cursor == null) {
                return list;
            }
            for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                MSMsg extra = serializeMsg(cursor);
                list.add(extra);
            }
        }
        return list;
    }

    /**
     * 获取被删除的条数
     *
     * @param minMessageSeq 最大messageSeq
     * @param maxMessageSeq 最小messageSeq
     * @param channelID     频道ID
     * @param channelType   频道类型
     * @return 删除条数
     */
    private int getDeletedCount(long minMessageSeq, long maxMessageSeq, String channelID, byte channelType) {
        String sql = "select count(*) num from " + message + " where " + MSDBColumns.MSMessageColumns.channel_id + "=? and " + MSDBColumns.MSMessageColumns.channel_type + "=? and " + MSDBColumns.MSMessageColumns.message_seq + ">? and " + MSDBColumns.MSMessageColumns.message_seq + "<? and " + MSDBColumns.MSMessageColumns.is_deleted + "=1";
        Cursor cursor = null;
        int num = 0;
        try {
            cursor = MSIMApplication
                    .getInstance()
                    .getDbHelper().rawQuery(sql, new Object[]{channelID, channelType, minMessageSeq, maxMessageSeq});
            if (cursor == null) {
                return 0;
            }
            if (cursor.moveToLast()) {
                num = MSCursor.readInt(cursor, "num");
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return num;
    }

    private List<MSMsg> queryMessages(String channelId, byte channelType, long oldestOrderSeq, boolean contain, int pullMode, int limit) {
        List<MSMsg> msgList = new ArrayList<>();
        String sql;
        Object[] args;
        if (oldestOrderSeq <= 0) {
            sql = "SELECT * FROM (SELECT " + messageCols + "," + extraCols + " FROM " + message + " LEFT JOIN " + messageExtra + " on " + message + ".message_id=" + messageExtra + ".message_id WHERE " + message + ".channel_id=? and " + message + ".channel_type=? and " + message + ".type<>0 and " + message + ".type<>99) where is_deleted=0 and is_mutual_deleted=0 order by order_seq desc limit 0," + limit;
            args = new Object[2];
            args[0] = channelId;
            args[1] = channelType;
        } else {
            if (pullMode == 0) {
                if (contain) {
                    sql = "SELECT * FROM (SELECT " + messageCols + "," + extraCols + " FROM " + message + " LEFT JOIN " + messageExtra + " on " + message + ".message_id=" + messageExtra + ".message_id WHERE " + message + ".channel_id=? and " + message + ".channel_type=? and " + message + ".type<>0 and " + message + ".type<>99 AND " + message + ".order_seq<=?) where is_deleted=0 and is_mutual_deleted=0 order by order_seq desc limit 0," + limit;
                } else {
                    sql = "SELECT * FROM (SELECT " + messageCols + "," + extraCols + " FROM " + message + " LEFT JOIN " + messageExtra + " on " + message + ".message_id=" + messageExtra + ".message_id WHERE " + message + ".channel_id=? and " + message + ".channel_type=? and " + message + ".type<>0 and " + message + ".type<>99 AND " + message + ".order_seq<?) where is_deleted=0 and is_mutual_deleted=0 order by order_seq desc limit 0," + limit;
                }
            } else {
                if (contain) {
                    sql = "SELECT * FROM (SELECT " + messageCols + "," + extraCols + " FROM " + message + " LEFT JOIN " + messageExtra + " on " + message + ".message_id=" + messageExtra + ".message_id WHERE " + message + ".channel_id=? and " + message + ".channel_type=? and " + message + ".type<>0 and " + message + ".type<>99 AND " + message + ".order_seq>=?) where is_deleted=0 and is_mutual_deleted=0 order by order_seq asc limit 0," + limit;
                } else {
                    sql = "SELECT * FROM (SELECT " + messageCols + "," + extraCols + " FROM " + message + " LEFT JOIN " + messageExtra + " on " + message + ".message_id=" + messageExtra + ".message_id WHERE " + message + ".channel_id=? and " + message + ".channel_type=? and " + message + ".type<>0 and " + message + ".type<>99 AND " + message + ".order_seq>?) where is_deleted=0 and is_mutual_deleted=0 order by order_seq asc limit 0," + limit;
                }
            }
            args = new Object[3];
            args[0] = channelId;
            args[1] = channelType;
            args[2] = oldestOrderSeq;
        }
        Cursor cursor = null;
        List<String> messageIds = new ArrayList<>();
        List<String> replyMsgIds = new ArrayList<>();
        List<String> fromUIDs = new ArrayList<>();

        try {
            cursor = MSIMApplication.getInstance().getDbHelper().rawQuery(sql, args);
            if (cursor == null) {
                return msgList;
            }
            MSChannel msChannel = ChannelDBManager.getInstance().query(channelId, channelType);
            for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                MSMsg msMsg = serializeMsg(cursor);
                msMsg.setChannelInfo(msChannel);
                if (!TextUtils.isEmpty(msMsg.messageID)) {
                    messageIds.add(msMsg.messageID);
                }
                if (msMsg.baseContentMsgModel != null && msMsg.baseContentMsgModel.reply != null && !TextUtils.isEmpty(msMsg.baseContentMsgModel.reply.message_id)) {
                    replyMsgIds.add(msMsg.baseContentMsgModel.reply.message_id);
                }
                if (!TextUtils.isEmpty(msMsg.fromUID))
                    fromUIDs.add(msMsg.fromUID);
                if (pullMode == 0)
                    msgList.add(0, msMsg);
                else msgList.add(msMsg);
            }
        } finally {
            if (cursor != null) cursor.close();
        }
        //扩展消息
        List<MSMsgReaction> list = MsgReactionDBManager.getInstance().queryWithMessageIds(messageIds);
        if (list != null && !list.isEmpty()) {
            for (int i = 0, size = msgList.size(); i < size; i++) {
                for (int j = 0, len = list.size(); j < len; j++) {
                    if (list.get(j).messageID.equals(msgList.get(i).messageID)) {
                        if (msgList.get(i).reactionList == null)
                            msgList.get(i).reactionList = new ArrayList<>();
                        msgList.get(i).reactionList.add(list.get(j));
                    }
                }
            }
        }
        // 发送者成员信息
        if (channelType == MSChannelType.GROUP) {
            List<MSChannelMember> memberList = ChannelMembersDbManager.getInstance().queryWithUIDs(channelId, channelType, fromUIDs);
            if (memberList != null && !memberList.isEmpty()) {
                for (MSChannelMember member : memberList) {
                    for (int i = 0, size = msgList.size(); i < size; i++) {
                        if (!TextUtils.isEmpty(msgList.get(i).fromUID) && msgList.get(i).fromUID.equals(member.memberUID)) {
                            msgList.get(i).setMemberOfFrom(member);
                        }
                    }
                }
            }
        }
        //消息发送者信息
        List<MSChannel> msChannels = ChannelDBManager.getInstance().queryWithChannelIdsAndChannelType(fromUIDs, MSChannelType.PERSONAL);
        if (MSCommonUtils.isNotEmpty(msChannels)) {
            for (MSChannel msChannel : msChannels) {
                for (int i = 0, size = msgList.size(); i < size; i++) {
                    if (!TextUtils.isEmpty(msgList.get(i).fromUID) && msgList.get(i).fromUID.equals(msChannel.channelID)) {
                        msgList.get(i).setFrom(msChannel);
                    }
                }
            }
        }
        // 被回复消息的编辑
        if (!replyMsgIds.isEmpty()) {
            List<MSMsgExtra> msgExtraList = queryMsgExtrasWithMsgIds(replyMsgIds);
            if (!msgExtraList.isEmpty()) {
                for (MSMsgExtra extra : msgExtraList) {
                    for (int i = 0, size = msgList.size(); i < size; i++) {
                        if (msgList.get(i).baseContentMsgModel != null
                                && msgList.get(i).baseContentMsgModel.reply != null
                                && extra.messageID.equals(msgList.get(i).baseContentMsgModel.reply.message_id)) {
                            msgList.get(i).baseContentMsgModel.reply.revoke = extra.revoke;
                        }
                        if (!TextUtils.isEmpty(extra.contentEdit) && msgList.get(i).baseContentMsgModel != null
                                && msgList.get(i).baseContentMsgModel.reply != null
                                && !TextUtils.isEmpty(msgList.get(i).baseContentMsgModel.reply.message_id)
                                && extra.messageID.equals(msgList.get(i).baseContentMsgModel.reply.message_id)) {
                            msgList.get(i).baseContentMsgModel.reply.editAt = extra.editedAt;
                            msgList.get(i).baseContentMsgModel.reply.contentEdit = extra.contentEdit;
                            msgList.get(i).baseContentMsgModel.reply.contentEditMsgModel = MsgManager.getInstance().getMsgContentModel(extra.contentEdit);
                            break;
                        }
                    }
                }
            }
        }
        return msgList;
    }

    public List<MSMsg> queryAll() {
        String sql = "select * from " + message;

        List<MSMsg> msMsgs = new ArrayList<>();
        try (Cursor cursor = MSIMApplication.getInstance().getDbHelper().rawQuery(sql)) {
            if (cursor == null) {
                return msMsgs;
            }
            for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                MSMsg msg = serializeMsg(cursor);
                msMsgs.add(msg);
            }
        }
        return msMsgs;
    }

    public List<MSMsg> queryExpireMessages(long timestamp, int limit) {
        String sql = "SELECT * from " + message + " where is_deleted=0 and " + MSDBColumns.MSMessageColumns.expire_time + ">0 and " + MSDBColumns.MSMessageColumns.expire_timestamp + "<=? order by order_seq desc limit 0," + limit;
        List<MSMsg> list = new ArrayList<>();
        try (Cursor cursor = MSIMApplication.getInstance().getDbHelper().rawQuery(sql, new Object[]{timestamp})) {
            if (cursor == null) {
                return list;
            }
            for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                MSMsg msMsg = serializeMsg(cursor);
                list.add(msMsg);
            }
        }
        return list;
    }

    public List<MSMsg> queryWithFromUID(String channelID, byte channelType, String fromUID, long oldestOrderSeq, int limit) {
        String sql;
        Object[] args;
        if (oldestOrderSeq == 0) {
            args = new Object[3];
            args[0] = channelID;
            args[1] = channelType;
            args[2] = fromUID;
            sql = "SELECT * FROM (SELECT " + messageCols + "," + extraCols + " FROM " + message + " LEFT JOIN " + messageExtra + " on " + message + ".message_id=" + messageExtra + ".message_id WHERE " + message + ".channel_id=? and " + message + ".channel_type=? and from_uid=? and " + message + ".type<>0 and " + message + ".type<>99) where is_deleted=0 and revoke=0 order by order_seq desc limit 0," + limit;
        } else {
            args = new Object[4];
            args[0] = channelID;
            args[1] = channelType;
            args[2] = fromUID;
            args[3] = oldestOrderSeq;
            sql = "SELECT * FROM (SELECT " + messageCols + "," + extraCols + " FROM " + message + " LEFT JOIN " + messageExtra + " on " + message + ".message_id=" + messageExtra + ".message_id WHERE " + message + ".channel_id=? and " + message + ".channel_type=? and from_uid=? and " + message + ".type<>0 and " + message + ".type<>99 AND " + message + ".order_seq<?) where is_deleted=0 and revoke=0 order by order_seq desc limit 0," + limit;
        }
        List<MSMsg> msMsgList = new ArrayList<>();
        try (Cursor cursor = MSIMApplication.getInstance().getDbHelper().rawQuery(sql, args)) {
            if (cursor == null) {
                return msMsgList;
            }
            MSChannel msChannel = ChannelDBManager.getInstance().query(channelID, channelType);
            for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                MSMsg msMsg = serializeMsg(cursor);
                msMsg.setChannelInfo(msChannel);
                if (channelType == MSChannelType.GROUP) {
                    //查询群成员信息
                    MSChannelMember member = ChannelMembersDbManager.getInstance().query(channelID, MSChannelType.GROUP, msMsg.fromUID);
                    msMsg.setMemberOfFrom(member);
                }
                msMsgList.add(msMsg);
            }
        }
        return msMsgList;
    }

    public long queryOrderSeq(String channelID, byte channelType, long maxOrderSeq, int limit) {
        long minOrderSeq = 0;
        String sql = "select order_seq from " + message + " where " + MSDBColumns.MSMessageColumns.channel_id + "=? and " + MSDBColumns.MSMessageColumns.channel_type + "=? and type<>99 and order_seq <=? order by " + MSDBColumns.MSMessageColumns.order_seq + " desc limit " + limit;
        try (Cursor cursor = MSIMApplication.getInstance().getDbHelper().rawQuery(sql, new Object[]{channelID, channelType, maxOrderSeq})) {
            if (cursor == null) {
                return minOrderSeq;
            }
            for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                minOrderSeq = MSCursor.readLong(cursor, "order_seq");
            }
        }
        return minOrderSeq;
    }

    public long queryMaxOrderSeqWithChannel(String channelID, byte channelType) {
        long maxOrderSeq = 0;
        String sql = "select max(order_seq) order_seq from " + message + " where " + MSDBColumns.MSMessageColumns.channel_id + "=? and " + MSDBColumns.MSMessageColumns.channel_type + "=? and type<>99 and type<>0 and is_deleted=0";
        try {
            if (MSIMApplication.getInstance().getDbHelper() != null) {
                Cursor cursor = MSIMApplication
                        .getInstance()
                        .getDbHelper()
                        .rawQuery(sql, new Object[]{channelID, channelType});
                if (cursor != null) {
                    if (cursor.moveToFirst()) {
                        maxOrderSeq = MSCursor.readLong(cursor, "order_seq");
                    }
                    cursor.close();
                }
            }
        } catch (Exception ignored) {
        }
        return maxOrderSeq;
    }

    public synchronized MSMsg updateMsgSendStatus(long clientSeq, long messageSeq, String messageID, int sendStatus) {

        String[] updateKey = new String[4];
        String[] updateValue = new String[4];

        updateKey[0] = MSDBColumns.MSMessageColumns.status;
        updateValue[0] = String.valueOf(sendStatus);

        updateKey[1] = MSDBColumns.MSMessageColumns.message_id;
        updateValue[1] = String.valueOf(messageID);

        updateKey[2] = MSDBColumns.MSMessageColumns.message_seq;
        updateValue[2] = String.valueOf(messageSeq);

        MSMsg msg = queryWithClientSeq(clientSeq);

        updateKey[3] = MSDBColumns.MSMessageColumns.order_seq;
        if (msg != null)
            updateValue[3] = String.valueOf(MsgManager.getInstance().getMessageOrderSeq(messageSeq, msg.channelID, msg.channelType));
        else updateValue[3] = "0";

        String where = MSDBColumns.MSMessageColumns.client_seq + "=?";
        String[] whereValue = new String[1];
        whereValue[0] = String.valueOf(clientSeq);

        int row = MSIMApplication.getInstance().getDbHelper()
                .update(message, updateKey, updateValue, where, whereValue);
        if (row > 0 && msg != null) {
            msg.status = sendStatus;
            msg.messageID = messageID;
            msg.messageSeq = (int) messageSeq;
            msg.orderSeq = MsgManager.getInstance().getMessageOrderSeq(messageSeq, msg.channelID, msg.channelType);
            MSIM.getInstance().getMsgManager().setRefreshMsg(msg, true);
        }
        return msg;
    }

    public synchronized void insertMsgs(List<MSMsg> list) {
        if (MSCommonUtils.isEmpty(list)) return;
        if (list.size() == 1) {
            insert(list.get(0));
            return;
        }
        List<MSMsg> saveList = new ArrayList<>();
        for (int i = 0, size = list.size(); i < size; i++) {
            boolean isExist = false;
            for (int j = 0, len = saveList.size(); j < len; j++) {
                if (list.get(i).clientMsgNO.equals(saveList.get(j).clientMsgNO)) {
                    isExist = true;
                    break;
                }
            }
            if (isExist) {
                list.get(i).clientMsgNO = MSIM.getInstance().getMsgManager().createClientMsgNO();
                list.get(i).isDeleted = 1;
            }
            saveList.add(list.get(i));
        }
        List<String> clientMsgNos = new ArrayList<>();
//        List<String> msgIds = new ArrayList<>();
        List<MSMsg> existMsgList = new ArrayList<>();
//        List<MSMsg> msgIdExistMsgList = new ArrayList<>();
        for (int i = 0, size = saveList.size(); i < size; i++) {
            boolean isSave = MSIM.getInstance().getMsgManager().setMessageStoreBeforeIntercept(saveList.get(i));
            if (!isSave) {
                saveList.get(i).isDeleted = 1;
            }
            if (saveList.get(i).setting == null) {
                saveList.get(i).setting = new MSMsgSetting();
            }
//            if (msgIds.size() == 200) {
//                List<MSMsg> tempList = queryWithMsgIds(msgIds);
//                if (MSCommonUtils.isNotEmpty(tempList)) {
//                    msgIdExistMsgList.addAll(tempList);
//                }
//                msgIds.clear();
//            }
            if (clientMsgNos.size() == 200) {
                List<MSMsg> tempList = queryWithClientMsgNos(clientMsgNos);
                if (MSCommonUtils.isNotEmpty(tempList))
                    existMsgList.addAll(tempList);
                clientMsgNos.clear();
            }
//            if (!TextUtils.isEmpty(saveList.get(i).messageID)) {
//                msgIds.add(saveList.get(i).messageID);
//            }
            if (!TextUtils.isEmpty(saveList.get(i).clientMsgNO))
                clientMsgNos.add(saveList.get(i).clientMsgNO);
        }
//        if (MSCommonUtils.isNotEmpty(msgIds)) {
//            List<MSMsg> tempList = queryWithMsgIds(msgIds);
//            if (MSCommonUtils.isNotEmpty(tempList)) {
//                msgIdExistMsgList.addAll(tempList);
//            }
//            msgIds.clear();
//        }
        if (MSCommonUtils.isNotEmpty(clientMsgNos)) {
            List<MSMsg> tempList = queryWithClientMsgNos(clientMsgNos);
            if (MSCommonUtils.isNotEmpty(tempList)) {
                existMsgList.addAll(tempList);
            }
            clientMsgNos.clear();
        }
        List<MSMsg> insertMsgList = new ArrayList<>();
        for (MSMsg msg : saveList) {
            if (TextUtils.isEmpty(msg.clientMsgNO) || TextUtils.isEmpty(msg.messageID)) {
                continue;
            }
            boolean isAdd = true;
            for (MSMsg tempMsg : existMsgList) {
                if (tempMsg == null || TextUtils.isEmpty(tempMsg.clientMsgNO)) {
                    continue;
                }
                if (tempMsg.clientMsgNO.equals(msg.clientMsgNO)) {
                    if (msg.isDeleted == tempMsg.isDeleted && tempMsg.isDeleted == 1) {
                        isAdd = false;
                    }
                    msg.isDeleted = 1;
                    msg.clientMsgNO = MSIM.getInstance().getMsgManager().createClientMsgNO();
                    break;
                }
            }
//            if (isAdd) {
//                for (MSMsg tempMsg : msgIdExistMsgList) {
//                    if (tempMsg == null || TextUtils.isEmpty(tempMsg.messageID)) {
//                        continue;
//                    }
//                    if (msg.messageID.equals(tempMsg.messageID)) {
//                        msg.localExtraMap = tempMsg.localExtraMap;
////                        if (msg.isDeleted != tempMsg.isDeleted && msg.isDeleted == 1) {
////                            msg.clientMsgNO = MSIM.getInstance().getMsgManager().createClientMsgNO();
////                        } else {
////                            isAdd = false;
////                        }
//                        break;
//                    }
//                }
//            }
            if (isAdd) {
                insertMsgList.add(msg);
            }

        }
        //  insertMsgList(insertMsgList);
        List<ContentValues> cvList = new ArrayList<>();
        for (MSMsg msMsg : insertMsgList) {
            ContentValues cv = MSSqlContentValues.getContentValuesWithMsg(msMsg);
            cvList.add(cv);
        }
        try {
            MSIMApplication.getInstance().getDbHelper().getDb().beginTransaction();
            for (ContentValues cv : cvList) {
                MSIMApplication.getInstance().getDbHelper()
                        .insert(message, cv);
            }
            MSIMApplication.getInstance().getDbHelper().getDb().setTransactionSuccessful();
        } finally {
            MSIMApplication.getInstance().getDbHelper().getDb().endTransaction();
        }
    }

    public List<MSMsg> queryWithClientMsgNos(List<String> clientMsgNos) {
        List<MSMsg> msgs = new ArrayList<>();
        try (Cursor cursor = MSIMApplication.getInstance().getDbHelper().select(message, "client_msg_no in (" + MSCursor.getPlaceholders(clientMsgNos.size()) + ")", clientMsgNos.toArray(new String[0]), null)) {
            if (cursor == null) {
                return msgs;
            }
            for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                MSMsg msg = serializeMsg(cursor);
                msgs.add(msg);
            }
        }
        return msgs;
    }

    public synchronized long insert(MSMsg msg) {
        boolean isSave = MSIM.getInstance().getMsgManager().setMessageStoreBeforeIntercept(msg);
        if (!isSave) {
            msg.isDeleted = 1;
        }
        //客户端id存在表示该条消息已存过库
        if (msg.clientSeq != 0) {
            update(msg);
            return msg.clientSeq;
        }
        if (!TextUtils.isEmpty(msg.clientMsgNO)) {
            boolean isExist = isExist(msg.clientMsgNO);
            if (isExist) {
                msg.isDeleted = 1;
                msg.clientMsgNO = MSIM.getInstance().getMsgManager().createClientMsgNO();
            }
        }
        ContentValues cv = new ContentValues();
        try {
            cv = MSSqlContentValues.getContentValuesWithMsg(msg);
        } catch (Exception e) {
            MSLoggerUtils.getInstance().e(TAG, " insert msg error");
        }
        long result = -1;
        try {
            result = MSIMApplication.getInstance().getDbHelper()
                    .insert(message, cv);
        } catch (Exception ignored) {
        }

        return result;
    }

    public synchronized void updateViewedAt(int viewed, long viewedAt, String clientMsgNo) {
        String[] updateKey = new String[2];
        String[] updateValue = new String[2];
        updateKey[0] = MSDBColumns.MSMessageColumns.viewed;
        updateValue[0] = String.valueOf(viewed);
        updateKey[1] = MSDBColumns.MSMessageColumns.viewed_at;
        updateValue[1] = String.valueOf(viewedAt);
        String where = MSDBColumns.MSMessageColumns.client_msg_no + "=?";
        String[] whereValue = new String[1];
        whereValue[0] = clientMsgNo;
        MSIMApplication.getInstance().getDbHelper()
                .update(message, updateKey, updateValue, where, whereValue);
    }

    private synchronized void update(MSMsg msg) {
        String[] updateKey = new String[4];
        String[] updateValue = new String[4];
        updateKey[0] = MSDBColumns.MSMessageColumns.content;
        updateValue[0] = msg.content;

        updateKey[1] = MSDBColumns.MSMessageColumns.status;
        updateValue[1] = String.valueOf(msg.status);

        updateKey[2] = MSDBColumns.MSMessageColumns.message_id;
        updateValue[2] = msg.messageID;

        updateKey[3] = MSDBColumns.MSMessageColumns.extra;
        updateValue[3] = msg.getLocalMapExtraString();
        String where = MSDBColumns.MSMessageColumns.client_seq + "=?";
        String[] whereValue = new String[1];
        whereValue[0] = String.valueOf(msg.clientSeq);
        MSIMApplication.getInstance().getDbHelper()
                .update(message, updateKey, updateValue, where, whereValue);

    }

    public boolean isExist(String clientMsgNo) {
        boolean isExist = false;
        String sql = "select * from " + message + " where " + MSDBColumns.MSMessageColumns.client_msg_no + "=?";
        try (Cursor cursor = MSIMApplication
                .getInstance()
                .getDbHelper().rawQuery(sql, new Object[]{clientMsgNo})) {
            if (cursor != null && cursor.moveToLast()) {
                isExist = true;
            }
        }
        return isExist;
    }

    public MSMsg queryWithClientMsgNo(String clientMsgNo) {
        MSMsg msMsg = null;
        String sql = "select " + messageCols + "," + extraCols + " from " + message + " LEFT JOIN " + messageExtra + " ON " + message + ".message_id=" + messageExtra + ".message_id WHERE " + message + ".client_msg_no=?";
        try (Cursor cursor = MSIMApplication
                .getInstance()
                .getDbHelper().rawQuery(sql, new Object[]{clientMsgNo})) {
            if (cursor == null) {
                return null;
            }
            if (cursor.moveToLast()) {
                msMsg = serializeMsg(cursor);
            }
        }
        if (msMsg != null)
            msMsg.reactionList = MsgReactionDBManager.getInstance().queryWithMessageId(msMsg.messageID);
        return msMsg;
    }


    public MSMsg queryWithClientSeq(long clientSeq) {
        MSMsg msg = null;
        try (Cursor cursor = MSIMApplication
                .getInstance()
                .getDbHelper().select(message, "client_seq=?", new String[]{String.valueOf(clientSeq)}, null)) {
            if (cursor == null) {
                return null;
            }
            if (cursor.moveToLast()) {
                msg = serializeMsg(cursor);
            }
        }
        if (msg != null)
            msg.reactionList = MsgReactionDBManager.getInstance().queryWithMessageId(msg.messageID);
        return msg;
    }

    public MSMsg queryMaxOrderSeqMsgWithChannel(String channelID, byte channelType) {
        String sql = "select * from " + message + " where " + MSDBColumns.MSMessageColumns.channel_id + "=? and " + MSDBColumns.MSMessageColumns.channel_type + "=? and " + MSDBColumns.MSMessageColumns.is_deleted + "=0 and type<>0 and type<>99 order by " + MSDBColumns.MSMessageColumns.order_seq + " desc limit 1";
        Cursor cursor = null;
        MSMsg msg = null;
        try {
            cursor = MSIMApplication
                    .getInstance()
                    .getDbHelper().rawQuery(sql, new Object[]{channelID, channelType});
            if (cursor == null) {
                return null;
            }
            if (cursor.moveToLast()) {
                msg = serializeMsg(cursor);
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return msg;
    }

    /**
     * 删除消息
     *
     * @param client_seq 消息客户端编号
     */
    public synchronized boolean deleteWithClientSeq(long client_seq) {
        String[] updateKey = new String[1];
        String[] updateValue = new String[1];
        updateKey[0] = MSDBColumns.MSMessageColumns.is_deleted;
        updateValue[0] = "1";
        String where = MSDBColumns.MSMessageColumns.client_seq + "=?";
        String[] whereValue = new String[1];
        whereValue[0] = String.valueOf(client_seq);
        int row = MSIMApplication.getInstance().getDbHelper()
                .update(message, updateKey, updateValue, where, whereValue);
        if (row > 0) {
            MSMsg msg = queryWithClientSeq(client_seq);
            if (msg != null)
                MSIM.getInstance().getMsgManager().setDeleteMsg(msg);
        }
        return row > 0;
    }

    public int queryRowNoWithOrderSeq(String channelID, byte channelType, long orderSeq) {
        String sql = "select count(*) cn from " + message + " where channel_id=? and channel_type=? and " + MSDBColumns.MSMessageColumns.type + "<>0 and " + MSDBColumns.MSMessageColumns.type + "<>99 and " + MSDBColumns.MSMessageColumns.order_seq + ">? and " + MSDBColumns.MSMessageColumns.is_deleted + "=0 order by " + MSDBColumns.MSMessageColumns.order_seq + " desc";
        Cursor cursor = null;
        int rowNo = 0;
        try {
            cursor = MSIMApplication
                    .getInstance()
                    .getDbHelper().rawQuery(sql, new Object[]{channelID, channelType, orderSeq});
            if (cursor == null) {
                return 0;
            }
            if (cursor.moveToLast()) {
                rowNo = MSCursor.readInt(cursor, "cn");
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return rowNo;
    }

    public synchronized boolean deleteWithMessageID(String messageID) {
        String[] updateKey = new String[1];
        String[] updateValue = new String[1];
        updateKey[0] = MSDBColumns.MSMessageColumns.is_deleted;
        updateValue[0] = "1";
        String where = MSDBColumns.MSMessageColumns.message_id + "=?";
        String[] whereValue = new String[1];
        whereValue[0] = messageID;
        int row = MSIMApplication.getInstance().getDbHelper()
                .update(message, updateKey, updateValue, where, whereValue);
        if (row > 0) {
            MSMsg msg = queryWithMessageID(messageID, false);
            if (msg != null)
                MSIM.getInstance().getMsgManager().setDeleteMsg(msg);
        }
        return row > 0;

    }

    public synchronized boolean deleteWithMessageIDs(List<String> messageIDs) {
        String[] updateKey = new String[1];
        String[] updateValue = new String[1];
        updateKey[0] = MSDBColumns.MSMessageColumns.is_deleted;
        updateValue[0] = "1";
        String where = MSDBColumns.MSMessageColumns.message_id + " in (" + MSCursor.getPlaceholders(messageIDs.size()) + ")";
        String[] whereValue = messageIDs.toArray(new String[0]);
        int row = MSIMApplication.getInstance().getDbHelper()
                .update(message, updateKey, updateValue, where, whereValue);
//        if (row > 0) {
//            List<MSMsg> msgList = queryWithMsgIds(messageIDs);
//            if (msgList.size() > 0) {
//                for (MSMsg msg : msgList) {
//                    MSIM.getInstance().getMsgManager().setDeleteMsg(msg);
//                }
//            }
//        }
        return row > 0;

    }

    public List<MSMsgExtra> queryMsgExtrasWithMsgIds(List<String> msgIds) {
        List<MSMsgExtra> list = new ArrayList<>();
        try (Cursor cursor = MSIMApplication.getInstance().getDbHelper().select(messageExtra, "message_id in (" + MSCursor.getPlaceholders(msgIds.size()) + ")", msgIds.toArray(new String[0]), null)) {
            if (cursor == null) {
                return list;
            }
            for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                MSMsgExtra extra = serializeMsgExtra(cursor);
                list.add(extra);
            }
        }
        return list;
    }

    public List<MSMsg> insertOrReplaceExtra(List<MSMsgExtra> list) {
        List<String> msgIds = new ArrayList<>();
        List<ContentValues> cvList = new ArrayList<>();
        for (int i = 0, size = list.size(); i < size; i++) {
            if (!TextUtils.isEmpty(list.get(i).messageID)) {
                msgIds.add(list.get(i).messageID);
            }
            cvList.add(MSSqlContentValues.getCVWithMsgExtra(list.get(i)));
        }
        try {
            MSIMApplication.getInstance().getDbHelper().getDb()
                    .beginTransaction();
            if (!cvList.isEmpty()) {
                for (ContentValues cv : cvList) {
                    MSIMApplication.getInstance().getDbHelper().insert(messageExtra, cv);
                }
            }
            MSIMApplication.getInstance().getDbHelper().getDb()
                    .setTransactionSuccessful();
        } catch (Exception ignored) {
            MSLoggerUtils.getInstance().e(TAG, "insertOrReplace error");
        } finally {
            if (MSIMApplication.getInstance().getDbHelper().getDb().inTransaction()) {
                MSIMApplication.getInstance().getDbHelper().getDb()
                        .endTransaction();
            }
        }
        List<MSMsg> msgList = queryWithMsgIds(msgIds);
        return msgList;
    }


    /**
     * 查询按日期分组的消息数量
     *
     * @param channelID   频道ID
     * @param channelType 频道类型
     * @return List<MSMessageGroupByDate>
     */
    public List<MSMessageGroupByDate> queryMessageGroupByDateWithChannel(String channelID, byte channelType) {
        String sql = "SELECT DATE(" + MSDBColumns.MSMessageColumns.timestamp + ", 'unixepoch','localtime') AS days,COUNT(" + MSDBColumns.MSMessageColumns.client_msg_no + ") count,min(" + MSDBColumns.MSMessageColumns.order_seq + ") AS order_seq FROM " + message + "  WHERE " + MSDBColumns.MSMessageColumns.channel_type + " =? and " + MSDBColumns.MSMessageColumns.channel_id + "=? and is_deleted=0" + " GROUP BY " + MSDBColumns.MSMessageColumns.timestamp + "," + MSDBColumns.MSMessageColumns.order_seq + "";
        List<MSMessageGroupByDate> list = new ArrayList<>();
        try (Cursor cursor = MSIMApplication
                .getInstance()
                .getDbHelper()
                .rawQuery(sql, new Object[]{channelType, channelID})) {
            if (cursor == null) {
                return list;
            }
            for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                MSMessageGroupByDate msg = new MSMessageGroupByDate();
                msg.count = MSCursor.readLong(cursor, "count");
                msg.orderSeq = MSCursor.readLong(cursor, "order_seq");
                msg.date = MSCursor.readString(cursor, "days");
                list.add(msg);
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    /**
     * 清空所有聊天消息
     */
    public synchronized void clearEmpty() {
        MSIMApplication.getInstance().getDbHelper()
                .delete(message, null, null);
    }

    /**
     * 获取某个类型的聊天数据
     *
     * @param type            消息类型
     * @param oldestClientSeq 最后一次消息客户端ID
     * @param limit           数量
     */
    public List<MSMsg> queryWithContentType(int type, long oldestClientSeq, int limit) {
        String sql;
        Object[] args;
        if (oldestClientSeq <= 0) {
            args = new Object[1];
            args[0] = type;
            sql = "select * from (select " + messageCols + "," + extraCols + " from " + message + " left join " + messageExtra + " on " + message + ".message_id=" + messageExtra + ".message_id where " + message + ".type=?) where is_deleted=0 and revoke=0 order by " + MSDBColumns.MSMessageColumns.timestamp + " desc limit 0," + limit;
        } else {
            args = new Object[2];
            args[0] = type;
            args[1] = oldestClientSeq;
            sql = "select * from (select " + messageCols + "," + extraCols + " from " + message + " left join " + messageExtra + " on " + message + ".message_id=" + messageExtra + ".message_id where " + message + ".type=? and " + MSDBColumns.MSMessageColumns.client_seq + "<?) where is_deleted=0 and revoke=0 order by " + MSDBColumns.MSMessageColumns.timestamp + " desc limit 0," + limit;
        }
        List<MSMsg> msgs = new ArrayList<>();
        try (Cursor cursor = MSIMApplication.getInstance().getDbHelper().rawQuery(sql, args)) {
            if (cursor == null) {
                return msgs;
            }
            for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                MSMsg msg = serializeMsg(cursor);
                if (msg.channelType == MSChannelType.GROUP) {
                    //查询群成员信息
                    MSChannelMember member = ChannelMembersDbManager.getInstance().query(msg.channelID, MSChannelType.GROUP, msg.fromUID);
                    msg.setMemberOfFrom(member);
                    MSChannel channel = ChannelDBManager.getInstance().query(msg.fromUID, MSChannelType.PERSONAL);
                    msg.setFrom(channel);
                } else {
                    MSChannel channel = ChannelDBManager.getInstance().query(msg.fromUID, MSChannelType.PERSONAL);
                    msg.setFrom(channel);
                }
                msgs.add(0, msg);
            }
        }
        return msgs;
    }

    public List<MSMsg> searchWithChannel(String searchKey, String channelID, byte channelType) {
        List<MSMsg> msgs = new ArrayList<>();
        String sql = "select * from (select " + messageCols + "," + extraCols + " from " + message + " left join " + messageExtra + " on " + message + ".message_id=" + messageExtra + ".message_id where " + message + ".searchable_word like ? and " + message + ".channel_id=? and " + message + ".channel_type=?) where is_deleted=0 and revoke=0";
        try (Cursor cursor = MSIMApplication
                .getInstance()
                .getDbHelper()
                .rawQuery(sql, new Object[]{"%" + searchKey + "%", channelID, channelType})) {
            if (cursor == null) {
                return msgs;
            }
            for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                MSMsg msg = serializeMsg(cursor);
                if (msg.channelType == MSChannelType.GROUP) {
                    //查询群成员信息
                    MSChannelMember member = ChannelMembersDbManager.getInstance().query(msg.channelID, MSChannelType.GROUP, msg.fromUID);
                    msg.setMemberOfFrom(member);
                    MSChannel channel = ChannelDBManager.getInstance().query(msg.fromUID, MSChannelType.PERSONAL);
                    msg.setFrom(channel);
                } else {
                    MSChannel channel = ChannelDBManager.getInstance().query(msg.fromUID, MSChannelType.PERSONAL);
                    msg.setFrom(channel);
                }
                msgs.add(0, msg);
            }
        } catch (Exception ignored) {
        }
        return msgs;
    }

    public List<MSMessageSearchResult> search(String searchKey) {
        List<MSMessageSearchResult> list = new ArrayList<>();

        String sql = "select distinct c.*, count(*) message_count, case count(*) WHEN 1 then" +
                " m.client_seq else ''END client_seq, CASE count(*) WHEN 1 THEN m.searchable_word else '' end searchable_word " +
                "from " + channel + " c LEFT JOIN " + message + " m ON m.channel_id = c.channel_id and " +
                "m.channel_type = c.channel_type WHERE m.is_deleted=0 and searchable_word LIKE ? GROUP BY " +
                "c.channel_id, c.channel_type ORDER BY m.created_at DESC limit 100";
        Cursor cursor = MSIMApplication.getInstance().getDbHelper().rawQuery(sql, new Object[]{"%" + searchKey + "%"});
        if (cursor == null) {
            return list;
        }
        for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
            MSChannel channel = ChannelDBManager.getInstance().serializableChannel(cursor);
            MSMessageSearchResult result = new MSMessageSearchResult();
            result.msChannel = channel;
            result.messageCount = MSCursor.readInt(cursor, "message_count");
            result.searchableWord = MSCursor.readString(cursor, MSDBColumns.MSMessageColumns.searchable_word);
            list.add(result);
        }
        cursor.close();
        return list;
    }

    public synchronized boolean deleteWithChannel(String channelId, byte channelType) {

        String[] updateKey = new String[1];
        String[] updateValue = new String[1];

        updateKey[0] = MSDBColumns.MSMessageColumns.is_deleted;
        updateValue[0] = "1";

        String where = MSDBColumns.MSMessageColumns.channel_id + "=? and " + MSDBColumns.MSMessageColumns.channel_type + "=?";
        String[] whereValue = new String[2];
        whereValue[0] = channelId;
        whereValue[1] = String.valueOf(channelType);

        int row = MSIMApplication.getInstance().getDbHelper()
                .update(message, updateKey, updateValue, where, whereValue);
        return row > 0;
    }

    public synchronized boolean deleteWithChannelAndFromUID(String channelId, byte channelType, String fromUID) {
        String[] updateKey = new String[1];
        String[] updateValue = new String[1];

        updateKey[0] = MSDBColumns.MSMessageColumns.is_deleted;
        updateValue[0] = "1";

        String where = MSDBColumns.MSMessageColumns.channel_id + "=? and " + MSDBColumns.MSMessageColumns.channel_type + "=? and " + MSDBColumns.MSMessageColumns.from_uid + "=?";
        String[] whereValue = new String[3];
        whereValue[0] = channelId;
        whereValue[1] = String.valueOf(channelType);
        whereValue[2] = String.valueOf(fromUID);

        int row = MSIMApplication.getInstance().getDbHelper()
                .update(message, updateKey, updateValue, where, whereValue);
        return row > 0;
    }

    /**
     * 查询固定类型的消息记录
     *
     * @param channelID      频道ID
     * @param channelType    频道类型
     * @param oldestOrderSeq 排序编号
     * @param limit          查询数量
     * @param contentTypes   内容类型
     * @return List<MSMsg>
     */
    public List<MSMsg> searchWithChannelAndContentTypes(String channelID, byte channelType, long oldestOrderSeq, int limit, int[] contentTypes) {
        if (TextUtils.isEmpty(channelID) || contentTypes == null || contentTypes.length == 0) {
            return null;
        }
        String whereStr = "";
        for (int contentType : contentTypes) {
            if (TextUtils.isEmpty(whereStr)) {
                whereStr = String.valueOf(contentType);
            } else {
                whereStr = "," + contentType;
            }
        }
        Object[] args;
        String sql;
        if (oldestOrderSeq <= 0) {
            args = new Object[]{channelID, channelType, whereStr};
            sql = "select * from (select " + messageCols + "," + extraCols + " from " + message + " left join " + messageExtra + " on " + message + ".message_id= " + messageExtra + ".message_id where " + message + ".channel_id=? and " + message + ".channel_type=? and " + message + ".type<>0 and " + message + ".type<>99 and " + message + ".type in (?)) where is_deleted=0 and revoke=0 order by order_seq desc limit 0," + limit;
        } else {
            args = new Object[]{channelID, channelType, oldestOrderSeq, whereStr};
            sql = "select * from (select " + messageCols + "," + extraCols + " from " + message + " left join " + messageExtra + " on " + message + ".message_id= " + messageExtra + ".message_id where " + message + ".channel_id=? and " + message + ".channel_type=? and " + message + ".order_seq<? and " + message + ".type<>0 and " + message + ".type<>99 and " + message + ".type in (?)) where is_deleted=0 and revoke=0 order by order_seq desc limit 0," + limit;
        }
        List<MSMsg> msMsgs = new ArrayList<>();
        try (Cursor cursor = MSIMApplication
                .getInstance()
                .getDbHelper()
                .rawQuery(sql, args)) {
            if (cursor == null) {
                return msMsgs;
            }
            for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                MSMsg msg = serializeMsg(cursor);
                if (msg.channelType == MSChannelType.GROUP) {
                    //查询群成员信息
                    MSChannelMember member = ChannelMembersDbManager.getInstance().query(msg.channelID, MSChannelType.GROUP, msg.fromUID);
                    msg.setMemberOfFrom(member);
                    MSChannel channel = ChannelDBManager.getInstance().query(msg.fromUID, MSChannelType.PERSONAL);
                    msg.setFrom(channel);
                } else {
                    MSChannel channel = ChannelDBManager.getInstance().query(msg.fromUID, MSChannelType.PERSONAL);
                    msg.setFrom(channel);
                }
                msMsgs.add(msg);
            }
        } catch (Exception ignored) {
        }
        return msMsgs;
    }

    /**
     * 获取最大扩展编号消息
     *
     * @param channelID   频道ID
     * @param channelType 频道类型
     */
    public long queryMsgExtraMaxVersionWithChannel(String channelID, byte channelType) {
        String sql = "select * from " + messageExtra + " where channel_id =? and channel_type=? order by extra_version desc limit 1";
        Cursor cursor = null;
        long version = 0;
        try {
            cursor = MSIMApplication
                    .getInstance()
                    .getDbHelper().rawQuery(sql, new Object[]{channelID, channelType});
            if (cursor == null) {
                return 0;
            }
            if (cursor.moveToLast()) {
                version = MSCursor.readLong(cursor, "extra_version");
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return version;
    }

    public synchronized boolean updateFieldWithClientMsgNo(String clientMsgNo, String field, String value, boolean isRefreshUI) {
        String[] updateKey = new String[]{field};
        String[] updateValue = new String[]{value};
        String where = MSDBColumns.MSMessageColumns.client_msg_no + "=?";
        String[] whereValue = new String[1];
        whereValue[0] = clientMsgNo;
        int row = MSIMApplication.getInstance().getDbHelper()
                .update(message, updateKey, updateValue, where, whereValue);
        if (row > 0 && isRefreshUI) {
            MSMsg msg = queryWithClientMsgNo(clientMsgNo);
            if (msg != null)
                MSIM.getInstance().getMsgManager().setRefreshMsg(msg, true);
        }
        return row > 0;
    }

    public synchronized boolean updateFieldWithMessageID(String messageID, String field, String value) {
        String[] updateKey = new String[]{field};
        String[] updateValue = new String[]{value};
        String where = MSDBColumns.MSMessageColumns.message_id + "=?";
        String[] whereValue = new String[1];
        whereValue[0] = messageID;
        int row = MSIMApplication.getInstance().getDbHelper()
                .update(message, updateKey, updateValue, where, whereValue);
        if (row > 0) {
            MSMsg msg = queryWithMessageID(messageID, true);
            if (msg != null)
                MSIM.getInstance().getMsgManager().setRefreshMsg(msg, true);
        }
        return row > 0;

    }


    public MSMsg queryWithMessageID(String messageID, boolean isGetMsgReaction) {
        MSMsg msg = null;
        String sql = "select " + messageCols + "," + extraCols + " from " + message + " LEFT JOIN " + messageExtra + " ON " + message + ".message_id=" + messageExtra + ".message_id WHERE " + message + ".message_id=? and " + message + ".is_deleted=0";

        try (Cursor cursor = MSIMApplication
                .getInstance()
                .getDbHelper().rawQuery(sql, new Object[]{messageID})) {
            if (cursor == null) {
                return null;
            }
            if (cursor.moveToLast()) {
                msg = serializeMsg(cursor);
            }
        }
        if (msg != null && isGetMsgReaction)
            msg.reactionList = MsgReactionDBManager.getInstance().queryWithMessageId(msg.messageID);
        return msg;
    }

    public int queryMaxMessageOrderSeqWithChannel(String channelID, byte channelType) {
        String sql = "SELECT max(order_seq) order_seq FROM " + message + " WHERE channel_id=? AND channel_type=?";
        int orderSeq = 0;
        try (Cursor cursor = MSIMApplication
                .getInstance()
                .getDbHelper().rawQuery(sql, new Object[]{channelID, channelType})) {
            if (cursor == null) {
                return 0;
            }
            if (cursor.moveToLast()) {
                orderSeq = MSCursor.readInt(cursor, MSDBColumns.MSMessageColumns.order_seq);
            }
        }
        return orderSeq;
    }

    public int queryMaxMessageSeqNotDeletedWithChannel(String channelID, byte channelType) {
        String sql = "SELECT max(message_seq) message_seq FROM " + message + " WHERE channel_id=? AND channel_type=? AND is_deleted=0";
        int messageSeq = 0;
        try (Cursor cursor = MSIMApplication
                .getInstance()
                .getDbHelper().rawQuery(sql, new Object[]{channelID, channelType})) {
            if (cursor == null) {
                return 0;
            }
            if (cursor.moveToLast()) {
                messageSeq = MSCursor.readInt(cursor, MSDBColumns.MSMessageColumns.message_seq);
            }
        }
        return messageSeq;
    }

    public int queryMaxMessageSeqWithChannel(String channelID, byte channelType) {
        String sql = "SELECT max(message_seq) message_seq FROM " + message + " WHERE channel_id=? AND channel_type=?";
        int messageSeq = 0;
        try (Cursor cursor = MSIMApplication
                .getInstance()
                .getDbHelper().rawQuery(sql, new Object[]{channelID, channelType})) {
            if (cursor == null) {
                return 0;
            }
            if (cursor.moveToLast()) {
                messageSeq = MSCursor.readInt(cursor, MSDBColumns.MSMessageColumns.message_seq);
            }
        }
        return messageSeq;
    }

    public int queryMinMessageSeqWithChannel(String channelID, byte channelType) {
        String sql = "SELECT min(message_seq) message_seq FROM " + message + " WHERE channel_id=? AND channel_type=?";
        int messageSeq = 0;
        try (Cursor cursor = MSIMApplication
                .getInstance()
                .getDbHelper().rawQuery(sql, new Object[]{channelID, channelType})) {
            if (cursor == null) {
                return 0;
            }
            if (cursor.moveToLast()) {
                messageSeq = MSCursor.readInt(cursor, MSDBColumns.MSMessageColumns.message_seq);
            }
        }
        return messageSeq;
    }

    private int queryMsgSeq(String channelID, byte channelType, long oldestOrderSeq, int pullMode) {
        String sql;
        int messageSeq = 0;
        if (pullMode == 1) {
            sql = "select * from " + message + " where channel_id=? and channel_type=? and  order_seq>? and message_seq<>0 order by message_seq desc limit 1";
        } else
            sql = "select * from " + message + " where channel_id=? and channel_type=? and  order_seq<? and message_seq<>0 order by message_seq asc limit 1";
        try (Cursor cursor = MSIMApplication
                .getInstance()
                .getDbHelper().rawQuery(sql, new Object[]{channelID, channelType, oldestOrderSeq})) {
            if (cursor == null) {
                return 0;
            }
            if (cursor.moveToLast()) {
                MSMsg msg = serializeMsg(cursor);
                messageSeq = msg.messageSeq;
            }
        }
        return messageSeq;
    }

    public List<MSMsg> queryWithMsgIds(List<String> messageIds) {

        String sql = "select " + messageCols + "," + extraCols + " from " + message + " left join " + messageExtra + " on " + message + ".message_id=" + messageExtra + ".message_id where " + message + ".message_id in (" + MSCursor.getPlaceholders(messageIds.size()) + ")";
        List<MSMsg> list = new ArrayList<>();
        List<String> gChannelIds = new ArrayList<>();
        List<String> pChannelIds = new ArrayList<>();
        List<String> fromChannelIds = new ArrayList<>();
        try (Cursor cursor = MSIMApplication
                .getInstance()
                .getDbHelper()
                .rawQuery(sql, messageIds.toArray())) {
            if (cursor == null) {
                return list;
            }

            for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                MSMsg msg = serializeMsg(cursor);
                boolean isAdd = true;
                if (msg.channelType == MSChannelType.GROUP) {
                    //查询群成员信息
                    for (int i = 0; i < gChannelIds.size(); i++) {
                        if (gChannelIds.get(i).equals(msg.fromUID)) {
                            isAdd = false;
                            break;
                        }
                    }
                    if (isAdd) {
                        gChannelIds.add(msg.fromUID);
                    }
                } else {
                    for (int i = 0; i < pChannelIds.size(); i++) {
                        if (pChannelIds.get(i).equals(msg.channelID)) {
                            isAdd = false;
                            break;
                        }
                    }
                    if (isAdd) {
                        pChannelIds.add(msg.channelID);
                    }

                }
                isAdd = true;
                for (int i = 0; i < fromChannelIds.size(); i++) {
                    if (fromChannelIds.get(i).equals(msg.fromUID)) {
                        isAdd = false;
                        break;
                    }
                }
                if (isAdd) {
                    fromChannelIds.add(msg.fromUID);
                }

                list.add(msg);
            }

        } catch (Exception ignored) {
        }

        if (MSCommonUtils.isNotEmpty(gChannelIds)) {
            List<MSChannel> channels = ChannelDBManager.getInstance().queryWithChannelIdsAndChannelType(gChannelIds, MSChannelType.GROUP);
            if (MSCommonUtils.isNotEmpty(channels)) {
                for (MSChannel channel : channels) {
                    if (channel == null || TextUtils.isEmpty(channel.channelID)) continue;
                    for (int i = 0, size = list.size(); i < size; i++) {
                        if (list.get(i).channelType == MSChannelType.GROUP && channel.channelID.equals(list.get(i).channelID)) {
                            list.get(i).setChannelInfo(channel);
                        }
                    }
                }
            }
        }
        if (MSCommonUtils.isNotEmpty(pChannelIds)) {
            List<MSChannel> channels = ChannelDBManager.getInstance().queryWithChannelIdsAndChannelType(pChannelIds, MSChannelType.PERSONAL);
            if (MSCommonUtils.isNotEmpty(channels)) {
                for (MSChannel channel : channels) {
                    if (channel == null || TextUtils.isEmpty(channel.channelID)) continue;
                    for (int i = 0, size = list.size(); i < size; i++) {
                        if (list.get(i).channelType == MSChannelType.PERSONAL && channel.channelID.equals(list.get(i).channelID)) {
                            list.get(i).setChannelInfo(channel);
                        }
                    }
                }
            }
        }

        if (MSCommonUtils.isNotEmpty(fromChannelIds)) {
            List<MSChannel> channels = ChannelDBManager.getInstance().queryWithChannelIdsAndChannelType(fromChannelIds, MSChannelType.PERSONAL);
            if (MSCommonUtils.isNotEmpty(channels)) {
                for (MSChannel channel : channels) {
                    if (channel == null || TextUtils.isEmpty(channel.channelID)) continue;
                    for (int i = 0, size = list.size(); i < size; i++) {
                        if (!TextUtils.isEmpty(list.get(i).fromUID) && list.get(i).channelType == MSChannelType.PERSONAL && channel.channelID.equals(list.get(i).fromUID)) {
                            list.get(i).setFrom(channel);
                        }
                    }
                }
            }
        }
        return list;
    }

    /**
     * 删除消息
     *
     * @param clientMsgNO 消息ID
     */
    public synchronized MSMsg deleteWithClientMsgNo(String clientMsgNO) {
        String[] updateKey = new String[1];
        String[] updateValue = new String[1];
        updateKey[0] = MSDBColumns.MSMessageColumns.is_deleted;
        updateValue[0] = "1";
        String where = MSDBColumns.MSMessageColumns.client_msg_no + "=?";
        String[] whereValue = new String[1];
        whereValue[0] = clientMsgNO;
        MSMsg msg = null;
        int row = MSIMApplication.getInstance().getDbHelper()
                .update(message, updateKey, updateValue, where, whereValue);
        if (row > 0) {
            msg = queryWithClientMsgNo(clientMsgNO);
        }
        return msg;
    }

    public long getMaxReactionSeqWithChannel(String channelID, byte channelType) {
        return MsgReactionDBManager.getInstance().queryMaxSeqWithChannel(channelID, channelType);
    }

    public void insertMsgReactions(List<MSMsgReaction> list) {
        MsgReactionDBManager.getInstance().insertReactions(list);
    }

    public List<MSMsgReaction> queryMsgReactionWithMsgIds(List<String> messageIds) {
        return MsgReactionDBManager.getInstance().queryWithMessageIds(messageIds);
    }

    public synchronized void updateAllMsgSendFail() {
        String[] updateKey = new String[1];
        updateKey[0] = MSDBColumns.MSMessageColumns.status;
        String[] updateValue = new String[1];
        updateValue[0] = MSSendMsgResult.send_fail + "";
        String where = MSDBColumns.MSMessageColumns.status + "=? ";
        String[] whereValue = new String[1];
        whereValue[0] = "0";
        try {
            if (MSIMApplication.getInstance().getDbHelper() != null) {
                MSIMApplication
                        .getInstance()
                        .getDbHelper()
                        .update(message, updateKey, updateValue, where,
                                whereValue);
            }
        } catch (Exception ignored) {
        }
    }

    public synchronized void updateMsgStatus(long client_seq, int status) {
        String[] updateKey = new String[1];
        String[] updateValue = new String[1];
        updateKey[0] = MSDBColumns.MSMessageColumns.status;
        updateValue[0] = String.valueOf(status);

        String where = MSDBColumns.MSMessageColumns.client_seq + "=?";
        String[] whereValue = new String[1];
        whereValue[0] = String.valueOf(client_seq);

        int row = MSIMApplication.getInstance().getDbHelper()
                .update(message, updateKey, updateValue, where, whereValue);
        if (row > 0) {
            MSMsg msg = queryWithClientSeq(client_seq);
            if (msg != null) {
                msg.status = status;
                MSIM.getInstance().getMsgManager().setRefreshMsg(msg, true);
            }
        }
    }

    public int queryMaxMessageSeqWithChannel() {
        int maxMessageSeq = 0;
        String sql = "select max(message_seq) message_seq from " + message;
        try {
            if (MSIMApplication.getInstance().getDbHelper() != null) {
                Cursor cursor = MSIMApplication
                        .getInstance()
                        .getDbHelper()
                        .rawQuery(sql);
                if (cursor != null) {
                    if (cursor.moveToFirst()) {
                        maxMessageSeq = MSCursor.readInt(cursor, MSDBColumns.MSMessageColumns.message_seq);
                    }
                    cursor.close();
                }
            }
        } catch (Exception ignored) {
        }
        return maxMessageSeq;
    }

    public List<MSMsgExtra> queryMsgExtraWithNeedUpload(int needUpload) {
        List<MSMsgExtra> list = new ArrayList<>();
        String sql = "select * from " + messageExtra + " where need_upload=?";
        try (Cursor cursor = MSIMApplication.getInstance().getDbHelper().rawQuery(sql, new Object[]{needUpload})) {
            if (cursor == null) {
                return list;
            }
            for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                MSMsgExtra extra = serializeMsgExtra(cursor);
                list.add(extra);
            }
        }
        return list;
    }

    public MSMsgExtra queryMsgExtraWithMsgID(String msgID) {
        MSMsgExtra extra = null;
        try {
            if (MSIMApplication.getInstance().getDbHelper() != null) {
                Cursor cursor = MSIMApplication
                        .getInstance()
                        .getDbHelper()
                        .select(messageExtra, "message_id=?", new String[]{msgID}, null);
                if (cursor != null) {
                    if (cursor.moveToFirst()) {
                        extra = serializeMsgExtra(cursor);
                    }
                    cursor.close();
                }
            }
        } catch (Exception ignored) {
        }
        return extra;
    }

    private MSMsgExtra serializeMsgExtra(Cursor cursor) {
        MSMsgExtra extra = new MSMsgExtra();
        extra.messageID = MSCursor.readString(cursor, "message_id");
        extra.channelID = MSCursor.readString(cursor, "channel_id");
        extra.channelType = MSCursor.readByte(cursor, "channel_type");
        extra.readed = MSCursor.readInt(cursor, "readed");
        extra.readedCount = MSCursor.readInt(cursor, "readed_count");
        extra.unreadCount = MSCursor.readInt(cursor, "unread_count");
        extra.revoke = MSCursor.readInt(cursor, "revoke");
        extra.isMutualDeleted = MSCursor.readInt(cursor, "is_mutual_deleted");
        extra.revoker = MSCursor.readString(cursor, "revoker");
        extra.extraVersion = MSCursor.readLong(cursor, "extra_version");
        extra.editedAt = MSCursor.readLong(cursor, "edited_at");
        extra.contentEdit = MSCursor.readString(cursor, "content_edit");
        extra.needUpload = MSCursor.readInt(cursor, "need_upload");
        extra.isPinned = MSCursor.readInt(cursor, "is_pinned");
        return extra;
    }

    private MSMsg serializeMsg(Cursor cursor) {
        MSMsg msg = new MSMsg();
        msg.messageID = MSCursor.readString(cursor, MSDBColumns.MSMessageColumns.message_id);
        msg.messageSeq = MSCursor.readInt(cursor, MSDBColumns.MSMessageColumns.message_seq);
        msg.clientSeq = MSCursor.readInt(cursor, MSDBColumns.MSMessageColumns.client_seq);
        msg.timestamp = MSCursor.readLong(cursor, MSDBColumns.MSMessageColumns.timestamp);
        msg.fromUID = MSCursor.readString(cursor, MSDBColumns.MSMessageColumns.from_uid);
        msg.channelID = MSCursor.readString(cursor, MSDBColumns.MSMessageColumns.channel_id);
        msg.channelType = MSCursor.readByte(cursor, MSDBColumns.MSMessageColumns.channel_type);
        msg.type = MSCursor.readInt(cursor, MSDBColumns.MSMessageColumns.type);
        msg.content = MSCursor.readString(cursor, MSDBColumns.MSMessageColumns.content);
        msg.status = MSCursor.readInt(cursor, MSDBColumns.MSMessageColumns.status);
        msg.voiceStatus = MSCursor.readInt(cursor, MSDBColumns.MSMessageColumns.voice_status);
        msg.createdAt = MSCursor.readString(cursor, MSDBColumns.MSMessageColumns.created_at);
        msg.updatedAt = MSCursor.readString(cursor, MSDBColumns.MSMessageColumns.updated_at);
        msg.searchableWord = MSCursor.readString(cursor, MSDBColumns.MSMessageColumns.searchable_word);
        msg.clientMsgNO = MSCursor.readString(cursor, MSDBColumns.MSMessageColumns.client_msg_no);
        msg.isDeleted = MSCursor.readInt(cursor, MSDBColumns.MSMessageColumns.is_deleted);
        msg.orderSeq = MSCursor.readLong(cursor, MSDBColumns.MSMessageColumns.order_seq);
        byte setting = MSCursor.readByte(cursor, MSDBColumns.MSMessageColumns.setting);
        msg.setting = MSTypeUtils.getInstance().getMsgSetting(setting);
        msg.flame = MSCursor.readInt(cursor, MSDBColumns.MSMessageColumns.flame);
        msg.flameSecond = MSCursor.readInt(cursor, MSDBColumns.MSMessageColumns.flame_second);
        msg.viewed = MSCursor.readInt(cursor, MSDBColumns.MSMessageColumns.viewed);
        msg.viewedAt = MSCursor.readLong(cursor, MSDBColumns.MSMessageColumns.viewed_at);
        msg.topicID = MSCursor.readString(cursor, MSDBColumns.MSMessageColumns.topic_id);
        msg.expireTime = MSCursor.readInt(cursor, MSDBColumns.MSMessageColumns.expire_time);
        msg.expireTimestamp = MSCursor.readInt(cursor, MSDBColumns.MSMessageColumns.expire_timestamp);
        // 扩展表数据
        msg.remoteExtra = serializeMsgExtra(cursor);

        String extra = MSCursor.readString(cursor, MSDBColumns.MSMessageColumns.extra);
        if (!TextUtils.isEmpty(extra)) {
            HashMap<String, Object> hashMap = new HashMap<>();
            try {
                JSONObject jsonObject = new JSONObject(extra);
                Iterator<String> keys = jsonObject.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    hashMap.put(key, jsonObject.opt(key));
                }
            } catch (JSONException e) {
                MSLoggerUtils.getInstance().e(TAG, " serializeMsg error extra not json format");
            }
            msg.localExtraMap = hashMap;
        }
        //获取附件
        msg.baseContentMsgModel = getMsgModel(msg);
        if (!TextUtils.isEmpty(msg.remoteExtra.contentEdit)) {
            msg.remoteExtra.contentEditMsgModel = MsgManager.getInstance().getMsgContentModel(msg.remoteExtra.contentEdit);
        }
        return msg;
    }

    private MSMessageContent getMsgModel(MSMsg msg) {
        JSONObject jsonObject = null;
        if (!TextUtils.isEmpty(msg.content)) {
            try {
                jsonObject = new JSONObject(msg.content);
            } catch (JSONException e) {
                MSLoggerUtils.getInstance().e(TAG, "getMsgModel error content not json format");
                return new MSFormatErrorContent();
            }
        }
        return MSIM.getInstance()
                .getMsgManager().getMsgContentModel(msg.type, jsonObject);
    }

}
