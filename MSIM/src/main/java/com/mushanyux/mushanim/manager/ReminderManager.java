package com.mushanyux.mushanim.manager;

import android.text.TextUtils;

import com.mushanyux.mushanim.db.ReminderDBManager;
import com.mushanyux.mushanim.entity.MSReminder;
import com.mushanyux.mushanim.interfaces.INewReminderListener;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ReminderManager extends BaseManager {
    private ReminderManager() {
    }

    private static class RemindManagerBinder {
        final static ReminderManager manager = new ReminderManager();
    }

    public static ReminderManager getInstance() {
        return RemindManagerBinder.manager;
    }

    private ConcurrentHashMap<String, INewReminderListener> newReminderMaps;

    public void addOnNewReminderListener(String key, INewReminderListener iNewReminderListener) {
        if (TextUtils.isEmpty(key) || iNewReminderListener == null) return;
        if (newReminderMaps == null) newReminderMaps = new ConcurrentHashMap<>();
        newReminderMaps.put(key, iNewReminderListener);
    }

    public void removeNewReminderListener(String key) {
        if (newReminderMaps != null) newReminderMaps.remove(key);
    }

    private void setNewReminders(List<MSReminder> list) {
        if (newReminderMaps != null && !newReminderMaps.isEmpty()) {
            runOnMainThread(() -> {
                for (Map.Entry<String, INewReminderListener> entry : newReminderMaps.entrySet()) {
                    entry.getValue().newReminder(list);
                }
            });
        }
    }

    /**
     * 获取某个类型的提醒
     *
     * @param channelID   频道ID
     * @param channelType 频道类型
     * @param type        提醒类型
     * @return MSReminder
     */
    public MSReminder getReminder(String channelID, byte channelType, int type) {
        List<MSReminder> list = getReminders(channelID, channelType);
        MSReminder msReminder = null;
        for (int i = 0, size = list.size(); i < size; i++) {
            if (list.get(i).type == type) {
                msReminder = list.get(i);
                break;
            }
        }
        return msReminder;
    }

    /**
     * 查询某个会话的高光内容
     *
     * @param channelID   频道ID
     * @param channelType 频道类型
     * @return List<MSReminder>
     */
    public List<MSReminder> getReminders(String channelID, byte channelType) {
        return ReminderDBManager.getInstance().queryWithChannelAndDone(channelID, channelType, 0);
    }

    public List<MSReminder> getRemindersWithType(String channelID, byte channelType, int type) {
        return ReminderDBManager.getInstance().queryWithChannelAndTypeAndDone(channelID, channelType, type, 0);
    }

    public void saveOrUpdateReminders(List<MSReminder> reminderList) {
        List<MSReminder> msReminders = ReminderDBManager.getInstance().insertOrUpdateReminders(reminderList);
        if (msReminders != null && !msReminders.isEmpty()) {
            setNewReminders(reminderList);
        }
    }

    public long getMaxVersion() {
        return ReminderDBManager.getInstance().queryMaxVersion();
    }

    public void done() {

    }
}
