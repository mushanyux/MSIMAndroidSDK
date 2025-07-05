package com.mushanyux.mushanim.db;

import android.content.ContentValues;
import android.text.TextUtils;

import com.mushanyux.mushanim.entity.MSChannel;
import com.mushanyux.mushanim.entity.MSChannelMember;
import com.mushanyux.mushanim.entity.MSConversationMsg;
import com.mushanyux.mushanim.entity.MSConversationMsgExtra;
import com.mushanyux.mushanim.entity.MSMsg;
import com.mushanyux.mushanim.entity.MSMsgExtra;
import com.mushanyux.mushanim.entity.MSMsgReaction;
import com.mushanyux.mushanim.entity.MSMsgSetting;
import com.mushanyux.mushanim.entity.MSReminder;
import com.mushanyux.mushanim.utils.MSLoggerUtils;
import com.mushanyux.mushanim.utils.MSTypeUtils;

import org.json.JSONException;
import org.json.JSONObject;


class MSSqlContentValues {
    private final static String TAG = "MSSqlContentValues";

    static ContentValues getContentValuesWithMsg(MSMsg msg) {
        ContentValues contentValues = new ContentValues();
        if (msg == null) {
            return contentValues;
        }
        if (msg.setting == null) {
            msg.setting = new MSMsgSetting();
        }
        contentValues.put(MSDBColumns.MSMessageColumns.message_id, msg.messageID);
        contentValues.put(MSDBColumns.MSMessageColumns.message_seq, msg.messageSeq);

        contentValues.put(MSDBColumns.MSMessageColumns.order_seq, msg.orderSeq);
        contentValues.put(MSDBColumns.MSMessageColumns.timestamp, msg.timestamp);
        contentValues.put(MSDBColumns.MSMessageColumns.from_uid, msg.fromUID);
        contentValues.put(MSDBColumns.MSMessageColumns.channel_id, msg.channelID);
        contentValues.put(MSDBColumns.MSMessageColumns.channel_type, msg.channelType);
        contentValues.put(MSDBColumns.MSMessageColumns.is_deleted, msg.isDeleted);
        contentValues.put(MSDBColumns.MSMessageColumns.type, msg.type);
        contentValues.put(MSDBColumns.MSMessageColumns.content, msg.content);
        contentValues.put(MSDBColumns.MSMessageColumns.status, msg.status);
        contentValues.put(MSDBColumns.MSMessageColumns.created_at, msg.createdAt);
        contentValues.put(MSDBColumns.MSMessageColumns.updated_at, msg.updatedAt);
        contentValues.put(MSDBColumns.MSMessageColumns.voice_status, msg.voiceStatus);
        contentValues.put(MSDBColumns.MSMessageColumns.client_msg_no, msg.clientMsgNO);
        contentValues.put(MSDBColumns.MSMessageColumns.flame, msg.flame);
        contentValues.put(MSDBColumns.MSMessageColumns.flame_second, msg.flameSecond);
        contentValues.put(MSDBColumns.MSMessageColumns.viewed, msg.viewed);
        contentValues.put(MSDBColumns.MSMessageColumns.viewed_at, msg.viewedAt);
        contentValues.put(MSDBColumns.MSMessageColumns.topic_id, msg.topicID);
        contentValues.put(MSDBColumns.MSMessageColumns.expire_time, msg.expireTime);
        contentValues.put(MSDBColumns.MSMessageColumns.expire_timestamp, msg.expireTimestamp);
        byte setting = MSTypeUtils.getInstance().getMsgSetting(msg.setting);
        contentValues.put(MSDBColumns.MSMessageColumns.setting, setting);
        if (msg.baseContentMsgModel != null) {
            contentValues.put(MSDBColumns.MSMessageColumns.searchable_word, msg.baseContentMsgModel.getSearchableWord());
        }
        contentValues.put(MSDBColumns.MSMessageColumns.extra, msg.getLocalMapExtraString());
        return contentValues;
    }

    static ContentValues getContentValuesWithCoverMsg(MSConversationMsg msConversationMsg, boolean isSync) {
        ContentValues contentValues = new ContentValues();
        if (msConversationMsg == null) {
            return contentValues;
        }
        contentValues.put(MSDBColumns.MSCoverMessageColumns.channel_id, msConversationMsg.channelID);
        contentValues.put(MSDBColumns.MSCoverMessageColumns.channel_type, msConversationMsg.channelType);
        contentValues.put(MSDBColumns.MSCoverMessageColumns.last_client_msg_no, msConversationMsg.lastClientMsgNO);
        contentValues.put(MSDBColumns.MSCoverMessageColumns.last_msg_timestamp, msConversationMsg.lastMsgTimestamp);
        contentValues.put(MSDBColumns.MSCoverMessageColumns.last_msg_seq, msConversationMsg.lastMsgSeq);
        contentValues.put(MSDBColumns.MSCoverMessageColumns.unread_count, msConversationMsg.unreadCount);
        contentValues.put(MSDBColumns.MSCoverMessageColumns.parent_channel_id, msConversationMsg.parentChannelID);
        contentValues.put(MSDBColumns.MSCoverMessageColumns.parent_channel_type, msConversationMsg.parentChannelType);
        if (isSync) {
            contentValues.put(MSDBColumns.MSCoverMessageColumns.version, msConversationMsg.version);
        }
        contentValues.put(MSDBColumns.MSCoverMessageColumns.is_deleted, msConversationMsg.isDeleted);
        contentValues.put(MSDBColumns.MSCoverMessageColumns.extra, msConversationMsg.getLocalExtraString());
        return contentValues;
    }

    static ContentValues getContentValuesWithChannel(MSChannel channel) {
        ContentValues contentValues = new ContentValues();
        if (channel == null) {
            return contentValues;
        }
        contentValues.put(MSDBColumns.MSChannelColumns.channel_id, channel.channelID);
        contentValues.put(MSDBColumns.MSChannelColumns.channel_type, channel.channelType);
        contentValues.put(MSDBColumns.MSChannelColumns.channel_name, channel.channelName);
        contentValues.put(MSDBColumns.MSChannelColumns.channel_remark, channel.channelRemark);
        contentValues.put(MSDBColumns.MSChannelColumns.avatar, channel.avatar);
        contentValues.put(MSDBColumns.MSChannelColumns.top, channel.top);
        contentValues.put(MSDBColumns.MSChannelColumns.save, channel.save);
        contentValues.put(MSDBColumns.MSChannelColumns.mute, channel.mute);
        contentValues.put(MSDBColumns.MSChannelColumns.forbidden, channel.forbidden);
        contentValues.put(MSDBColumns.MSChannelColumns.invite, channel.invite);
        contentValues.put(MSDBColumns.MSChannelColumns.status, channel.status);
        contentValues.put(MSDBColumns.MSChannelColumns.is_deleted, channel.isDeleted);
        contentValues.put(MSDBColumns.MSChannelColumns.follow, channel.follow);
        contentValues.put(MSDBColumns.MSChannelColumns.version, channel.version);
        contentValues.put(MSDBColumns.MSChannelColumns.show_nick, channel.showNick);
        contentValues.put(MSDBColumns.MSChannelColumns.created_at, channel.createdAt);
        contentValues.put(MSDBColumns.MSChannelColumns.updated_at, channel.updatedAt);
        contentValues.put(MSDBColumns.MSChannelColumns.online, channel.online);
        contentValues.put(MSDBColumns.MSChannelColumns.last_offline, channel.lastOffline);
        contentValues.put(MSDBColumns.MSChannelColumns.receipt, channel.receipt);
        contentValues.put(MSDBColumns.MSChannelColumns.robot, channel.robot);
        contentValues.put(MSDBColumns.MSChannelColumns.category, channel.category);
        contentValues.put(MSDBColumns.MSChannelColumns.username, channel.username);
        contentValues.put(MSDBColumns.MSChannelColumns.avatar_cache_key, TextUtils.isEmpty(channel.avatarCacheKey) ? "" : channel.avatarCacheKey);
        contentValues.put(MSDBColumns.MSChannelColumns.flame, channel.flame);
        contentValues.put(MSDBColumns.MSChannelColumns.flame_second, channel.flameSecond);
        contentValues.put(MSDBColumns.MSChannelColumns.device_flag, channel.deviceFlag);
        contentValues.put(MSDBColumns.MSChannelColumns.parent_channel_id, channel.parentChannelID);
        contentValues.put(MSDBColumns.MSChannelColumns.parent_channel_type, channel.parentChannelType);

        if (channel.localExtra != null) {
            JSONObject jsonObject = new JSONObject(channel.localExtra);
            contentValues.put(MSDBColumns.MSChannelColumns.localExtra, jsonObject.toString());
        }
        if (channel.remoteExtraMap != null) {
            JSONObject jsonObject = new JSONObject(channel.remoteExtraMap);
            contentValues.put(MSDBColumns.MSChannelColumns.remote_extra, jsonObject.toString());
        }
        return contentValues;
    }

    static ContentValues getContentValuesWithChannelMember(MSChannelMember channelMember) {
        ContentValues contentValues = new ContentValues();
        if (channelMember == null) {
            return contentValues;
        }
        contentValues.put(MSDBColumns.MSChannelMembersColumns.channel_id, channelMember.channelID);
        contentValues.put(MSDBColumns.MSChannelMembersColumns.channel_type, channelMember.channelType);
        contentValues.put(MSDBColumns.MSChannelMembersColumns.member_invite_uid, channelMember.memberInviteUID);
        contentValues.put(MSDBColumns.MSChannelMembersColumns.member_uid, channelMember.memberUID);
        contentValues.put(MSDBColumns.MSChannelMembersColumns.member_name, channelMember.memberName);
        contentValues.put(MSDBColumns.MSChannelMembersColumns.member_remark, channelMember.memberRemark);
        contentValues.put(MSDBColumns.MSChannelMembersColumns.member_avatar, channelMember.memberAvatar);
        contentValues.put(MSDBColumns.MSChannelMembersColumns.memberAvatarCacheKey, TextUtils.isEmpty(channelMember.memberAvatarCacheKey) ? "" : channelMember.memberAvatarCacheKey);
        contentValues.put(MSDBColumns.MSChannelMembersColumns.role, channelMember.role);
        contentValues.put(MSDBColumns.MSChannelMembersColumns.is_deleted, channelMember.isDeleted);
        contentValues.put(MSDBColumns.MSChannelMembersColumns.version, channelMember.version);
        contentValues.put(MSDBColumns.MSChannelMembersColumns.status, channelMember.status);
        contentValues.put(MSDBColumns.MSChannelMembersColumns.robot, channelMember.robot);
        contentValues.put(MSDBColumns.MSChannelMembersColumns.forbidden_expiration_time, channelMember.forbiddenExpirationTime);
        contentValues.put(MSDBColumns.MSChannelMembersColumns.created_at, channelMember.createdAt);
        contentValues.put(MSDBColumns.MSChannelMembersColumns.updated_at, channelMember.updatedAt);

        if (channelMember.extraMap != null) {
            JSONObject jsonObject = new JSONObject(channelMember.extraMap);
            contentValues.put(MSDBColumns.MSChannelMembersColumns.extra, jsonObject.toString());
        }

        return contentValues;
    }

    static ContentValues getContentValuesWithMsgReaction(MSMsgReaction reaction) {
        ContentValues contentValues = new ContentValues();
        if (reaction == null) {
            return contentValues;
        }
        contentValues.put("channel_id", reaction.channelID);
        contentValues.put("channel_type", reaction.channelType);
        contentValues.put("message_id", reaction.messageID);
        contentValues.put("uid", reaction.uid);
        contentValues.put("name", reaction.name);
        contentValues.put("is_deleted", reaction.isDeleted);
        contentValues.put("seq", reaction.seq);
        contentValues.put("emoji", reaction.emoji);
        contentValues.put("created_at", reaction.createdAt);
        return contentValues;
    }

    static ContentValues getCVWithMsgExtra(MSMsgExtra extra) {
        ContentValues cv = new ContentValues();
        cv.put("channel_id", extra.channelID);
        cv.put("channel_type", extra.channelType);
        cv.put("message_id", extra.messageID);
        cv.put("readed", extra.readed);
        cv.put("readed_count", extra.readedCount);
        cv.put("unread_count", extra.unreadCount);
        cv.put("revoke", extra.revoke);
        cv.put("revoker", extra.revoker);
        cv.put("extra_version", extra.extraVersion);
        cv.put("is_mutual_deleted", extra.isMutualDeleted);
        cv.put("content_edit", extra.contentEdit);
        cv.put("edited_at", extra.editedAt);
        cv.put("need_upload", extra.needUpload);
        cv.put("is_pinned", extra.isPinned);
        return cv;
    }

    static ContentValues getCVWithReminder(MSReminder reminder) {
        ContentValues cv = new ContentValues();
        cv.put("channel_id", reminder.channelID);
        cv.put("channel_type", reminder.channelType);
        cv.put("reminder_id", reminder.reminderID);
        cv.put("message_id", reminder.messageID);
        cv.put("message_seq", reminder.messageSeq);
        cv.put("uid", reminder.uid);
        cv.put("type", reminder.type);
        cv.put("is_locate", reminder.isLocate);
        cv.put("text", reminder.text);
        cv.put("version", reminder.version);
        cv.put("done", reminder.done);
        cv.put("need_upload", reminder.needUpload);
        cv.put("publisher", reminder.publisher);

        if (reminder.data != null) {
            JSONObject jsonObject = new JSONObject();
            for (Object key : reminder.data.keySet()) {
                try {
                    jsonObject.put(String.valueOf(key), reminder.data.get(key));
                } catch (JSONException e) {
                    MSLoggerUtils.getInstance().e(TAG, "getCVWithReminder error");
                }
            }
            cv.put("data", jsonObject.toString());
        }
        return cv;
    }

    static ContentValues getCVWithExtra(MSConversationMsgExtra extra) {
        ContentValues cv = new ContentValues();
        cv.put("channel_id", extra.channelID);
        cv.put("channel_type", extra.channelType);
        cv.put("browse_to", extra.browseTo);
        cv.put("keep_message_seq", extra.keepMessageSeq);
        cv.put("keep_offset_y", extra.keepOffsetY);
        cv.put("draft", extra.draft);
        cv.put("draft_updated_at", extra.draftUpdatedAt);
        cv.put("version", extra.version);
        return cv;
    }
}
