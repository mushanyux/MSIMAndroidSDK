package com.mushanyux.mushanim.db;

import static com.mushanyux.mushanim.db.MSDBColumns.TABLE.reminders;

import android.content.ContentValues;
import android.database.Cursor;
import android.text.TextUtils;

import com.mushanyux.mushanim.MSIM;
import com.mushanyux.mushanim.MSIMApplication;
import com.mushanyux.mushanim.entity.MSReminder;
import com.mushanyux.mushanim.entity.MSUIConversationMsg;
import com.mushanyux.mushanim.utils.MSLoggerUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

public class ReminderDBManager {
    private static final String TAG = "ReminderDBManager";

    private ReminderDBManager() {
    }

    private static class ReminderDBManagerBinder {
        final static ReminderDBManager binder = new ReminderDBManager();
    }

    public static ReminderDBManager getInstance() {
        return ReminderDBManagerBinder.binder;
    }

    public void doneWithReminderIds(List<Long> ids) {
        ContentValues cv = new ContentValues();
        cv.put("done", 1);
        String[] strings = new String[ids.size()];
        for (int i = 0; i < ids.size(); i++) {
            strings[i] = ids.get(i) + "";
        }
        MSIMApplication.getInstance().getDbHelper().update(reminders, cv, "reminder_id in (" + MSCursor.getPlaceholders(ids.size()) + ")", strings);
    }

    public long queryMaxVersion() {
        String sql = "select * from " + reminders + " order by version desc limit 1";
        long version = 0;
        try (Cursor cursor = MSIMApplication
                .getInstance()
                .getDbHelper().rawQuery(sql)) {
            if (cursor == null) {
                return 0;
            }
            if (cursor.moveToLast()) {
                version = MSCursor.readLong(cursor, "version");
            }
        }
        return version;
    }

    public List<MSReminder> queryWithChannelAndDone(String channelID, byte channelType, int done) {
        String sql = "select * from " + reminders + " where channel_id=? and channel_type=? and done=? order by message_seq desc";
        List<MSReminder> list = new ArrayList<>();
        try (Cursor cursor = MSIMApplication.getInstance().getDbHelper().rawQuery(sql, new Object[]{channelID, channelType, done})) {
            if (cursor == null) {
                return list;
            }
            for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                MSReminder reminder = serializeReminder(cursor);
                list.add(reminder);
            }
        }
        return list;
    }

    public List<MSReminder> queryWithChannelAndTypeAndDone(String channelID, byte channelType, int type, int done) {
        String sql = "select * from " + reminders + " where channel_id=? and channel_type=? and done=? and type =? order by message_seq desc";
        List<MSReminder> list = new ArrayList<>();
        try (Cursor cursor = MSIMApplication.getInstance().getDbHelper().rawQuery(sql, new Object[]{channelID, channelType, done, type})) {
            if (cursor == null) {
                return list;
            }
            for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                MSReminder reminder = serializeReminder(cursor);
                list.add(reminder);
            }
        }
        return list;
    }

    private List<MSReminder> queryWithIds(List<Long> ids) {
        StringBuilder stringBuffer = new StringBuilder();
        for (int i = 0, size = ids.size(); i < size; i++) {
            if (!TextUtils.isEmpty(stringBuffer)) {
                stringBuffer.append(",");
            }
            stringBuffer.append(ids.get(i));
        }
        String sql = "select * from " + reminders + " where reminder_id in (" + stringBuffer + ")";
        List<MSReminder> list = new ArrayList<>();
        try (Cursor cursor = MSIMApplication
                .getInstance()
                .getDbHelper()
                .rawQuery(sql)) {
            if (cursor == null) {
                return list;
            }
            for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                MSReminder reminder = serializeReminder(cursor);
                list.add(reminder);
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    private List<MSReminder> queryWithChannelIds(List<String> channelIds) {
        List<MSReminder> list = new ArrayList<>();
        try (Cursor cursor = MSIMApplication
                .getInstance()
                .getDbHelper()
                .select(reminders, "channel_id in (" + MSCursor.getPlaceholders(channelIds.size()) + ")", channelIds.toArray(new String[0]), null)) {
            if (cursor == null) {
                return list;
            }
            for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                MSReminder reminder = serializeReminder(cursor);
                list.add(reminder);
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    public List<MSReminder> insertOrUpdateReminders(List<MSReminder> list) {
        List<Long> ids = new ArrayList<>();
        List<String> channelIds = new ArrayList<>();
        for (int i = 0, size = list.size(); i < size; i++) {
            boolean isAdd = true;
            for (String channelId : channelIds) {
                if (!TextUtils.isEmpty(list.get(i).channelID) && channelId.equals(list.get(i).channelID)) {
                    isAdd = false;
                    break;
                }
            }
            if (isAdd) channelIds.add(list.get(i).channelID);
            ids.add(list.get(i).reminderID);

        }
        List<ContentValues> insertCVs = new ArrayList<>();
        List<ContentValues> updateCVs = new ArrayList<>();
        List<MSReminder> allList = queryWithIds(ids);
        for (int i = 0, size = list.size(); i < size; i++) {
            boolean isAdd = true;
            for (MSReminder reminder : allList) {
                if (reminder.reminderID == list.get(i).reminderID) {
                    updateCVs.add(MSSqlContentValues.getCVWithReminder(list.get(i)));
                    isAdd = false;
                    break;
                }
            }
            if (isAdd) {
                insertCVs.add(MSSqlContentValues.getCVWithReminder(list.get(i)));
            }
        }
        try {
            MSIMApplication.getInstance().getDbHelper().getDb()
                    .beginTransaction();
            if (!insertCVs.isEmpty()) {
                for (ContentValues cv : insertCVs) {
                    MSIMApplication.getInstance().getDbHelper().insert(reminders, cv);
                }
            }
            if (!updateCVs.isEmpty()) {
                for (ContentValues cv : updateCVs) {
                    String[] update = new String[1];
                    update[0] = cv.getAsString("reminder_id");
                    MSIMApplication.getInstance().getDbHelper()
                            .update(reminders, cv, "reminder_id=?", update);
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

        List<MSReminder> reminderList = queryWithChannelIds(channelIds);
        HashMap<String, List<MSReminder>> maps = listToMap(reminderList);
        List<MSUIConversationMsg> uiMsgList = ConversationDbManager.getInstance().queryWithChannelIds(channelIds);
        for (int i = 0, size = uiMsgList.size(); i < size; i++) {
            String key = uiMsgList.get(i).channelID + "_" + uiMsgList.get(i).channelType;
            if (maps.containsKey(key)) {
                uiMsgList.get(i).setReminderList(maps.get(key));
            }
           // MSIM.getInstance().getConversationManager().setOnRefreshMsg(uiMsgList.get(i), i == list.size() - 1, "saveReminders");
        }
        MSIM.getInstance().getConversationManager().setOnRefreshMsg(uiMsgList,"saveReminders");
        return reminderList;
    }

    private HashMap<String, List<MSReminder>> listToMap(List<MSReminder> list) {
        HashMap<String, List<MSReminder>> map = new HashMap<>();
        if (list == null || list.isEmpty()) {
            return map;
        }
        for (MSReminder reminder : list) {
            String key = reminder.channelID + "_" + reminder.channelType;
            List<MSReminder> tempList = null;
            if (map.containsKey(key)) {
                tempList = map.get(key);
            }
            if (tempList == null) tempList = new ArrayList<>();
            tempList.add(reminder);
            map.put(key, tempList);
        }
        return map;
    }

    private MSReminder serializeReminder(Cursor cursor) {
        MSReminder reminder = new MSReminder();
        reminder.type = MSCursor.readInt(cursor, "type");
        reminder.reminderID = MSCursor.readLong(cursor, "reminder_id");
        reminder.messageID = MSCursor.readString(cursor, "message_id");
        reminder.messageSeq = MSCursor.readLong(cursor, "message_seq");
        reminder.isLocate = MSCursor.readInt(cursor, "is_locate");
        reminder.channelID = MSCursor.readString(cursor, "channel_id");
        reminder.channelType = (byte) MSCursor.readInt(cursor, "channel_type");
        reminder.text = MSCursor.readString(cursor, "text");
        reminder.version = MSCursor.readLong(cursor, "version");
        reminder.done = MSCursor.readInt(cursor, "done");
        String data = MSCursor.readString(cursor, "data");
        reminder.needUpload = MSCursor.readInt(cursor, "need_upload");
        reminder.publisher = MSCursor.readString(cursor, "publisher");
        if (!TextUtils.isEmpty(data)) {
            HashMap<String, Object> hashMap = new HashMap<>();
            try {
                JSONObject jsonObject = new JSONObject(data);
                Iterator<String> keys = jsonObject.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    hashMap.put(key, jsonObject.opt(key));
                }
            } catch (JSONException e) {
                MSLoggerUtils.getInstance().e(TAG, "serializeReminder error");
            }
            reminder.data = hashMap;
        }
        return reminder;
    }
}
