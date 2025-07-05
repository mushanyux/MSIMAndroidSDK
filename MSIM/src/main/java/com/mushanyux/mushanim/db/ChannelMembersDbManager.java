package com.mushanyux.mushanim.db;

import static com.mushanyux.mushanim.db.MSDBColumns.TABLE.channel;
import static com.mushanyux.mushanim.db.MSDBColumns.TABLE.channelMembers;

import android.content.ContentValues;
import android.database.Cursor;
import android.text.TextUtils;

import com.mushanyux.mushanim.MSIMApplication;
import com.mushanyux.mushanim.entity.MSChannelMember;
import com.mushanyux.mushanim.manager.ChannelMembersManager;
import com.mushanyux.mushanim.utils.MSCommonUtils;
import com.mushanyux.mushanim.utils.MSLoggerUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

public class ChannelMembersDbManager {
    private static final String TAG = "ChannelMembersDbManager";
    final String channelCols = channel + ".channel_remark," + channel + ".channel_name," + channel + ".avatar," + channel + ".avatar_cache_key";

    private ChannelMembersDbManager() {
    }

    private static class ChannelMembersManagerBinder {
        private final static ChannelMembersDbManager channelMembersManager = new ChannelMembersDbManager();
    }

    public static ChannelMembersDbManager getInstance() {
        return ChannelMembersManagerBinder.channelMembersManager;
    }

    public synchronized List<MSChannelMember> search(String channelId, byte channelType, String keyword, int page, int size) {
        int queryPage = (page - 1) * size;
        Object[] args = new Object[6];
        args[0] = channelId;
        args[1] = channelType;
        args[2] = "%" + keyword + "%";
        args[3] = "%" + keyword + "%";
        args[4] = "%" + keyword + "%";
        args[5] = "%" + keyword + "%";
        String sql = "select " + channelMembers + ".*," + channelCols + " from " + channelMembers + " LEFT JOIN " + channel + " on " + channelMembers + ".member_uid=" + channel + ".channel_id and " + channel + ".channel_type=1 where " + channelMembers + ".channel_id=? and " + channelMembers + ".channel_type=? and " + channelMembers + ".is_deleted=0 and " + channelMembers + ".status=1 and (member_name like ? or member_remark like ? or channel_name like ? or channel_remark like ?) order by " + channelMembers + ".role=1 desc," + channelMembers + ".role=2 desc," + channelMembers + "." + MSDBColumns.MSChannelMembersColumns.created_at + " asc limit " + queryPage + "," + size;
        Cursor cursor = MSIMApplication
                .getInstance()
                .getDbHelper().rawQuery(sql, args);
        List<MSChannelMember> list = new ArrayList<>();
        if (cursor == null) {
            return list;
        }
        for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
            list.add(serializableChannelMember(cursor));
        }
        cursor.close();
        return list;
    }

    public synchronized List<MSChannelMember> queryWithPage(String channelId, byte channelType, int page, int size) {
        int queryPage = (page - 1) * size;
        Object[] args = new Object[2];
        args[0] = channelId;
        args[1] = channelType;
        String sql = "select " + channelMembers + ".*," + channelCols + " from " + channelMembers + " LEFT JOIN " + channel + " on " + channelMembers + ".member_uid=" + channel + ".channel_id and " + channel + ".channel_type=1 where " + channelMembers + ".channel_id=? and " + channelMembers + ".channel_type=? and " + channelMembers + ".is_deleted=0 and " + channelMembers + ".status=1 order by " + channelMembers + ".role=1 desc," + channelMembers + ".role=2 desc," + channelMembers + "." + MSDBColumns.MSChannelMembersColumns.created_at + " asc limit " + queryPage + "," + size;
        Cursor cursor = MSIMApplication
                .getInstance()
                .getDbHelper().rawQuery(sql, args);
        List<MSChannelMember> list = new ArrayList<>();
        if (cursor == null) {
            return list;
        }
        for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
            list.add(serializableChannelMember(cursor));
        }
        cursor.close();
        return list;
    }

    /**
     * 查询某个频道的所有成员
     *
     * @param channelId 频道ID
     * @return List<MSChannelMember>
     */
    public synchronized List<MSChannelMember> query(String channelId, byte channelType) {
        Object[] args = new Object[2];
        args[0] = channelId;
        args[1] = channelType;
        String sql = "select " + channelMembers + ".*," + channelCols + " from " + channelMembers + " LEFT JOIN " + channel + " on " + channelMembers + ".member_uid=" + channel + ".channel_id and " + channel + ".channel_type=1 where " + channelMembers + ".channel_id=? and " + channelMembers + ".channel_type=? and " + channelMembers + ".is_deleted=0 and " + channelMembers + ".status=1 order by " + channelMembers + ".role=1 desc," + channelMembers + ".role=2 desc," + channelMembers + "." + MSDBColumns.MSChannelMembersColumns.created_at + " asc";
        Cursor cursor = MSIMApplication
                .getInstance()
                .getDbHelper().rawQuery(sql, args);
        List<MSChannelMember> list = new ArrayList<>();
        if (cursor == null) {
            return list;
        }
        for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
            list.add(serializableChannelMember(cursor));
        }
        cursor.close();
        return list;
    }

    public synchronized List<MSChannelMember> queryDeleted(String channelId, byte channelType) {
        Object[] args = new Object[2];
        args[0] = channelId;
        args[1] = channelType;
        String sql = "select " + channelMembers + ".*," + channelCols + " from " + channelMembers + " LEFT JOIN " + channel + " on " + channelMembers + ".member_uid=" + channel + ".channel_id and " + channel + ".channel_type=1 where " + channelMembers + ".channel_id=? and " + channelMembers + ".channel_type=? and " + channelMembers + ".is_deleted=1 and " + channelMembers + ".status=1 order by " + channelMembers + ".role=1 desc," + channelMembers + ".role=2 desc," + channelMembers + "." + MSDBColumns.MSChannelMembersColumns.created_at + " asc";
        Cursor cursor = MSIMApplication
                .getInstance()
                .getDbHelper().rawQuery(sql, args);
        List<MSChannelMember> list = new ArrayList<>();
        if (cursor == null) {
            return list;
        }
        for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
            list.add(serializableChannelMember(cursor));
        }
        cursor.close();
        return list;
    }

    public synchronized boolean isExist(String channelId, byte channelType, String uid) {
        boolean isExist = false;
        Object[] args = new Object[3];
        args[0] = channelId;
        args[1] = channelType;
        args[2] = uid;
        String sql = "select " + channelMembers + ".*," + channelCols + " from " + channelMembers + " left join " + channel + " on " + channelMembers + ".member_uid = " + channel + ".channel_id AND " + channel + ".channel_type=1 where (" + channelMembers + "." + MSDBColumns.MSChannelMembersColumns.channel_id + "=? and " + channelMembers + "." + MSDBColumns.MSChannelMembersColumns.channel_type + "=? and " + channelMembers + "." + MSDBColumns.MSChannelMembersColumns.member_uid + "=?)";
        try (Cursor cursor = MSIMApplication
                .getInstance()
                .getDbHelper().rawQuery(sql, args)) {

            if (cursor != null && cursor.moveToLast()) {
                isExist = true;
            }
        }
        return isExist;
    }

    public List<MSChannelMember> queryWithUIDs(String channelID, byte channelType, List<String> uidList) {
        List<String> args = new ArrayList<>();
        args.add(channelID);
        args.add(String.valueOf(channelType));
        args.addAll(uidList);
        uidList.add(String.valueOf(channelType));
        Cursor cursor = MSIMApplication
                .getInstance()
                .getDbHelper().select(channelMembers, "channel_id =? and channel_type=? and member_uid in (" + MSCursor.getPlaceholders(uidList.size()) + ")", args.toArray(new String[0]), null);
        List<MSChannelMember> list = new ArrayList<>();
        if (cursor == null) {
            return list;
        }
        for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
            list.add(serializableChannelMember(cursor));
        }
        cursor.close();
        return list;
    }

    /**
     * 查询单个频道成员
     *
     * @param channelId 频道ID
     * @param uid       用户ID
     */
    public synchronized MSChannelMember query(String channelId, byte channelType, String uid) {
        MSChannelMember msChannelMember = null;
        Object[] args = new Object[3];
        args[0] = channelId;
        args[1] = channelType;
        args[2] = uid;
        String sql = "select " + channelMembers + ".*," + channelCols + " from " + channelMembers + " left join " + channel + " on " + channelMembers + ".member_uid = " + channel + ".channel_id AND " + channel + ".channel_type=1 where (" + channelMembers + "." + MSDBColumns.MSChannelMembersColumns.channel_id + "=? and " + channelMembers + "." + MSDBColumns.MSChannelMembersColumns.channel_type + "=? and " + channelMembers + "." + MSDBColumns.MSChannelMembersColumns.member_uid + "=?)";
        try (Cursor cursor = MSIMApplication
                .getInstance()
                .getDbHelper().rawQuery(sql, args)) {
            if (cursor == null) {
                return null;
            }
            if (cursor.moveToLast()) {
                msChannelMember = serializableChannelMember(cursor);
            }
        }
        return msChannelMember;
    }

    public synchronized void insert(MSChannelMember channelMember) {
        if (TextUtils.isEmpty(channelMember.channelID) || TextUtils.isEmpty(channelMember.memberUID))
            return;
        ContentValues cv = new ContentValues();
        try {
            cv = MSSqlContentValues.getContentValuesWithChannelMember(channelMember);
        } catch (Exception e) {
            MSLoggerUtils.getInstance().e(TAG, "insert error");
        }
        MSIMApplication.getInstance().getDbHelper()
                .insert(channelMembers, cv);
    }

    /**
     * 批量插入频道成员
     *
     * @param list List<MSChannelMember>
     */
    public void insertMembers(List<MSChannelMember> list) {
        List<ContentValues> newCVList = new ArrayList<>();
        for (MSChannelMember member : list) {
            ContentValues cv = MSSqlContentValues.getContentValuesWithChannelMember(member);
            newCVList.add(cv);
        }
        try {
            MSIMApplication.getInstance().getDbHelper().getDb()
                    .beginTransaction();
            if (MSCommonUtils.isNotEmpty(newCVList)) {
                for (ContentValues cv : newCVList) {
                    MSIMApplication.getInstance().getDbHelper().insert(channelMembers, cv);
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

    public void insertMembers(List<MSChannelMember> allMemberList, List<MSChannelMember> existList) {
        List<ContentValues> insertCVList = new ArrayList<>();
        for (MSChannelMember channelMember : allMemberList) {
            insertCVList.add(MSSqlContentValues.getContentValuesWithChannelMember(channelMember));
        }
        MSIMApplication.getInstance().getDbHelper().getDb()
                .beginTransaction();
        try {
            if (MSCommonUtils.isNotEmpty(insertCVList)) {
                for (ContentValues cv : insertCVList) {
                    MSIMApplication.getInstance().getDbHelper().insert(channelMembers, cv);
                }
            }
            MSIMApplication.getInstance().getDbHelper().getDb().setTransactionSuccessful();
        } finally {
            if (MSIMApplication.getInstance().getDbHelper().getDb().inTransaction()) {
                MSIMApplication.getInstance().getDbHelper().getDb()
                        .endTransaction();
            }
        }
    }

    public void insertOrUpdate(MSChannelMember channelMember) {
        if (channelMember == null) return;
        if (isExist(channelMember.channelID, channelMember.channelType, channelMember.memberUID)) {
            update(channelMember);
        } else {
            insert(channelMember);
        }
    }

    /**
     * 修改某个频道的某个成员信息
     *
     * @param channelMember 成员
     */
    public synchronized void update(MSChannelMember channelMember) {
        String[] update = new String[3];
        update[0] = channelMember.channelID;
        update[1] = String.valueOf(channelMember.channelType);
        update[2] = channelMember.memberUID;
        ContentValues cv = new ContentValues();
        try {
            cv = MSSqlContentValues.getContentValuesWithChannelMember(channelMember);
        } catch (Exception e) {
            MSLoggerUtils.getInstance().e(TAG, "update error");
        }
        MSIMApplication.getInstance().getDbHelper()
                .update(channelMembers, cv, MSDBColumns.MSChannelMembersColumns.channel_id + "=? and " + MSDBColumns.MSChannelMembersColumns.channel_type + "=? and " + MSDBColumns.MSChannelMembersColumns.member_uid + "=?", update);
    }

    /**
     * 根据字段修改频道成员
     *
     * @param channelID   频道ID
     * @param channelType 频道类型
     * @param uid         用户ID
     * @param field       字段
     * @param value       值
     */
    public synchronized boolean updateWithField(String channelID, byte channelType, String uid, String field, String value) {
        String[] updateKey = new String[]{field};
        String[] updateValue = new String[]{value};
        String where = MSDBColumns.MSChannelMembersColumns.channel_id + "=? and " + MSDBColumns.MSChannelMembersColumns.channel_type + "=? and " + MSDBColumns.MSChannelMembersColumns.member_uid + "=?";
        String[] whereValue = new String[3];
        whereValue[0] = channelID;
        whereValue[1] = String.valueOf(channelType);
        whereValue[2] = uid;
        int row = MSIMApplication.getInstance().getDbHelper()
                .update(channelMembers, updateKey, updateValue, where, whereValue);
        if (row > 0) {
            MSChannelMember channelMember = query(channelID, channelType, uid);
            if (channelMember != null)
                //刷新频道成员信息
                ChannelMembersManager.getInstance().setRefreshChannelMember(channelMember, true);
        }
        return row > 0;
    }

    public void deleteWithChannel(String channelID, byte channelType) {
        String selection = "channel_id=? and channel_type=?";
        String[] selectionArgs = new String[2];
        selectionArgs[0] = channelID;
        selectionArgs[1] = String.valueOf(channelType);
        MSIMApplication.getInstance().getDbHelper().delete(channelMembers, selection, selectionArgs);
    }

    /**
     * 批量删除频道成员
     *
     * @param list 频道成员
     */
    public synchronized void deleteMembers(List<MSChannelMember> list) {
        try {
            MSIMApplication.getInstance().getDbHelper().getDb()
                    .beginTransaction();
            if (MSCommonUtils.isNotEmpty(list)) {
                for (int i = 0, size = list.size(); i < size; i++) {
                    insertOrUpdate(list.get(i));
                }
                MSIMApplication.getInstance().getDbHelper().getDb()
                        .setTransactionSuccessful();
            }
        } catch (Exception ignored) {
        } finally {
            if (MSIMApplication.getInstance().getDbHelper().getDb().inTransaction()) {
                MSIMApplication.getInstance().getDbHelper().getDb()
                        .endTransaction();
            }
        }
        ChannelMembersManager.getInstance().setOnRemoveChannelMember(list);
    }

    public long queryMaxVersion(String channelID, byte channelType) {
        Object[] args = new Object[2];
        args[0] = channelID;
        args[1] = channelType;
        String sql = "select max(version) version from " + channelMembers + " where channel_id =? and channel_type=? limit 0, 1";
        long version = 0;
        try {
            if (MSIMApplication.getInstance().getDbHelper() != null) {
                Cursor cursor = MSIMApplication
                        .getInstance()
                        .getDbHelper()
                        .rawQuery(sql, args);
                if (cursor != null) {
                    if (cursor.moveToFirst()) {
                        version = MSCursor.readLong(cursor, "version");
                    }
                    cursor.close();
                }
            }
        } catch (Exception ignored) {
        }
        return version;
    }

    @Deprecated
    public synchronized MSChannelMember queryMaxVersionMember(String channelID, byte channelType) {
        MSChannelMember channelMember = null;
        Object[] args = new Object[2];
        args[0] = channelID;
        args[1] = channelType;
        String sql = "select * from " + channelMembers + " where " + MSDBColumns.MSChannelMembersColumns.channel_id + "=? and " + MSDBColumns.MSChannelMembersColumns.channel_type + "=? order by " + MSDBColumns.MSChannelMembersColumns.version + " desc limit 0,1";
        try (Cursor cursor = MSIMApplication
                .getInstance()
                .getDbHelper().rawQuery(sql, args)) {
            if (cursor == null) {
                return null;
            }
            if (cursor.moveToLast()) {
                channelMember = serializableChannelMember(cursor);
            }
        }
        return channelMember;
    }

    public synchronized List<MSChannelMember> queryRobotMembers(String channelId, byte channelType) {
        String selection = "channel_id=? and channel_type=? and robot=1 and is_deleted=0";
        Cursor cursor = MSIMApplication
                .getInstance()
                .getDbHelper().select(channelMembers, selection, new String[]{channelId, String.valueOf(channelType)}, null);
        List<MSChannelMember> list = new ArrayList<>();
        if (cursor == null) {
            return list;
        }
        for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
            list.add(serializableChannelMember(cursor));
        }
        cursor.close();
        return list;
    }

    public List<MSChannelMember> queryWithRole(String channelId, byte channelType, int role) {
        String selection = "channel_id=? AND channel_type=? AND role=? AND is_deleted=0";
        Cursor cursor = MSIMApplication
                .getInstance()
                .getDbHelper().select(channelMembers, selection, new String[]{channelId, String.valueOf(channelType), String.valueOf(role)}, null);
        List<MSChannelMember> list = new ArrayList<>();
        if (cursor == null) {
            return list;
        }
        for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
            list.add(serializableChannelMember(cursor));
        }
        cursor.close();
        return list;
    }

    public synchronized List<MSChannelMember> queryWithStatus(String channelId, byte channelType, int status) {
        Object[] args = new Object[3];
        args[0] = channelId;
        args[1] = channelType;
        args[2] = status;
        String sql = "select " + channelMembers + ".*," + channel + ".channel_name," + channel + ".channel_remark," + channel + ".avatar from " + channelMembers + " left Join " + channel + " where " + channelMembers + ".member_uid = " + channel + ".channel_id AND " + channel + ".channel_type=1 AND " + channelMembers + ".channel_id=? and " + channelMembers + ".channel_type=? and " + channelMembers + ".status=? order by " + channelMembers + ".role=1 desc," + channelMembers + ".role=2 desc," + channelMembers + "." + MSDBColumns.MSChannelMembersColumns.created_at + " asc";
        Cursor cursor = MSIMApplication
                .getInstance()
                .getDbHelper().rawQuery(sql, args);
        List<MSChannelMember> list = new ArrayList<>();
        if (cursor == null) {
            return list;
        }
        for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
            list.add(serializableChannelMember(cursor));
        }
        cursor.close();
        return list;
    }

    public synchronized int queryCount(String channelID, byte channelType) {
        Object[] args = new Object[2];
        args[0] = channelID;
        args[1] = channelType;
        String sql = "select count(*) from " + channelMembers
                + " where (" + MSDBColumns.MSChannelMembersColumns.channel_id + "=? and "
                + MSDBColumns.MSChannelMembersColumns.channel_type + "=? and " + MSDBColumns.MSChannelMembersColumns.is_deleted + "=0 and " + MSDBColumns.MSChannelMembersColumns.status + "=1)";
        Cursor cursor = MSIMApplication.getInstance().getDbHelper().rawQuery(sql, args);
        cursor.moveToFirst();
        int count = cursor.getInt(0);
        cursor.close();
        return count;
    }

    /**
     * 序列化频道成员
     *
     * @param cursor Cursor
     * @return MSChannelMember
     */
    private MSChannelMember serializableChannelMember(Cursor cursor) {
        MSChannelMember channelMember = new MSChannelMember();
        channelMember.id = MSCursor.readLong(cursor, MSDBColumns.MSChannelMembersColumns.id);
        channelMember.status = MSCursor.readInt(cursor, MSDBColumns.MSChannelMembersColumns.status);
        channelMember.channelID = MSCursor.readString(cursor, MSDBColumns.MSChannelMembersColumns.channel_id);
        channelMember.channelType = (byte) MSCursor.readInt(cursor, MSDBColumns.MSChannelMembersColumns.channel_type);
        channelMember.memberUID = MSCursor.readString(cursor, MSDBColumns.MSChannelMembersColumns.member_uid);
        channelMember.memberName = MSCursor.readString(cursor, MSDBColumns.MSChannelMembersColumns.member_name);
        channelMember.memberAvatar = MSCursor.readString(cursor, MSDBColumns.MSChannelMembersColumns.member_avatar);
        channelMember.memberRemark = MSCursor.readString(cursor, MSDBColumns.MSChannelMembersColumns.member_remark);
        channelMember.role = MSCursor.readInt(cursor, MSDBColumns.MSChannelMembersColumns.role);
        channelMember.isDeleted = MSCursor.readInt(cursor, MSDBColumns.MSChannelMembersColumns.is_deleted);
        channelMember.version = MSCursor.readLong(cursor, MSDBColumns.MSChannelMembersColumns.version);
        channelMember.createdAt = MSCursor.readString(cursor, MSDBColumns.MSChannelMembersColumns.created_at);
        channelMember.updatedAt = MSCursor.readString(cursor, MSDBColumns.MSChannelMembersColumns.updated_at);
        channelMember.memberInviteUID = MSCursor.readString(cursor, MSDBColumns.MSChannelMembersColumns.member_invite_uid);
        channelMember.robot = MSCursor.readInt(cursor, MSDBColumns.MSChannelMembersColumns.robot);
        channelMember.forbiddenExpirationTime = MSCursor.readLong(cursor, MSDBColumns.MSChannelMembersColumns.forbidden_expiration_time);
        String channelName = MSCursor.readString(cursor, MSDBColumns.MSChannelColumns.channel_name);
        if (!TextUtils.isEmpty(channelName)) channelMember.memberName = channelName;
        channelMember.remark = MSCursor.readString(cursor, MSDBColumns.MSChannelColumns.channel_remark);
        channelMember.memberAvatar = MSCursor.readString(cursor, MSDBColumns.MSChannelColumns.avatar);
        String avatarCache = MSCursor.readString(cursor, MSDBColumns.MSChannelColumns.avatar_cache_key);
        if (!TextUtils.isEmpty(avatarCache)) {
            channelMember.memberAvatarCacheKey = avatarCache;
        } else {
            channelMember.memberAvatarCacheKey = MSCursor.readString(cursor, MSDBColumns.MSChannelMembersColumns.memberAvatarCacheKey);
        }
        String extra = MSCursor.readString(cursor, MSDBColumns.MSChannelMembersColumns.extra);
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
                MSLoggerUtils.getInstance().e(TAG, "serializableChannelMember extra error");
            }
            channelMember.extraMap = hashMap;
        }
        return channelMember;
    }
}
