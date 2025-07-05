package com.mushanyux.mushanim.db;

import static com.mushanyux.mushanim.db.MSDBColumns.TABLE.channel;
import static com.mushanyux.mushanim.db.MSDBColumns.TABLE.channelMembers;

import android.content.ContentValues;
import android.database.Cursor;
import android.text.TextUtils;

import com.mushanyux.mushanim.MSIMApplication;
import com.mushanyux.mushanim.entity.MSChannel;
import com.mushanyux.mushanim.entity.MSChannelSearchResult;
import com.mushanyux.mushanim.utils.MSCommonUtils;
import com.mushanyux.mushanim.utils.MSLoggerUtils;

import java.util.ArrayList;
import java.util.List;

public class ChannelDBManager {
    private static final String TAG = "ChannelDBManager";
    private ChannelDBManager() {
    }
    private static class ChannelDBManagerBinder {
        static final ChannelDBManager channelDBManager = new ChannelDBManager();
    }
    public static ChannelDBManager getInstance() {
        return ChannelDBManagerBinder.channelDBManager;
    }

    public List<MSChannel> queryWithChannelIds(List<String> channelIDs) {
        List<MSChannel> list = new ArrayList<>();
        if (MSIMApplication
                .getInstance()
                .getDbHelper() == null) {
            return list;
        }
        try (Cursor cursor = MSIMApplication
                .getInstance()
                .getDbHelper()
                .select(channel, "channel_id in (" + MSCursor.getPlaceholders(channelIDs.size()) + ")", channelIDs.toArray(new String[0]), null)) {
            if (cursor == null) {
                return list;
            }
            for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                MSChannel channel = serializableChannel(cursor);
                list.add(channel);
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    public List<MSChannel> queryWithChannelIdsAndChannelType(List<String> channelIDs, byte channelType) {
        List<MSChannel> list = new ArrayList<>();
        if (MSIMApplication
                .getInstance()
                .getDbHelper() == null) {
            return list;
        }
        List<String> args = new ArrayList<>(channelIDs);
        args.add(String.valueOf(channelType));
        try (Cursor cursor = MSIMApplication
                .getInstance()
                .getDbHelper()
                .select(channel, "channel_id in (" + MSCursor.getPlaceholders(channelIDs.size()) + ") and channel_type=?", args.toArray(new String[0]), null)) {
            if (cursor == null) {
                return list;
            }
            for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                MSChannel channel = serializableChannel(cursor);
                list.add(channel);
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    public synchronized MSChannel query(String channelId, int channelType) {
        String selection = MSDBColumns.MSChannelColumns.channel_id + "=? and " + MSDBColumns.MSChannelColumns.channel_type + "=?";
        String[] selectionArgs = new String[2];
        selectionArgs[0] = channelId;
        selectionArgs[1] = String.valueOf(channelType);
        Cursor cursor = null;
        MSChannel msChannel = null;
        if (MSIMApplication
                .getInstance()
                .getDbHelper() == null) {
            return null;
        }
        try {
            cursor = MSIMApplication
                    .getInstance()
                    .getDbHelper()
                    .select(channel, selection, selectionArgs,
                            null);
            if (cursor != null) {
                if (cursor.getCount() > 0) {
                    cursor.moveToNext();
                    msChannel = serializableChannel(cursor);
                }
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return msChannel;
    }

    private boolean isExist(String channelId, int channelType) {
        String selection = MSDBColumns.MSChannelColumns.channel_id + "=? and " + MSDBColumns.MSChannelColumns.channel_type + "=?";
        String[] selectionArgs = new String[2];
        selectionArgs[0] = channelId;
        selectionArgs[1] = String.valueOf(channelType);
        Cursor cursor = null;
        boolean isExist = false;
        try {
            if (MSIMApplication
                    .getInstance()
                    .getDbHelper() == null) {
                return false;
            }
            cursor = MSIMApplication
                    .getInstance()
                    .getDbHelper()
                    .select(channel, selection, selectionArgs,
                            null);
            if (cursor != null && cursor.moveToNext()) {
                isExist = true;
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return isExist;
    }

    public synchronized void insertChannels(List<MSChannel> list) {
        List<ContentValues> newCVList = new ArrayList<>();
        for (MSChannel channel : list) {
            ContentValues cv = MSSqlContentValues.getContentValuesWithChannel(channel);
            newCVList.add(cv);
        }
        try {
            if (MSIMApplication.getInstance().getDbHelper() == null) {
                return;
            }
            MSIMApplication.getInstance().getDbHelper().getDb()
                    .beginTransaction();
            for (ContentValues cv : newCVList) {
                MSIMApplication.getInstance().getDbHelper()
                        .insert(channel, cv);
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

    public synchronized void insertOrUpdate(MSChannel channel) {
        if (isExist(channel.channelID, channel.channelType)) {
            update(channel);
        } else {
            insert(channel);
        }
    }

    private synchronized void insert(MSChannel msChannel) {
        ContentValues cv = new ContentValues();
        try {
            cv = MSSqlContentValues.getContentValuesWithChannel(msChannel);
        } catch (Exception e) {
            MSLoggerUtils.getInstance().e(TAG, "insert channel error");
        }
        if (MSIMApplication.getInstance().getDbHelper() == null) {
            return;
        }
        MSIMApplication.getInstance().getDbHelper()
                .insert(channel, cv);
    }

    public synchronized void update(MSChannel msChannel) {
        String[] update = new String[2];
        update[0] = msChannel.channelID;
        update[1] = String.valueOf(msChannel.channelType);
        ContentValues cv = new ContentValues();
        try {
            cv = MSSqlContentValues.getContentValuesWithChannel(msChannel);
        } catch (Exception e) {
            MSLoggerUtils.getInstance().e(TAG, "update channel error");
        }
        if (MSIMApplication.getInstance().getDbHelper() == null) {
            return;
        }
        MSIMApplication.getInstance().getDbHelper()
                .update(channel, cv, MSDBColumns.MSChannelColumns.channel_id + "=? and " + MSDBColumns.MSChannelColumns.channel_type + "=?", update);

    }

    /**
     * 查询频道
     *
     * @param channelType 频道类型
     * @param follow      是否关注 好友或陌生人
     * @param status      状态 正常或黑名单
     * @return List<MSChannel>
     */
    public synchronized List<MSChannel> queryWithFollowAndStatus(byte channelType, int follow, int status) {
        String[] args = new String[3];
        args[0] = String.valueOf(channelType);
        args[1] = String.valueOf(follow);
        args[2] = String.valueOf(status);
        String selection = MSDBColumns.MSChannelColumns.channel_type + "=? and " + MSDBColumns.MSChannelColumns.follow + "=? and " + MSDBColumns.MSChannelColumns.status + "=? and is_deleted=0";
        List<MSChannel> channels = new ArrayList<>();
        if (MSIMApplication
                .getInstance()
                .getDbHelper() != null) {
            try (Cursor cursor = MSIMApplication
                    .getInstance()
                    .getDbHelper().select(channel, selection, args, null)) {
                if (cursor == null) {
                    return channels;
                }
                for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                    channels.add(serializableChannel(cursor));
                }
            }
        }
        return channels;
    }

    /**
     * 查下指定频道类型和频道状态的频道
     *
     * @param channelType 频道类型
     * @param status      状态[sdk不维护状态]
     * @return List<MSChannel>
     */
    public synchronized List<MSChannel> queryWithStatus(byte channelType, int status) {
        String[] args = new String[2];
        args[0] = String.valueOf(channelType);
        args[1] = String.valueOf(status);
        String selection = MSDBColumns.MSChannelColumns.channel_type + "=? and " + MSDBColumns.MSChannelColumns.status + "=?";
        List<MSChannel> channels = new ArrayList<>();
        if (MSIMApplication.getInstance().getDbHelper() == null) {
            return channels;
        }
        try (Cursor cursor = MSIMApplication
                .getInstance()
                .getDbHelper().select(channel, selection, args, null)) {
            if (cursor == null) {
                return channels;
            }
            for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                channels.add(serializableChannel(cursor));
            }
        }
        return channels;
    }

    public synchronized List<MSChannelSearchResult> search(String searchKey) {
        List<MSChannelSearchResult> list = new ArrayList<>();
        Object[] args = new Object[4];
        args[0] = "%" + searchKey + "%";
        args[1] = "%" + searchKey + "%";
        args[2] = "%" + searchKey + "%";
        args[3] = "%" + searchKey + "%";
        String sql = " select t.*,cm.member_name,cm.member_remark from (\n" +
                " select " + channel + ".*,max(" + channelMembers + ".id) mid from " + channel + "," + channelMembers + " " +
                "where " + channel + ".channel_id=" + channelMembers + ".channel_id and " + channel + ".channel_type=" + channelMembers + ".channel_type" +
                " and (" + channel + ".channel_name like ? or " + channel + ".channel_remark" +
                " like ? or " + channelMembers + ".member_name like ? or " + channelMembers + ".member_remark like ?)\n" +
                " group by " + channel + ".channel_id," + channel + ".channel_type\n" +
                " ) t," + channelMembers + " cm where t.channel_id=cm.channel_id and t.channel_type=cm.channel_type and t.mid=cm.id";
        Cursor cursor = MSIMApplication.getInstance().getDbHelper().rawQuery(sql, args);
        if (cursor == null) {
            return list;
        }
        for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
            String member_name = MSCursor.readString(cursor, "member_name");
            String member_remark = MSCursor.readString(cursor, "member_remark");
            MSChannel channel = serializableChannel(cursor);
            MSChannelSearchResult result = new MSChannelSearchResult();
            result.msChannel = channel;
            if (!TextUtils.isEmpty(member_remark)) {
                //优先显示备注名称
                if (member_remark.toUpperCase().contains(searchKey.toUpperCase())) {
                    result.containMemberName = member_remark;
                }
            }
            if (TextUtils.isEmpty(result.containMemberName)) {
                if (!TextUtils.isEmpty(member_name)) {
                    if (member_name.toUpperCase().contains(searchKey.toUpperCase())) {
                        result.containMemberName = member_name;
                    }
                }
            }
            list.add(result);
        }
        cursor.close();
        return list;
    }

    public synchronized List<MSChannel> searchWithChannelType(String searchKey, byte channelType) {
        List<MSChannel> list = new ArrayList<>();
        Object[] args = new Object[3];
        args[0] = "%" + searchKey + "%";
        args[1] = "%" + searchKey + "%";
        args[2] = channelType;
        String sql = "select * from " + channel + " where (" + MSDBColumns.MSChannelColumns.channel_name + " LIKE ? or " + MSDBColumns.MSChannelColumns.channel_remark + " LIKE ?) and " + MSDBColumns.MSChannelColumns.channel_type + "=?";
        try (Cursor cursor = MSIMApplication.getInstance().getDbHelper().rawQuery(sql, args)) {
            if (cursor == null) {
                return list;
            }
            for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                list.add(serializableChannel(cursor));
            }
        }
        return list;
    }

    public synchronized List<MSChannel> searchWithChannelTypeAndFollow(String searchKey, byte channelType, int follow) {
        List<MSChannel> list = new ArrayList<>();
        Object[] args = new Object[4];
        args[0] = "%" + searchKey + "%";
        args[1] = "%" + searchKey + "%";
        args[2] = channelType;
        args[3] = follow;
        String sql = "select * from " + channel + " where (" + MSDBColumns.MSChannelColumns.channel_name + " LIKE ? or " + MSDBColumns.MSChannelColumns.channel_remark + " LIKE ?) and " + MSDBColumns.MSChannelColumns.channel_type + "=? and " + MSDBColumns.MSChannelColumns.follow + "=?";
        try (Cursor cursor = MSIMApplication.getInstance().getDbHelper().rawQuery(sql, args)) {
            if (cursor == null) {
                return list;
            }
            for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                list.add(serializableChannel(cursor));
            }
        }
        return list;
    }

    public synchronized List<MSChannel> queryWithChannelTypeAndFollow(byte channelType, int follow) {
        String[] args = new String[2];
        args[0] = String.valueOf(channelType);
        args[1] = String.valueOf(follow);
        String selection = MSDBColumns.MSChannelColumns.channel_type + "=? and " + MSDBColumns.MSChannelColumns.follow + "=?";
        List<MSChannel> channels = new ArrayList<>();
        try (Cursor cursor = MSIMApplication
                .getInstance()
                .getDbHelper().select(channel, selection, args, null)) {
            if (cursor == null) {
                return channels;
            }
            for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                channels.add(serializableChannel(cursor));
            }
        }
        return channels;
    }

    public synchronized void updateWithField(String channelID, byte channelType, String field, String value) {
        String[] updateKey = new String[]{field};
        String[] updateValue = new String[]{value};
        String where = MSDBColumns.MSChannelColumns.channel_id + "=? and " + MSDBColumns.MSChannelColumns.channel_type + "=?";
        String[] whereValue = new String[2];
        whereValue[0] = channelID;
        whereValue[1] = String.valueOf(channelType);
        MSIMApplication.getInstance().getDbHelper()
                .update(channel, updateKey, updateValue, where, whereValue);
    }

    public MSChannel serializableChannel(Cursor cursor) {
        MSChannel channel = new MSChannel();
        channel.id = MSCursor.readLong(cursor, MSDBColumns.MSChannelColumns.id);
        channel.channelID = MSCursor.readString(cursor, MSDBColumns.MSChannelColumns.channel_id);
        channel.channelType = MSCursor.readByte(cursor, MSDBColumns.MSChannelColumns.channel_type);
        channel.channelName = MSCursor.readString(cursor, MSDBColumns.MSChannelColumns.channel_name);
        channel.channelRemark = MSCursor.readString(cursor, MSDBColumns.MSChannelColumns.channel_remark);
        channel.showNick = MSCursor.readInt(cursor, MSDBColumns.MSChannelColumns.show_nick);
        channel.top = MSCursor.readInt(cursor, MSDBColumns.MSChannelColumns.top);
        channel.mute = MSCursor.readInt(cursor, MSDBColumns.MSChannelColumns.mute);
        channel.isDeleted = MSCursor.readInt(cursor, MSDBColumns.MSChannelColumns.is_deleted);
        channel.forbidden = MSCursor.readInt(cursor, MSDBColumns.MSChannelColumns.forbidden);
        channel.status = MSCursor.readInt(cursor, MSDBColumns.MSChannelColumns.status);
        channel.follow = MSCursor.readInt(cursor, MSDBColumns.MSChannelColumns.follow);
        channel.invite = MSCursor.readInt(cursor, MSDBColumns.MSChannelColumns.invite);
        channel.version = MSCursor.readLong(cursor, MSDBColumns.MSChannelColumns.version);
        channel.createdAt = MSCursor.readString(cursor, MSDBColumns.MSChannelColumns.created_at);
        channel.updatedAt = MSCursor.readString(cursor, MSDBColumns.MSChannelColumns.updated_at);
        channel.avatar = MSCursor.readString(cursor, MSDBColumns.MSChannelColumns.avatar);
        channel.online = MSCursor.readInt(cursor, MSDBColumns.MSChannelColumns.online);
        channel.lastOffline = MSCursor.readLong(cursor, MSDBColumns.MSChannelColumns.last_offline);
        channel.category = MSCursor.readString(cursor, MSDBColumns.MSChannelColumns.category);
        channel.receipt = MSCursor.readInt(cursor, MSDBColumns.MSChannelColumns.receipt);
        channel.robot = MSCursor.readInt(cursor, MSDBColumns.MSChannelColumns.robot);
        channel.username = MSCursor.readString(cursor, MSDBColumns.MSChannelColumns.username);
        channel.avatarCacheKey = MSCursor.readString(cursor, MSDBColumns.MSChannelColumns.avatar_cache_key);
        channel.flame = MSCursor.readInt(cursor, MSDBColumns.MSChannelColumns.flame);
        channel.flameSecond = MSCursor.readInt(cursor, MSDBColumns.MSChannelColumns.flame_second);
        channel.deviceFlag = MSCursor.readInt(cursor, MSDBColumns.MSChannelColumns.device_flag);
        channel.parentChannelID = MSCursor.readString(cursor, MSDBColumns.MSChannelColumns.parent_channel_id);
        channel.parentChannelType = MSCursor.readByte(cursor, MSDBColumns.MSChannelColumns.parent_channel_type);
        String extra = MSCursor.readString(cursor, MSDBColumns.MSChannelColumns.localExtra);
        String remoteExtra = MSCursor.readString(cursor, MSDBColumns.MSChannelColumns.remote_extra);
        channel.localExtra = MSCommonUtils.str2HashMap(extra);
        channel.remoteExtraMap = MSCommonUtils.str2HashMap(remoteExtra);
        return channel;
    }
}
