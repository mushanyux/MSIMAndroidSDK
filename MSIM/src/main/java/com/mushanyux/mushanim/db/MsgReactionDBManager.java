package com.mushanyux.mushanim.db;

import static com.mushanyux.mushanim.db.MSDBColumns.TABLE.messageReaction;

import android.content.ContentValues;
import android.database.Cursor;
import android.text.TextUtils;

import com.mushanyux.mushanim.MSIM;
import com.mushanyux.mushanim.MSIMApplication;
import com.mushanyux.mushanim.entity.MSChannel;
import com.mushanyux.mushanim.entity.MSChannelType;
import com.mushanyux.mushanim.entity.MSMsgReaction;
import com.mushanyux.mushanim.utils.MSCommonUtils;

import java.util.ArrayList;
import java.util.List;

class MsgReactionDBManager {
    private MsgReactionDBManager() {
    }

    private static class MessageReactionDBManagerBinder {
        final static MsgReactionDBManager manager = new MsgReactionDBManager();
    }

    public static MsgReactionDBManager getInstance() {
        return MessageReactionDBManagerBinder.manager;
    }

    public void insertReactions(List<MSMsgReaction> list) {
        if (MSCommonUtils.isEmpty(list)) return;
        List<ContentValues> insertCVs = new ArrayList<>();
        for (MSMsgReaction reaction : list) {
            insertCVs.add(MSSqlContentValues.getContentValuesWithMsgReaction(reaction));
        }
        try {
            MSIMApplication.getInstance().getDbHelper().getDb()
                    .beginTransaction();
            if (!insertCVs.isEmpty()) {
                for (ContentValues cv : insertCVs) {
                    MSIMApplication.getInstance().getDbHelper().insert(messageReaction, cv);
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
    }

    public List<MSMsgReaction> queryWithMessageId(String messageID) {
        List<MSMsgReaction> list = new ArrayList<>();
        String sql = "select * from " + messageReaction + " where message_id=? and is_deleted=0 ORDER BY created_at desc";
        try (Cursor cursor = MSIMApplication.getInstance().getDbHelper().rawQuery(sql, new Object[]{messageID})) {
            if (cursor == null) {
                return list;
            }
            for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                MSMsgReaction reaction = serializeReaction(cursor);
                MSChannel channel = MSIM.getInstance().getChannelManager().getChannel(reaction.uid, MSChannelType.PERSONAL);
                if (channel != null) {
                    String showName = TextUtils.isEmpty(channel.channelRemark) ? channel.channelName : channel.channelRemark;
                    if (!TextUtils.isEmpty(showName))
                        reaction.name = showName;
                }
                list.add(reaction);
            }
        }
        return list;
    }

    public List<MSMsgReaction> queryWithMessageIds(List<String> messageIds) {
        List<MSMsgReaction> list = new ArrayList<>();
        List<String> channelIds = new ArrayList<>();
        try (Cursor cursor = MSIMApplication
                .getInstance()
                .getDbHelper().select(messageReaction, "message_id in (" + MSCursor.getPlaceholders(messageIds.size()) + ")", messageIds.toArray(new String[0]), "created_at desc")) {
            if (cursor == null) {
                return list;
            }
            for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                MSMsgReaction msgReaction = serializeReaction(cursor);
                channelIds.add(msgReaction.uid);
                list.add(msgReaction);
            }
        } catch (Exception ignored) {
        }
        //查询用户备注
        List<MSChannel> channelList = ChannelDBManager.getInstance().queryWithChannelIdsAndChannelType(channelIds, MSChannelType.PERSONAL);
        for (int i = 0, size = list.size(); i < size; i++) {
            for (int j = 0, len = channelList.size(); j < len; j++) {
                if (channelList.get(j).channelID.equals(list.get(i).uid)) {
                    list.get(i).name = TextUtils.isEmpty(channelList.get(j).channelRemark) ? channelList.get(j).channelName : channelList.get(j).channelRemark;
                }
            }
        }
        return list;
    }

    public long queryMaxSeqWithChannel(String channelID, byte channelType) {
        int maxSeq = 0;
        String sql = "select max(seq) seq from " + messageReaction
                + " where channel_id=? and channel_type=? limit 0, 1";
        try {
            if (MSIMApplication.getInstance().getDbHelper() != null) {
                Cursor cursor = MSIMApplication
                        .getInstance()
                        .getDbHelper()
                        .rawQuery(sql, new Object[]{channelID, channelType});
                if (cursor != null) {
                    if (cursor.moveToFirst()) {
                        maxSeq = MSCursor.readInt(cursor, "seq");
                    }
                    cursor.close();
                }
            }
        } catch (Exception ignored) {
        }
        return maxSeq;
    }

    private MSMsgReaction serializeReaction(Cursor cursor) {
        MSMsgReaction reaction = new MSMsgReaction();
        reaction.channelID = MSCursor.readString(cursor, "channel_id");
        reaction.channelType = (byte) MSCursor.readInt(cursor, "channel_type");
        reaction.uid = MSCursor.readString(cursor, "uid");
        reaction.name = MSCursor.readString(cursor, "name");
        reaction.messageID = MSCursor.readString(cursor, "message_id");
        reaction.createdAt = MSCursor.readString(cursor, "created_at");
        reaction.seq = MSCursor.readLong(cursor, "seq");
        reaction.emoji = MSCursor.readString(cursor, "emoji");
        reaction.isDeleted = MSCursor.readInt(cursor, "is_deleted");
        return reaction;
    }
}
