package com.mushanyux.mushanim.manager;

import android.text.TextUtils;

import com.mushanyux.mushanim.db.ChannelDBManager;
import com.mushanyux.mushanim.db.MSDBColumns;
import com.mushanyux.mushanim.entity.MSChannel;
import com.mushanyux.mushanim.entity.MSChannelSearchResult;
import com.mushanyux.mushanim.interfaces.IChannelInfoListener;
import com.mushanyux.mushanim.interfaces.IGetChannelInfo;
import com.mushanyux.mushanim.interfaces.IRefreshChannel;
import com.mushanyux.mushanim.interfaces.IRefreshChannelAvatar;
import com.mushanyux.mushanim.utils.MSCommonUtils;
import com.mushanyux.mushanim.utils.MSLoggerUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class ChannelManager extends BaseManager {
    private final String TAG = "ChannelManager";

    private ChannelManager() {
    }

    private static class ChannelManagerBinder {
        static final ChannelManager channelManager = new ChannelManager();
    }

    public static ChannelManager getInstance() {
        return ChannelManagerBinder.channelManager;
    }

    private IRefreshChannelAvatar iRefreshChannelAvatar;
    private IGetChannelInfo iGetChannelInfo;
    private final CopyOnWriteArrayList<MSChannel> msChannelList = new CopyOnWriteArrayList<>();
    //监听刷新频道
    private ConcurrentHashMap<String, IRefreshChannel> refreshChannelMap;

    public synchronized MSChannel getChannel(String channelID, byte channelType) {
        if (TextUtils.isEmpty(channelID)) return null;
        MSChannel msChannel = null;
        for (MSChannel channel : msChannelList) {
            if (channel != null && channel.channelID.equals(channelID) && channel.channelType == channelType) {
                msChannel = channel;
                break;
            }
        }
        if (msChannel == null) {
            msChannel = ChannelDBManager.getInstance().query(channelID, channelType);
            if (msChannel != null) {
                msChannelList.add(msChannel);
            }
        }
        return msChannel;
    }

    // 从网络获取channel
    public void fetchChannelInfo(String channelID, byte channelType) {
        if (TextUtils.isEmpty(channelID)) return;
        MSChannel channel = getChannel(channelID, channelType, msChannel -> {
            if (msChannel != null)
                saveOrUpdateChannel(msChannel);
        });
        if (channel != null) {
            saveOrUpdateChannel(channel);
        }
    }

    public MSChannel getChannel(String channelId, byte channelType, IChannelInfoListener iChannelInfoListener) {
        if (this.iGetChannelInfo != null && !TextUtils.isEmpty(channelId) && iChannelInfoListener != null) {
            return iGetChannelInfo.onGetChannelInfo(channelId, channelType, iChannelInfoListener);
        } else return null;
    }

    public void addOnGetChannelInfoListener(IGetChannelInfo iGetChannelInfoListener) {
        this.iGetChannelInfo = iGetChannelInfoListener;
    }

    public void saveOrUpdateChannel(MSChannel channel) {
        if (channel == null) return;
        //先更改内存数据
        updateChannel(channel);
        setRefreshChannel(channel, true);
        ChannelDBManager.getInstance().insertOrUpdate(channel);
    }

    /**
     * 修改频道信息
     *
     * @param channel 频道
     */
    private void updateChannel(MSChannel channel) {
        if (channel == null) return;
        boolean isAdd = true;
        for (int i = 0, size = msChannelList.size(); i < size; i++) {
            if (msChannelList.get(i).channelID.equals(channel.channelID) && msChannelList.get(i).channelType == channel.channelType) {
                isAdd = false;
                msChannelList.get(i).forbidden = channel.forbidden;
                msChannelList.get(i).channelName = channel.channelName;
                msChannelList.get(i).avatar = channel.avatar;
                msChannelList.get(i).category = channel.category;
                msChannelList.get(i).lastOffline = channel.lastOffline;
                msChannelList.get(i).online = channel.online;
                msChannelList.get(i).follow = channel.follow;
                msChannelList.get(i).top = channel.top;
                msChannelList.get(i).channelRemark = channel.channelRemark;
                msChannelList.get(i).status = channel.status;
                msChannelList.get(i).version = channel.version;
                msChannelList.get(i).invite = channel.invite;
                msChannelList.get(i).localExtra = channel.localExtra;
                msChannelList.get(i).mute = channel.mute;
                msChannelList.get(i).save = channel.save;
                msChannelList.get(i).showNick = channel.showNick;
                msChannelList.get(i).isDeleted = channel.isDeleted;
                msChannelList.get(i).receipt = channel.receipt;
                msChannelList.get(i).robot = channel.robot;
                msChannelList.get(i).flameSecond = channel.flameSecond;
                msChannelList.get(i).flame = channel.flame;
                msChannelList.get(i).deviceFlag = channel.deviceFlag;
                msChannelList.get(i).parentChannelID = channel.parentChannelID;
                msChannelList.get(i).parentChannelType = channel.parentChannelType;
                msChannelList.get(i).avatarCacheKey = channel.avatarCacheKey;
                msChannelList.get(i).remoteExtraMap = channel.remoteExtraMap;
                break;
            }
        }
        if (isAdd) {
            msChannelList.add(channel);
        }
    }

    private void updateChannel(String channelID, byte channelType, String key, Object value) {
        if (TextUtils.isEmpty(channelID) || TextUtils.isEmpty(key)) return;
        for (int i = 0, size = msChannelList.size(); i < size; i++) {
            if (msChannelList.get(i).channelID.equals(channelID) && msChannelList.get(i).channelType == channelType) {
                switch (key) {
                    case MSDBColumns.MSChannelColumns.avatar_cache_key:
                        msChannelList.get(i).avatarCacheKey = (String) value;
                        break;
                    case MSDBColumns.MSChannelColumns.remote_extra:
                        msChannelList.get(i).remoteExtraMap = (HashMap<String, Object>) value;
                        break;
                    case MSDBColumns.MSChannelColumns.avatar:
                        msChannelList.get(i).avatar = (String) value;
                        break;
                    case MSDBColumns.MSChannelColumns.channel_remark:
                        msChannelList.get(i).channelRemark = (String) value;
                        break;
                    case MSDBColumns.MSChannelColumns.channel_name:
                        msChannelList.get(i).channelName = (String) value;
                        break;
                    case MSDBColumns.MSChannelColumns.follow:
                        msChannelList.get(i).follow = (int) value;
                        break;
                    case MSDBColumns.MSChannelColumns.forbidden:
                        msChannelList.get(i).forbidden = (int) value;
                        break;
                    case MSDBColumns.MSChannelColumns.invite:
                        msChannelList.get(i).invite = (int) value;
                        break;
                    case MSDBColumns.MSChannelColumns.is_deleted:
                        msChannelList.get(i).isDeleted = (int) value;
                        break;
                    case MSDBColumns.MSChannelColumns.last_offline:
                        msChannelList.get(i).lastOffline = (long) value;
                        break;
                    case MSDBColumns.MSChannelColumns.mute:
                        msChannelList.get(i).mute = (int) value;
                        break;
                    case MSDBColumns.MSChannelColumns.top:
                        msChannelList.get(i).top = (int) value;
                        break;
                    case MSDBColumns.MSChannelColumns.online:
                        msChannelList.get(i).online = (int) value;
                        break;
                    case MSDBColumns.MSChannelColumns.receipt:
                        msChannelList.get(i).receipt = (int) value;
                        break;
                    case MSDBColumns.MSChannelColumns.save:
                        msChannelList.get(i).save = (int) value;
                        break;
                    case MSDBColumns.MSChannelColumns.show_nick:
                        msChannelList.get(i).showNick = (int) value;
                        break;
                    case MSDBColumns.MSChannelColumns.status:
                        msChannelList.get(i).status = (int) value;
                        break;
                    case MSDBColumns.MSChannelColumns.username:
                        msChannelList.get(i).username = (String) value;
                        break;
                    case MSDBColumns.MSChannelColumns.flame:
                        msChannelList.get(i).flame = (int) value;
                        break;
                    case MSDBColumns.MSChannelColumns.flame_second:
                        msChannelList.get(i).flameSecond = (int) value;
                        break;
                    case MSDBColumns.MSChannelColumns.localExtra:
                        msChannelList.get(i).localExtra = (HashMap<String, Object>) value;
                        break;
                }
                setRefreshChannel(msChannelList.get(i), true);
                break;
            }
        }
    }

    /**
     * 添加或修改频道信息
     *
     * @param list 频道数据
     */
    public void saveOrUpdateChannels(List<MSChannel> list) {
        if (MSCommonUtils.isEmpty(list)) return;
        // 先修改内存数据
        for (int i = 0, size = list.size(); i < size; i++) {
            updateChannel(list.get(i));
            setRefreshChannel(list.get(i), i == list.size() - 1);
        }
        ChannelDBManager.getInstance().insertChannels(list);
    }

    /**
     * 修改频道状态
     *
     * @param channelID   频道ID
     * @param channelType 频道类型
     * @param status      状态
     */
    public void updateStatus(String channelID, byte channelType, int status) {
        updateChannel(channelID, channelType, MSDBColumns.MSChannelColumns.status, status);
        ChannelDBManager.getInstance().updateWithField(channelID, channelType, MSDBColumns.MSChannelColumns.status, String.valueOf(status));
    }


    /**
     * 修改频道名称
     *
     * @param channelID   频道ID
     * @param channelType 频道类型
     * @param name        名称
     */
    public void updateName(String channelID, byte channelType, String name) {
        updateChannel(channelID, channelType, MSDBColumns.MSChannelColumns.channel_name, name);
        ChannelDBManager.getInstance().updateWithField(channelID, channelType, MSDBColumns.MSChannelColumns.channel_name, name);
    }

    /**
     * 获取频道信息
     *
     * @param channelType 频道类型
     * @param status      状态
     * @return List<MSChannel>
     */
    public List<MSChannel> getWithStatus(byte channelType, int status) {
        return ChannelDBManager.getInstance().queryWithStatus(channelType, status);
    }

    public List<MSChannel> getWithChannelIdsAndChannelType(List<String> channelIds, byte channelType) {
        return ChannelDBManager.getInstance().queryWithChannelIdsAndChannelType(channelIds, channelType);
    }

    public List<MSChannel> getChannels(List<String> channelIds) {
        return ChannelDBManager.getInstance().queryWithChannelIds(channelIds);
    }

    /**
     * 搜索频道
     *
     * @param keyword 关键字
     * @return List<MSChannelSearchResult>
     */
    public List<MSChannelSearchResult> search(String keyword) {
        return ChannelDBManager.getInstance().search(keyword);
    }

    /**
     * 搜索频道
     *
     * @param keyword     关键字
     * @param channelType 频道类型
     * @return List<MSChannel>
     */
    public List<MSChannel> searchWithChannelType(String keyword, byte channelType) {
        return ChannelDBManager.getInstance().searchWithChannelType(keyword, channelType);
    }

    public List<MSChannel> searchWithChannelTypeAndFollow(String keyword, byte channelType, int follow) {
        return ChannelDBManager.getInstance().searchWithChannelTypeAndFollow(keyword, channelType, follow);
    }

    /**
     * 获取频道信息
     *
     * @param channelType 频道类型
     * @param follow      关注状态
     * @return List<MSChannel>
     */
    public List<MSChannel> getWithChannelTypeAndFollow(byte channelType, int follow) {
        return ChannelDBManager.getInstance().queryWithChannelTypeAndFollow(channelType, follow);
    }

    /**
     * 修改某个频道免打扰
     *
     * @param channelID   频道ID
     * @param channelType 频道类型
     * @param isMute      1：免打扰
     */
    public void updateMute(String channelID, byte channelType, int isMute) {
        updateChannel(channelID, channelType, MSDBColumns.MSChannelColumns.mute, isMute);
        ChannelDBManager.getInstance().updateWithField(channelID, channelType, MSDBColumns.MSChannelColumns.mute, String.valueOf(isMute));
    }

    /**
     * 修改备注信息
     *
     * @param channelID   频道ID
     * @param channelType 频道类型
     * @param hashExtra   扩展字段
     */
    public void updateLocalExtra(String channelID, byte channelType, HashMap<String, Object> hashExtra) {
        updateChannel(channelID, channelType, MSDBColumns.MSChannelColumns.localExtra, hashExtra);
        if (hashExtra != null) {
            JSONObject jsonObject = new JSONObject();
            for (String key : hashExtra.keySet()) {
                try {
                    jsonObject.put(key, hashExtra.get(key));
                } catch (JSONException e) {
                    MSLoggerUtils.getInstance().e(TAG, "updateLocalExtra error");
                }
            }
            ChannelDBManager.getInstance().updateWithField(channelID, channelType, MSDBColumns.MSChannelColumns.localExtra, jsonObject.toString());
        }
    }

    /**
     * 修改频道是否保存在通讯录
     *
     * @param channelID   频道ID
     * @param channelType 频道类型
     * @param isSave      1:保存
     */
    public void updateSave(String channelID, byte channelType, int isSave) {
        updateChannel(channelID, channelType, MSDBColumns.MSChannelColumns.save, isSave);
        ChannelDBManager.getInstance().updateWithField(channelID, channelType, MSDBColumns.MSChannelColumns.save, String.valueOf(isSave));
    }

    /**
     * 是否显示频道昵称
     *
     * @param channelID   频道ID
     * @param channelType 频道类型
     * @param showNick    1：显示频道昵称
     */
    public void updateShowNick(String channelID, byte channelType, int showNick) {
        updateChannel(channelID, channelType, MSDBColumns.MSChannelColumns.show_nick, showNick);
        ChannelDBManager.getInstance().updateWithField(channelID, channelType, MSDBColumns.MSChannelColumns.show_nick, String.valueOf(showNick));
    }

    /**
     * 修改某个频道是否置顶
     *
     * @param channelID   频道ID
     * @param channelType 频道类型
     * @param top         1：置顶
     */
    public void updateTop(String channelID, byte channelType, int top) {
        updateChannel(channelID, channelType, MSDBColumns.MSChannelColumns.top, top);
        ChannelDBManager.getInstance().updateWithField(channelID, channelType, MSDBColumns.MSChannelColumns.top, String.valueOf(top));
    }

    /**
     * 修改某个频道的备注
     *
     * @param channelID   频道ID
     * @param channelType 频道类型
     * @param remark      备注
     */
    public void updateRemark(String channelID, byte channelType, String remark) {
        updateChannel(channelID, channelType, MSDBColumns.MSChannelColumns.channel_remark, remark);
        ChannelDBManager.getInstance().updateWithField(channelID, channelType, MSDBColumns.MSChannelColumns.channel_remark, remark);
    }

    /**
     * 修改关注状态
     *
     * @param channelID   频道ID
     * @param channelType 频道类型
     * @param follow      是否关注
     */
    public void updateFollow(String channelID, byte channelType, int follow) {
        updateChannel(channelID, channelType, MSDBColumns.MSChannelColumns.follow, follow);
        ChannelDBManager.getInstance().updateWithField(channelID, channelType, MSDBColumns.MSChannelColumns.follow, String.valueOf(follow));
    }

    /**
     * 通过follow和status查询频道
     *
     * @param channelType 频道类型
     * @param follow      是否关注 好友或陌生人
     * @param status      状态 正常或黑名单
     * @return list
     */
    public List<MSChannel> getWithFollowAndStatus(byte channelType, int follow, int status) {
        return ChannelDBManager.getInstance().queryWithFollowAndStatus(channelType, follow, status);
    }

    public void updateAvatarCacheKey(String channelID, byte channelType, String avatar) {
        updateChannel(channelID, channelType, MSDBColumns.MSChannelColumns.avatar_cache_key, avatar);
        ChannelDBManager.getInstance().updateWithField(channelID, channelType, MSDBColumns.MSChannelColumns.avatar_cache_key, avatar);
    }

    public void addOnRefreshChannelAvatar(IRefreshChannelAvatar iRefreshChannelAvatar) {
        this.iRefreshChannelAvatar = iRefreshChannelAvatar;
    }

    public void setOnRefreshChannelAvatar(String channelID, byte channelType) {
        if (iRefreshChannelAvatar != null) {
            runOnMainThread(() -> iRefreshChannelAvatar.onRefreshChannelAvatar(channelID, channelType));
        }
    }

    public synchronized void clearARMCache() {
        msChannelList.clear();
    }

    // 刷新频道
    public void setRefreshChannel(MSChannel channel, boolean isEnd) {
        if (refreshChannelMap != null) {
            runOnMainThread(() -> {
                updateChannel(channel);
                for (Map.Entry<String, IRefreshChannel> entry : refreshChannelMap.entrySet()) {
                    entry.getValue().onRefreshChannel(channel, isEnd);
                }
            });
        }
    }

    // 监听刷新普通
    public void addOnRefreshChannelInfo(String key, IRefreshChannel iRefreshChannelListener) {
        if (TextUtils.isEmpty(key)) return;
        if (refreshChannelMap == null) refreshChannelMap = new ConcurrentHashMap<>();
        if (iRefreshChannelListener != null)
            refreshChannelMap.put(key, iRefreshChannelListener);
    }

    // 移除频道刷新监听
    public void removeRefreshChannelInfo(String key) {
        if (TextUtils.isEmpty(key) || refreshChannelMap == null) return;
        refreshChannelMap.remove(key);
    }

}
