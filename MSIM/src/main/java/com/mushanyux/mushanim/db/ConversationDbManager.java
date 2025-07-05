package com.mushanyux.mushanim.db;

import static com.mushanyux.mushanim.db.MSDBColumns.TABLE.channel;
import static com.mushanyux.mushanim.db.MSDBColumns.TABLE.conversation;
import static com.mushanyux.mushanim.db.MSDBColumns.TABLE.conversationExtra;
import static com.mushanyux.mushanim.db.MSDBColumns.TABLE.message;

import android.content.ContentValues;
import android.database.Cursor;
import android.text.TextUtils;

import com.mushanyux.mushanim.MSIM;
import com.mushanyux.mushanim.MSIMApplication;
import com.mushanyux.mushanim.entity.MSChannel;
import com.mushanyux.mushanim.entity.MSChannelType;
import com.mushanyux.mushanim.entity.MSConversationMsg;
import com.mushanyux.mushanim.entity.MSConversationMsgExtra;
import com.mushanyux.mushanim.entity.MSMsg;
import com.mushanyux.mushanim.entity.MSMsgExtra;
import com.mushanyux.mushanim.entity.MSUIConversationMsg;
import com.mushanyux.mushanim.manager.ConversationManager;
import com.mushanyux.mushanim.utils.MSCommonUtils;
import com.mushanyux.mushanim.utils.MSLoggerUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

public class ConversationDbManager {
    private final String TAG = "ConversationDbManager";
    private final String extraCols = "IFNULL(" + conversationExtra + ".browse_to,0) AS browse_to,IFNULL(" + conversationExtra + ".keep_message_seq,0) AS keep_message_seq,IFNULL(" + conversationExtra + ".keep_offset_y,0) AS keep_offset_y,IFNULL(" + conversationExtra + ".draft,'') AS draft,IFNULL(" + conversationExtra + ".version,0) AS extra_version";
    private final String channelCols = channel + ".channel_remark," +
            channel + ".channel_name," +
            channel + ".top," +
            channel + ".mute," +
            channel + ".save," +
            channel + ".status as channel_status," +
            channel + ".forbidden," +
            channel + ".invite," +
            channel + ".follow," +
            channel + ".is_deleted as channel_is_deleted," +
            channel + ".show_nick," +
            channel + ".avatar," +
            channel + ".avatar_cache_key," +
            channel + ".online," +
            channel + ".last_offline," +
            channel + ".category," +
            channel + ".receipt," +
            channel + ".robot," +
            channel + ".parent_channel_id AS c_parent_channel_id," +
            channel + ".parent_channel_type AS c_parent_channel_type," +
            channel + ".version AS channel_version," +
            channel + ".remote_extra AS channel_remote_extra," +
            channel + ".extra AS channel_extra";

    private ConversationDbManager() {
    }

    private static class ConversationDbManagerBinder {
        static final ConversationDbManager db = new ConversationDbManager();
    }

    public static ConversationDbManager getInstance() {
        return ConversationDbManagerBinder.db;
    }

    public synchronized List<MSUIConversationMsg> queryAll() {
        List<MSUIConversationMsg> list = new ArrayList<>();
        if (MSIMApplication.getInstance().getDbHelper() == null || MSIMApplication.getInstance().getDbHelper().getDb() == null) {
            return list;
        }

        String sql = "SELECT " + conversation + ".*," + channelCols + "," + extraCols + " FROM "
                + conversation + " LEFT JOIN " + channel + " ON "
                + conversation + ".channel_id = " + channel + ".channel_id AND "
                + conversation + ".channel_type = " + channel + ".channel_type LEFT JOIN " + conversationExtra + " ON " + conversation + ".channel_id=" + conversationExtra + ".channel_id AND " + conversation + ".channel_type=" + conversationExtra + ".channel_type where " + conversation + ".is_deleted=0 order by "
                + MSDBColumns.MSCoverMessageColumns.last_msg_timestamp + " desc";
        try (Cursor cursor = MSIMApplication
                .getInstance()
                .getDbHelper()
                .rawQuery(sql)) {
            if (cursor == null) {
                return list;
            }
            List<String> clientMsgNos = new ArrayList<>();
            for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                MSConversationMsg msg = serializeMsg(cursor);
                if (msg.isDeleted == 0) {
                    MSUIConversationMsg uiMsg = getUIMsg(msg, cursor);
                    list.add(uiMsg);
                    clientMsgNos.add(uiMsg.clientMsgNo);
                }
            }
            if (!clientMsgNos.isEmpty()) {
                List<MSMsg> msgList = queryWithClientMsgNos(clientMsgNos);
                List<String> msgIds = new ArrayList<>();
                if (MSCommonUtils.isNotEmpty(msgList)) {
                    for (MSUIConversationMsg uiMsg : list) {
                        for (MSMsg msg : msgList) {
                            if (uiMsg.clientMsgNo.equals(msg.clientMsgNO)) {
                                uiMsg.setMsMsg(msg);
                                if (!TextUtils.isEmpty(msg.messageID)) {
                                    msgIds.add(msg.messageID);
                                }
                                break;
                            }
                        }
                    }
                }
                List<MSMsgExtra> extraList = queryWithMsgIds(msgIds);
                if (MSCommonUtils.isNotEmpty(extraList)) {
                    for (MSUIConversationMsg uiMsg : list) {
                        for (MSMsgExtra extra : extraList) {
                            if (uiMsg.getMsMsg() != null && !TextUtils.isEmpty(uiMsg.getMsMsg().messageID) && uiMsg.getMsMsg().messageID.equals(extra.messageID)) {
                                uiMsg.getMsMsg().remoteExtra = extra;
                                break;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            MSLoggerUtils.getInstance().e(TAG, "queryAll error");
        }
        return list;
    }

    private List<MSMsgExtra> queryWithMsgIds(List<String> msgIds) {
        List<MSMsgExtra> msgExtraList = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        for (int i = 0, size = msgIds.size(); i < size; i++) {
            if (ids.size() == 200) {
                List<MSMsgExtra> list = MsgDbManager.getInstance().queryMsgExtrasWithMsgIds(ids);
                if (MSCommonUtils.isNotEmpty(list)) {
                    msgExtraList.addAll(list);
                }
                ids.clear();
            }
            ids.add(msgIds.get(i));
        }
        return msgExtraList;
    }

    private List<MSMsg> queryWithClientMsgNos(List<String> clientMsgNos) {
        List<MSMsg> msgList = new ArrayList<>();
        List<String> nos = new ArrayList<>();
        for (int i = 0, size = clientMsgNos.size(); i < size; i++) {
            if (nos.size() == 200) {
                List<MSMsg> list = MsgDbManager.getInstance().queryWithClientMsgNos(nos);
                if (MSCommonUtils.isNotEmpty(list)) {
                    msgList.addAll(list);
                }
                nos.clear();
            }
            nos.add(clientMsgNos.get(i));
        }
        if (!nos.isEmpty()) {
            List<MSMsg> list = MsgDbManager.getInstance().queryWithClientMsgNos(nos);
            if (MSCommonUtils.isNotEmpty(list)) {
                msgList.addAll(list);
            }
            nos.clear();
        }
        return msgList;
    }

    public List<MSUIConversationMsg> queryWithChannelIds(List<String> channelIds) {
        String sql = "select " + conversation + ".*," + channelCols + "," + extraCols + " from " + conversation + " left join " + channel + " on " + conversation + ".channel_id=" + channel + ".channel_id and " + conversation + ".channel_type=" + channel + ".channel_type left join " + conversationExtra + " on " + conversation + ".channel_id=" + conversationExtra + ".channel_id and " + conversation + ".channel_type=" + conversationExtra + ".channel_type where " + conversation + ".is_deleted=0 and " + conversation + ".channel_id in (" + MSCursor.getPlaceholders(channelIds.size()) + ")";
        List<MSUIConversationMsg> list = new ArrayList<>();
        try (Cursor cursor = MSIMApplication
                .getInstance()
                .getDbHelper()
                .rawQuery(sql, channelIds.toArray(new String[0]))) {
            if (cursor == null) {
                return list;
            }

            for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                MSConversationMsg msg = serializeMsg(cursor);
                MSUIConversationMsg uiMsg = getUIMsg(msg, cursor);
                list.add(uiMsg);
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    public List<MSConversationMsg> queryWithChannelType(byte channelType) {
        List<MSConversationMsg> list = new ArrayList<>();
        try (Cursor cursor = MSIMApplication
                .getInstance()
                .getDbHelper()
                .select(conversation, "channel_type=?", new String[]{String.valueOf(channelType)}, null)) {
            if (cursor == null) {
                return list;
            }

            for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                MSConversationMsg msg = serializeMsg(cursor);
                list.add(msg);
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    private MSUIConversationMsg getUIMsg(MSConversationMsg msg, Cursor cursor) {
        MSUIConversationMsg uiMsg = getUIMsg(msg);
        MSChannel channel = ChannelDBManager.getInstance().serializableChannel(cursor);
        if (channel != null) {
            String extra = MSCursor.readString(cursor, "channel_extra");
            channel.localExtra = MSCommonUtils.str2HashMap(extra);
            String remoteExtra = MSCursor.readString(cursor, "channel_remote_extra");
            channel.remoteExtraMap = MSCommonUtils.str2HashMap(remoteExtra);
            channel.status = MSCursor.readInt(cursor, "channel_status");
            channel.version = MSCursor.readLong(cursor, "channel_version");
            channel.parentChannelID = MSCursor.readString(cursor, "c_parent_channel_id");
            channel.parentChannelType = MSCursor.readByte(cursor, "c_parent_channel_type");
            channel.channelID = msg.channelID;
            channel.channelType = msg.channelType;
            uiMsg.setMsChannel(channel);
        }
        return uiMsg;
    }

    public long queryMaxVersion() {
        long maxVersion = 0;
        String sql = "select max(version) version from " + conversation + " limit 0, 1";
        Cursor cursor = MSIMApplication
                .getInstance()
                .getDbHelper()
                .rawQuery(sql);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                maxVersion = MSCursor.readLong(cursor, MSDBColumns.MSCoverMessageColumns.version);
            }
            cursor.close();
        }
        return maxVersion;
    }

    public synchronized ContentValues getInsertSyncCV(MSConversationMsg conversationMsg) {
        return MSSqlContentValues.getContentValuesWithCoverMsg(conversationMsg, true);
    }

    public synchronized void insertSyncMsg(ContentValues cv) {
        MSIMApplication.getInstance().getDbHelper().insertSql(conversation, cv);
    }

    public synchronized String queryLastMsgSeqs() {
        String lastMsgSeqs = "";
        String sql = "select GROUP_CONCAT(channel_id||':'||channel_type||':'|| last_seq,'|') synckey from (select *,(select max(message_seq) from " + message + " where " + message + ".channel_id=" + conversation + ".channel_id and " + message + ".channel_type=" + conversation + ".channel_type limit 1) last_seq from " + conversation + ") cn where channel_id<>'' AND is_deleted=0";
        Cursor cursor = MSIMApplication.getInstance().getDbHelper().rawQuery(sql);
        if (cursor == null) {
            return lastMsgSeqs;
        }
        if (cursor.moveToFirst()) {
            lastMsgSeqs = MSCursor.readString(cursor, "synckey");
        }
        cursor.close();

        return lastMsgSeqs;
    }

    public synchronized boolean updateRedDot(String channelID, byte channelType, int redDot) {
        if (MSIMApplication.getInstance().getDbHelper() == null || MSIMApplication.getInstance().getDbHelper().getDb() == null) {
            return false;
        }
        ContentValues cv = new ContentValues();
        cv.put(MSDBColumns.MSCoverMessageColumns.unread_count, redDot);
        return MSIMApplication.getInstance().getDbHelper().update(conversation, MSDBColumns.MSCoverMessageColumns.channel_id + "='" + channelID + "' and " + MSDBColumns.MSCoverMessageColumns.channel_type + "=" + channelType, cv);
    }

    public synchronized void updateMsg(String channelID, byte channelType, String clientMsgNo, long lastMsgSeq, int count) {
        String[] update = new String[2];
        update[0] = channelID;
        update[1] = String.valueOf(channelType);
        ContentValues cv = new ContentValues();
        try {
            cv.put(MSDBColumns.MSCoverMessageColumns.last_client_msg_no, clientMsgNo);
            cv.put(MSDBColumns.MSCoverMessageColumns.last_msg_seq, lastMsgSeq);
            cv.put(MSDBColumns.MSCoverMessageColumns.unread_count, count);
        } catch (Exception e) {
            MSLoggerUtils.getInstance().e(TAG, "updateMsg error");
        }
        MSIMApplication.getInstance().getDbHelper()
                .update(conversation, cv, MSDBColumns.MSCoverMessageColumns.channel_id + "=? and " + MSDBColumns.MSCoverMessageColumns.channel_type + "=?", update);
    }

    public MSConversationMsg queryWithChannel(String channelID, byte channelType) {
        String sql = "select " + conversation + ".*," + channelCols + "," + extraCols + " from " + conversation + " left join " + channel + " on " + conversation + ".channel_id=" + channel + ".channel_id and " + conversation + ".channel_type=" + channel + ".channel_type left join " + conversationExtra + " on " + conversation + ".channel_id=" + conversationExtra + ".channel_id and " + conversation + ".channel_type=" + conversationExtra + ".channel_type where " + conversation + ".channel_id=? and " + conversation + ".channel_type=? and " + conversation + ".is_deleted=0";
        Cursor cursor = MSIMApplication
                .getInstance()
                .getDbHelper()
                .rawQuery(sql, new Object[]{channelID, channelType});
        MSConversationMsg conversationMsg = null;
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                conversationMsg = serializeMsg(cursor);
            }
            cursor.close();
        }
        return conversationMsg;
    }

    public synchronized boolean deleteWithChannel(String channelID, byte channelType, int isDeleted) {
        String[] update = new String[2];
        update[0] = channelID;
        update[1] = String.valueOf(channelType);
        ContentValues cv = new ContentValues();
        try {
            cv.put(MSDBColumns.MSCoverMessageColumns.is_deleted, isDeleted);
        } catch (Exception e) {
            MSLoggerUtils.getInstance().e(TAG, "deleteWithChannel error");
        }

        boolean result = MSIMApplication.getInstance().getDbHelper()
                .update(conversation, cv, MSDBColumns.MSCoverMessageColumns.channel_id + "=? and " + MSDBColumns.MSCoverMessageColumns.channel_type + "=?", update);
        if (result) {
            ConversationManager.getInstance().setDeleteMsg(channelID, channelType);
        }
        return result;

    }

    public synchronized MSUIConversationMsg insertOrUpdateWithMsg(MSMsg msg, int unreadCount) {
        if (msg.channelID.equals(MSIMApplication.getInstance().getUid())) return null;
        MSConversationMsg msConversationMsg = new MSConversationMsg();
        if (msg.channelType == MSChannelType.COMMUNITY_TOPIC && !TextUtils.isEmpty(msg.channelID)) {
            if (msg.channelID.contains("@")) {
                String[] str = msg.channelID.split("@");
                msConversationMsg.parentChannelID = str[0];
                msConversationMsg.parentChannelType = MSChannelType.COMMUNITY;
            }
        }
        msConversationMsg.channelID = msg.channelID;
        msConversationMsg.channelType = msg.channelType;
//        msConversationMsg.localExtraMap = msg.localExtraMap;
        msConversationMsg.lastMsgTimestamp = msg.timestamp;
        msConversationMsg.lastClientMsgNO = msg.clientMsgNO;
        msConversationMsg.lastMsgSeq = msg.messageSeq;
        msConversationMsg.unreadCount = unreadCount;
        return insertOrUpdateWithConvMsg(msConversationMsg);// 插入消息列表数据表
    }

    public synchronized MSUIConversationMsg insertOrUpdateWithConvMsg(MSConversationMsg conversationMsg) {
        boolean result;
        MSConversationMsg lastMsg = queryWithChannelId(conversationMsg.channelID, conversationMsg.channelType);
        if (lastMsg == null || TextUtils.isEmpty(lastMsg.channelID)) {
            //如果服务器自增id为0则表示是本地数据|直接保存
            result = insert(conversationMsg);
        } else {
            conversationMsg.unreadCount = lastMsg.unreadCount + conversationMsg.unreadCount;
            result = update(conversationMsg);
        }
        if (result) {
            return getUIMsg(conversationMsg);
        }
        return null;
    }

    private synchronized boolean insert(MSConversationMsg msg) {
        ContentValues cv = new ContentValues();
        try {
            cv = MSSqlContentValues.getContentValuesWithCoverMsg(msg, false);
        } catch (Exception e) {
            MSLoggerUtils.getInstance().e(TAG, "insert error");
        }
        long result = -1;
        try {
            result = MSIMApplication.getInstance().getDbHelper()
                    .insert(conversation, cv);
        } catch (Exception ignored) {
        }
        return result > 0;
    }

    /**
     * 更新会话记录消息
     *
     * @param msg 会话消息
     * @return 修改结果
     */
    private synchronized boolean update(MSConversationMsg msg) {
        String[] update = new String[2];
        update[0] = msg.channelID;
        update[1] = String.valueOf(msg.channelType);
        ContentValues cv = new ContentValues();
        try {
            cv = MSSqlContentValues.getContentValuesWithCoverMsg(msg, false);
        } catch (Exception e) {
            MSLoggerUtils.getInstance().e(TAG, "update error");
        }
        return MSIMApplication.getInstance().getDbHelper()
                .update(conversation, cv, MSDBColumns.MSCoverMessageColumns.channel_id + "=? and " + MSDBColumns.MSCoverMessageColumns.channel_type + "=?", update);
    }

    private synchronized MSConversationMsg queryWithChannelId(String channelId, byte channelType) {
        MSConversationMsg msg = null;
        String selection = MSDBColumns.MSCoverMessageColumns.channel_id + " = ? and " + MSDBColumns.MSCoverMessageColumns.channel_type + "=?";
        String[] selectionArgs = new String[]{channelId, String.valueOf(channelType)};
        Cursor cursor = MSIMApplication
                .getInstance()
                .getDbHelper()
                .select(conversation, selection, selectionArgs,
                        null);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                msg = serializeMsg(cursor);
            }
            cursor.close();
        }
        return msg;
    }


    public synchronized boolean clearEmpty() {
        return MSIMApplication.getInstance().getDbHelper()
                .delete(conversation, null, null);
    }

    public MSConversationMsgExtra queryMsgExtraWithChannel(String channelID, byte channelType) {
        MSConversationMsgExtra msgExtra = null;
        String selection = "channel_id=? and channel_type=?";
        Cursor cursor = MSIMApplication
                .getInstance()
                .getDbHelper().select(conversationExtra, selection, new String[]{channelID, String.valueOf(channelType)}, null);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                msgExtra = serializeMsgExtra(cursor);
            }
            cursor.close();
        }
        return msgExtra;
    }

    private List<MSConversationMsgExtra> queryWithExtraChannelIds(List<String> channelIds) {
        List<MSConversationMsgExtra> list = new ArrayList<>();
        try (Cursor cursor = MSIMApplication.getInstance().getDbHelper().select(conversationExtra, "channel_id in (" + MSCursor.getPlaceholders(channelIds.size()) + ")", channelIds.toArray(new String[0]), null)) {
            if (cursor == null) {
                return list;
            }
            for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                MSConversationMsgExtra extra = serializeMsgExtra(cursor);
                list.add(extra);
            }
        }
        return list;
    }

    public synchronized boolean insertOrUpdateMsgExtra(MSConversationMsgExtra extra) {
        MSConversationMsgExtra msgExtra = queryMsgExtraWithChannel(extra.channelID, extra.channelType);
        boolean isAdd = true;
        if (msgExtra != null) {
            extra.version = msgExtra.version;
            isAdd = false;
        }
        ContentValues cv = MSSqlContentValues.getCVWithExtra(extra);
        if (isAdd) {
            return MSIMApplication.getInstance().getDbHelper().insert(conversationExtra, cv) > 0;
        }
        return MSIMApplication.getInstance().getDbHelper().update(conversationExtra, "channel_id='" + extra.channelID + "' and channel_type=" + extra.channelType, cv);
    }

    public synchronized void insertMsgExtras(List<MSConversationMsgExtra> list) {
        List<String> channelIds = new ArrayList<>();
        for (MSConversationMsgExtra extra : list) {
            boolean isAdd = true;
            for (String channelID : channelIds) {
                if (channelID.equals(extra.channelID)) {
                    isAdd = false;
                    break;
                }
            }
            if (isAdd) channelIds.add(extra.channelID);
        }
        List<ContentValues> insertCVList = new ArrayList<>();
        List<ContentValues> updateCVList = new ArrayList<>();
        List<MSConversationMsgExtra> existList = queryWithExtraChannelIds(channelIds);
        for (MSConversationMsgExtra extra : list) {
            boolean isAdd = true;
            for (MSConversationMsgExtra existExtra : existList) {
                if (existExtra.channelID.equals(extra.channelID) && existExtra.channelType == extra.channelType) {
                    isAdd = false;
                    break;
                }
            }
            if (isAdd) {
                insertCVList.add(MSSqlContentValues.getCVWithExtra(extra));
            } else {
                updateCVList.add(MSSqlContentValues.getCVWithExtra(extra));
            }
        }

        try {
            MSIMApplication.getInstance().getDbHelper().getDb().beginTransaction();
            if (MSCommonUtils.isNotEmpty(insertCVList)) {
                for (ContentValues cv : insertCVList) {
                    MSIMApplication.getInstance().getDbHelper()
                            .insert(conversationExtra, cv);
                }
            }
            if (MSCommonUtils.isNotEmpty(updateCVList)) {
                for (ContentValues cv : updateCVList) {
                    String[] sv = new String[2];
                    sv[0] = cv.getAsString("channel_id");
                    sv[1] = cv.getAsString("channel_type");
                    MSIMApplication.getInstance().getDbHelper()
                            .update(conversationExtra, cv, "channel_id=? and channel_type=?", sv);
                }
            }
            MSIMApplication.getInstance().getDbHelper().getDb().setTransactionSuccessful();
        } finally {
            MSIMApplication.getInstance().getDbHelper().getDb().endTransaction();
        }
        List<MSUIConversationMsg> uiMsgList = ConversationDbManager.getInstance().queryWithChannelIds(channelIds);
        MSIM.getInstance().getConversationManager().setOnRefreshMsg(uiMsgList,"saveMsgExtras");
    }

    public long queryMsgExtraMaxVersion() {
        long maxVersion = 0;
        String sql = "select max(version) version from " + conversationExtra;
        Cursor cursor = MSIMApplication
                .getInstance()
                .getDbHelper()
                .rawQuery(sql);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                maxVersion = MSCursor.readLong(cursor, "version");
            }
            cursor.close();
        }
        return maxVersion;
    }

    private synchronized MSConversationMsgExtra serializeMsgExtra(Cursor cursor) {
        MSConversationMsgExtra extra = new MSConversationMsgExtra();
        extra.channelID = MSCursor.readString(cursor, "channel_id");
        extra.channelType = (byte) MSCursor.readInt(cursor, "channel_type");
        extra.keepMessageSeq = MSCursor.readLong(cursor, "keep_message_seq");
        extra.keepOffsetY = MSCursor.readInt(cursor, "keep_offset_y");
        extra.draft = MSCursor.readString(cursor, "draft");
        extra.browseTo = MSCursor.readLong(cursor, "browse_to");
        extra.draftUpdatedAt = MSCursor.readLong(cursor, "draft_updated_at");
        extra.version = MSCursor.readLong(cursor, "version");
        if (cursor.getColumnIndex("extra_version") > 0) {
            extra.version = MSCursor.readLong(cursor, "extra_version");
        }
        return extra;
    }

    private synchronized MSConversationMsg serializeMsg(Cursor cursor) {
        MSConversationMsg msg = new MSConversationMsg();
        msg.channelID = MSCursor.readString(cursor, MSDBColumns.MSCoverMessageColumns.channel_id);
        msg.channelType = MSCursor.readByte(cursor, MSDBColumns.MSCoverMessageColumns.channel_type);
        msg.lastMsgTimestamp = MSCursor.readLong(cursor, MSDBColumns.MSCoverMessageColumns.last_msg_timestamp);
        msg.unreadCount = MSCursor.readInt(cursor, MSDBColumns.MSCoverMessageColumns.unread_count);
        msg.isDeleted = MSCursor.readInt(cursor, MSDBColumns.MSCoverMessageColumns.is_deleted);
        msg.version = MSCursor.readLong(cursor, MSDBColumns.MSCoverMessageColumns.version);
        msg.lastClientMsgNO = MSCursor.readString(cursor, MSDBColumns.MSCoverMessageColumns.last_client_msg_no);
        msg.lastMsgSeq = MSCursor.readLong(cursor, MSDBColumns.MSCoverMessageColumns.last_msg_seq);
        msg.parentChannelID = MSCursor.readString(cursor, MSDBColumns.MSCoverMessageColumns.parent_channel_id);
        msg.parentChannelType = MSCursor.readByte(cursor, MSDBColumns.MSCoverMessageColumns.parent_channel_type);
        String extra = MSCursor.readString(cursor, MSDBColumns.MSCoverMessageColumns.extra);
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
                MSLoggerUtils.getInstance().e(TAG, "serializeMsg error");
            }
            msg.localExtraMap = hashMap;
        }
        msg.msgExtra = serializeMsgExtra(cursor);
        return msg;
    }

    public MSUIConversationMsg getUIMsg(MSConversationMsg conversationMsg) {
        MSUIConversationMsg msg = new MSUIConversationMsg();
        msg.lastMsgSeq = conversationMsg.lastMsgSeq;
        msg.clientMsgNo = conversationMsg.lastClientMsgNO;
        msg.unreadCount = conversationMsg.unreadCount;
        msg.lastMsgTimestamp = conversationMsg.lastMsgTimestamp;
        msg.channelID = conversationMsg.channelID;
        msg.channelType = conversationMsg.channelType;
        msg.isDeleted = conversationMsg.isDeleted;
        msg.parentChannelID = conversationMsg.parentChannelID;
        msg.parentChannelType = conversationMsg.parentChannelType;
        msg.setRemoteMsgExtra(conversationMsg.msgExtra);
        return msg;
    }
}
