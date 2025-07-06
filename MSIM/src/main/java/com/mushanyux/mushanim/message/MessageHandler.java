package com.mushanyux.mushanim.message;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import com.mushanyux.mushanim.MSIM;
import com.mushanyux.mushanim.MSIMApplication;
import com.mushanyux.mushanim.db.ConversationDbManager;
import com.mushanyux.mushanim.db.MsgDbManager;
import com.mushanyux.mushanim.entity.MSChannelType;
import com.mushanyux.mushanim.entity.MSMsg;
import com.mushanyux.mushanim.entity.MSSyncMsg;
import com.mushanyux.mushanim.entity.MSUIConversationMsg;
import com.mushanyux.mushanim.interfaces.IReceivedMsgListener;
import com.mushanyux.mushanim.manager.CMDManager;
import com.mushanyux.mushanim.message.type.MSMsgContentType;
import com.mushanyux.mushanim.message.type.MSMsgType;
import com.mushanyux.mushanim.protocol.MSBaseMsg;
import com.mushanyux.mushanim.protocol.MSConnectAckMsg;
import com.mushanyux.mushanim.protocol.MSDisconnectMsg;
import com.mushanyux.mushanim.protocol.MSPongMsg;
import com.mushanyux.mushanim.protocol.MSReceivedAckMsg;
import com.mushanyux.mushanim.protocol.MSSendAckMsg;
import com.mushanyux.mushanim.utils.MSCommonUtils;
import com.mushanyux.mushanim.utils.MSLoggerUtils;
import com.mushanyux.mushanim.utils.MSTypeUtils;

import org.json.JSONException;
import org.json.JSONObject;
import org.xsocket.connection.INonBlockingConnection;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.BufferOverflowException;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class MessageHandler {
    private final String TAG = "MessageHandler";

    private MessageHandler() {
    }

    private static class MessageHandlerBinder {
        static final MessageHandler handler = new MessageHandler();
    }

    public static MessageHandler getInstance() {
        return MessageHandlerBinder.handler;
    }

    private final List<MSReceivedAckMsg> receivedAckMsgList = Collections.synchronizedList(new ArrayList<>());

    int sendMessage(INonBlockingConnection connection, MSBaseMsg msg) {
        if (msg == null) {
            return 1;
        }
        byte[] bytes = MSProto.getInstance().encodeMsg(msg);
        if (bytes == null || bytes.length == 0) {
            MSLoggerUtils.getInstance().e(TAG, "发送了非法包:" + msg.packetType);
            return 1;
        }

        if (connection != null && connection.isOpen()) {
            try {
                connection.write(bytes, 0, bytes.length);
                connection.flush();
                return 1;
            } catch (BufferOverflowException e) {
                MSLoggerUtils.getInstance().e(TAG, "发消息异常 BufferOverflowException"
                        + e.getMessage());
                return 0;
            } catch (ClosedChannelException e) {
                MSLoggerUtils.getInstance().e(TAG, "发消息异常 ClosedChannelException"
                        + e.getMessage());
                return 0;
            } catch (SocketTimeoutException e) {
                MSLoggerUtils.getInstance().e(TAG, "发消息异常 SocketTimeoutException"
                        + e.getMessage());
                return 0;
            } catch (IOException e) {
                MSLoggerUtils.getInstance().e(TAG, "发消息异常 IOException" + e.getMessage());
                return 0;
            }
        } else {
            MSLoggerUtils.getInstance().e("发消息异常:"
                    + connection);
            return 0;
        }
    }

    private volatile List<MSSyncMsg> receivedMsgList;
    private final Object receivedMsgListLock = new Object();
    private final ReentrantLock cacheLock = new ReentrantLock(true); // 使用公平锁
    private static final long LOCK_TIMEOUT = 2000; // 2秒超时
    private byte[] cacheData = null;
    private int available_len;

    public void clearCacheData() {
        boolean locked = false;
        try {
            // 尝试获取锁，最多等待3秒
            locked = cacheLock.tryLock(LOCK_TIMEOUT, TimeUnit.MILLISECONDS);
            if (locked) {
                cacheData = null;
                available_len = 0;
            } else {
                MSLoggerUtils.getInstance().e(TAG, "获取锁超时，clearCacheData失败");
            }
        } catch (InterruptedException e) {
            MSLoggerUtils.getInstance().e(TAG, "clearCacheData等待锁被中断: " + e.getMessage());
            Thread.currentThread().interrupt();
        } finally {
            if (locked) {
                cacheLock.unlock();
            }
        }
    }

    synchronized void handlerOnlineBytes(INonBlockingConnection iNonBlockingConnection) {
        boolean locked = false;
        try {
            locked = cacheLock.tryLock(LOCK_TIMEOUT, TimeUnit.MILLISECONDS);
            if (!locked) {
                MSLoggerUtils.getInstance().e(TAG, "获取锁超时，handlerOnlineBytes失败");
                return;
            }
            
            try {
                // 获取可用数据长度
                available_len = iNonBlockingConnection.available();

                // 安全检查
                if (available_len <= 0) {
                    return;
                }

                // 限制单次最大读取大小为150kb
                int bufLen = 1024 / 2;

                // 分批读取数据
                while (available_len > 0) {
                    // 计算本次应该读取的长度
                    int readLen = Math.min(bufLen, available_len);
                    if (readLen <= 0) break;
                    // 读取数据前确保连接仍然有效
                    if (!iNonBlockingConnection.isOpen()) {
                        MSLoggerUtils.getInstance().e(TAG, "读取数据时连接关闭");
                        break;
                    }
                    // 读取数据
                    byte[] buffBytes = iNonBlockingConnection.readBytesByLength(readLen);
                    if (buffBytes != null && buffBytes.length > 0) {
                        MSConnection.getInstance().receivedData(buffBytes);
                        available_len -= buffBytes.length;
                    } else {
                        MSLoggerUtils.getInstance().e(TAG, "读取数据失败或收到空数据");
                        break;
                    }
                    // 给一个很小的延迟，避免过快读取
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException ignored) {
                    }
                }
            } catch (IOException e) {
                MSLoggerUtils.getInstance().e(TAG, "处理接收到的数据异常:" + e.getMessage());
                clearCacheData();
            } catch (Exception e) {
                MSLoggerUtils.getInstance().e(TAG, "onData 中发生意外错误: " + e.getMessage());
                clearCacheData();
            }
        } catch (InterruptedException e) {
            MSLoggerUtils.getInstance().e(TAG, "handlerOnlineBytes等待锁被中断: " + e.getMessage());
            Thread.currentThread().interrupt();
        } finally {
            if (locked) {
                cacheLock.unlock();
            }
        }
    }

    synchronized void cutBytes(byte[] available_bytes,
                               IReceivedMsgListener mIReceivedMsgListener) {
        boolean locked = false;
        try {
            locked = cacheLock.tryLock(LOCK_TIMEOUT, TimeUnit.MILLISECONDS);
            if (!locked) {
                MSLoggerUtils.getInstance().e(TAG, "获取锁超时，cutBytes失败");
                return;
            }

            if (cacheData == null || cacheData.length == 0) cacheData = available_bytes;
            else {
                //如果上次还存在未解析完的消息将新数据追加到缓存数据中
                byte[] temp = new byte[available_bytes.length + cacheData.length];
                try {
                    System.arraycopy(cacheData, 0, temp, 0, cacheData.length);
                    System.arraycopy(available_bytes, 0, temp, cacheData.length, available_bytes.length);
                    cacheData = temp;
                } catch (Exception e) {
                    MSLoggerUtils.getInstance().e(TAG, "处理粘包消息异常" + e.getMessage());
                    clearCacheData();
                    return;
                }
            }
            byte[] lastMsgBytes = cacheData;
            int readLength = 0;

            while (lastMsgBytes.length > 0 && readLength != lastMsgBytes.length) {
                readLength = lastMsgBytes.length;
                int packetType = MSTypeUtils.getInstance().getHeight4(lastMsgBytes[0]);
                // 是否不持久化：0。 是否显示红点：1。是否只同步一次：0
                //是否持久化[是否保存在数据库]
                int no_persist = MSTypeUtils.getInstance().getBit(lastMsgBytes[0], 0);
                //是否显示红点
                int red_dot = MSTypeUtils.getInstance().getBit(lastMsgBytes[0], 1);
                //是否只同步一次
                int sync_once = MSTypeUtils.getInstance().getBit(lastMsgBytes[0], 2);
                if (MSIM.getInstance().isDebug()) {
                    String packetTypeStr = "[其他]";
                    switch (packetType) {
                        case MSMsgType.CONNACK:
                            packetTypeStr = "[连接状态包]";
                            break;
                        case MSMsgType.SEND:
                            packetTypeStr = "[发送包]";
                            break;
                        case MSMsgType.RECEIVED:
                            packetTypeStr = "[收到消息包]";
                            break;
                        case MSMsgType.DISCONNECT:
                            packetTypeStr = "[断开连接包]";
                            break;
                        case MSMsgType.SENDACK:
                            packetTypeStr = "[发送回执包]";
                            break;
                        case MSMsgType.PONG:
                            packetTypeStr = "[心跳包]";
                            break;
                    }
                    String info = "是否不持续化：" + no_persist + "，是否显示红点：" + red_dot + "，是否只同步一次：" + sync_once;
                    MSLoggerUtils.getInstance().e(TAG, "收到包类型" + packetType + " " + packetTypeStr + "|" + info);
                }
                if (packetType == MSMsgType.REVACK || packetType == MSMsgType.SEND || packetType == MSMsgType.Reserved) {
                    MSConnection.getInstance().forcedReconnection();
                    return;
                }
                if (packetType == MSMsgType.PONG) {
                    //心跳ack
                    mIReceivedMsgListener.pongMsg(new MSPongMsg());
                    MSLoggerUtils.getInstance().e(TAG, "pong...");
                    byte[] bytes = Arrays.copyOfRange(lastMsgBytes, 1, lastMsgBytes.length);
                    cacheData = lastMsgBytes = bytes;
                } else {
                    if (packetType < 10) {
                        if (lastMsgBytes.length < 5) {
                            cacheData = lastMsgBytes;
                            break;
                        }
                        //其他消息类型
                        int remainingLength = MSTypeUtils.getInstance().getRemainingLength(Arrays.copyOfRange(lastMsgBytes, 1, lastMsgBytes.length));
                        if (remainingLength == -1) {
                            //剩余长度被分包
                            cacheData = lastMsgBytes;
                            break;
                        }
                        if (remainingLength > 1 << 21) {
                            cacheData = null;
                            break;
                        }
                        byte[] bytes = MSTypeUtils.getInstance().getRemainingLengthByte(remainingLength);
                        if (remainingLength + 1 + bytes.length > lastMsgBytes.length) {
                            //半包情况
                            cacheData = lastMsgBytes;
                        } else {
                            byte[] msg = Arrays.copyOfRange(lastMsgBytes, 0, remainingLength + 1 + bytes.length);
                            acceptMsg(msg, no_persist, sync_once, red_dot, mIReceivedMsgListener);
                            byte[] temps = Arrays.copyOfRange(lastMsgBytes, msg.length, lastMsgBytes.length);
                            cacheData = lastMsgBytes = temps;
                        }
                    } else {
                        cacheData = null;
                        mIReceivedMsgListener.reconnect();
                        break;
                    }
                }
            }
            saveReceiveMsg();
        } catch (InterruptedException e) {
            MSLoggerUtils.getInstance().e(TAG, "cutBytes等待锁被中断: " + e.getMessage());
            Thread.currentThread().interrupt();
        } finally {
            if (locked) {
                cacheLock.unlock();
            }
        }
    }

    private void acceptMsg(byte[] bytes, int no_persist, int sync_once, int red_dot,
                           IReceivedMsgListener mIReceivedMsgListener) {

        if (bytes != null && bytes.length > 0) {
            MSBaseMsg g_msg;
            g_msg = MSProto.getInstance().decodeMessage(bytes);
            if (g_msg != null) {
                //连接ack
                if (g_msg.packetType == MSMsgType.CONNACK) {
                    MSConnectAckMsg loginStatusMsg = (MSConnectAckMsg) g_msg;
                    mIReceivedMsgListener.loginStatusMsg(loginStatusMsg);
                } else if (g_msg.packetType == MSMsgType.SENDACK) {
                    //发送ack
                    MSSendAckMsg sendAckMsg = (MSSendAckMsg) g_msg;
                    MSMsg msMsg = null;
                    if (no_persist == 0) {
                        msMsg = MsgDbManager.getInstance().updateMsgSendStatus(sendAckMsg.clientSeq, sendAckMsg.messageSeq, sendAckMsg.messageID, sendAckMsg.reasonCode);
                    }
                    if (msMsg == null) {
                        msMsg = new MSMsg();
                        msMsg.clientSeq = sendAckMsg.clientSeq;
                        msMsg.messageID = sendAckMsg.messageID;
                        msMsg.status = sendAckMsg.reasonCode;
                        msMsg.messageSeq = (int) sendAckMsg.messageSeq;
                    }
                    MSIM.getInstance().getMsgManager().setSendMsgAck(msMsg);

                    mIReceivedMsgListener
                            .sendAckMsg(sendAckMsg);
                } else if (g_msg.packetType == MSMsgType.RECEIVED) {
                    //收到消息
                    MSMsg message = MSProto.getInstance().baseMsg2MSMsg(g_msg);
                    message.header.noPersist = no_persist == 1;
                    message.header.redDot = red_dot == 1;
                    message.header.syncOnce = sync_once == 1;
                    handleReceiveMsg(message);
                    // mIReceivedMsgListener.receiveMsg(message);
                } else if (g_msg.packetType == MSMsgType.DISCONNECT) {
                    //被踢消息
                    MSDisconnectMsg disconnectMsg = (MSDisconnectMsg) g_msg;
                    mIReceivedMsgListener.kickMsg(disconnectMsg);
                } else if (g_msg.packetType == MSMsgType.PONG) {
                    mIReceivedMsgListener.pongMsg((MSPongMsg) g_msg);
                }
            } else {
                mIReceivedMsgListener.reconnect();
            }
        }
    }

    private void handleReceiveMsg(MSMsg message) {
        message = parsingMsg(message);
        if (message.type != MSMsgContentType.MS_INSIDE_MSG) {
            addReceivedMsg(message);
        } else {
            MSReceivedAckMsg receivedAckMsg = getReceivedAckMsg(message);
            receivedAckMsgList.add(receivedAckMsg);
        }
    }

    private MSReceivedAckMsg getReceivedAckMsg(MSMsg message) {
        MSReceivedAckMsg receivedAckMsg = new MSReceivedAckMsg();
        receivedAckMsg.messageID = message.messageID;
        receivedAckMsg.messageSeq = message.messageSeq;
        receivedAckMsg.no_persist = message.header.noPersist;
        receivedAckMsg.red_dot = message.header.redDot;
        receivedAckMsg.sync_once = message.header.syncOnce;
        return receivedAckMsg;
    }

    private void addReceivedMsg(MSMsg msg) {
        synchronized (receivedMsgListLock) {
            if (receivedMsgList == null) {
                receivedMsgList = new ArrayList<>();
            }
            MSSyncMsg syncMsg = new MSSyncMsg();
            syncMsg.no_persist = msg.header.noPersist ? 1 : 0;
            syncMsg.sync_once = msg.header.syncOnce ? 1 : 0;
            syncMsg.red_dot = msg.header.redDot ? 1 : 0;
            syncMsg.msMsg = msg;
            receivedMsgList.add(syncMsg);
        }
    }

    public void saveReceiveMsg() {
        List<MSSyncMsg> tempList = null;
        synchronized (receivedMsgListLock) {
            if (MSCommonUtils.isNotEmpty(receivedMsgList)) {
                tempList = new ArrayList<>(receivedMsgList);
                receivedMsgList.clear();
            }
        }

        if (tempList != null) {
            saveSyncMsg(tempList);
            synchronized (receivedAckMsgList) {
                for (MSSyncMsg syncMsg : tempList) {
                    MSReceivedAckMsg receivedAckMsg = getReceivedAckMsg(syncMsg.msMsg);
                    receivedAckMsgList.add(receivedAckMsg);
                }
            }
            sendAck();
        }
    }

    private final Handler sendAckHandler = new Handler(Looper.getMainLooper());
    private final Runnable sendAckRunnable = new Runnable() {
        @Override
        public void run() {
            // 检查连接状态
            if (MSConnection.getInstance().connectionIsNull() || MSConnection.getInstance().isReConnecting) {
                // 连接断开，取消所有待发送的消息
                sendAckHandler.removeCallbacks(this);
                return;
            }

            synchronized (receivedAckMsgList) {
                if (!receivedAckMsgList.isEmpty()) {
                    MSConnection.getInstance().sendMessage(receivedAckMsgList.get(0));
                    receivedAckMsgList.remove(0);
                    // 如果列表不为空，继续发送下一条
                    if (!receivedAckMsgList.isEmpty()) {
                        sendAckHandler.postDelayed(this, 100);
                    }
                }
            }
        }
    };

    //回复消息ack
    public void sendAck() {
        if (MSConnection.getInstance().connectionIsNull() || MSConnection.getInstance().isReConnecting) {
            return;
        }
        synchronized (receivedAckMsgList) {
            if (receivedAckMsgList.isEmpty()) {
                return;
            }
            if (receivedAckMsgList.size() == 1) {
                MSConnection.getInstance().sendMessage(receivedAckMsgList.get(0));
                receivedAckMsgList.clear();
                return;
            }
            // 移除所有待发送的消息，避免重复发送
            sendAckHandler.removeCallbacks(sendAckRunnable);
            // 开始发送消息
            sendAckHandler.post(sendAckRunnable);
        }
    }

    // 在需要清理资源的地方（比如onDestroy）调用此方法
    public void destroy() {
        if (sendAckHandler != null) {
            sendAckHandler.removeCallbacks(sendAckRunnable);
        }
    }

    /**
     * 保存同步消息
     *
     * @param list 同步消息对象
     */
    public synchronized void saveSyncMsg(List<MSSyncMsg> list) {
        List<MSMsg> saveMsgList = new ArrayList<>();
        List<MSMsg> allList = new ArrayList<>();
        for (MSSyncMsg mMsg : list) {
            if (mMsg.no_persist == 0 && mMsg.sync_once == 0) {
                saveMsgList.add(mMsg.msMsg);
            }
            allList.add(mMsg.msMsg);
        }
        MsgDbManager.getInstance().insertMsgs(saveMsgList);
        //将消息push给UI
        MSIM.getInstance().getMsgManager().pushNewMsg(allList);
        groupMsg(list);
    }

    private void groupMsg(List<MSSyncMsg> list) {
        LinkedHashMap<String, SavedMsg> savedList = new LinkedHashMap<>();
        //再将消息分组
        for (int i = 0, size = list.size(); i < size; i++) {
            MSMsg lastMsg = null;
            int count;

            if (list.get(i).msMsg.channelType == MSChannelType.PERSONAL) {
                //如果是单聊先将channelId改成发送者ID
                if (!TextUtils.isEmpty(list.get(i).msMsg.channelID) && !TextUtils.isEmpty(list.get(i).msMsg.fromUID) && list.get(i).msMsg.channelID.equals(MSIMApplication.getInstance().getUid())) {
                    list.get(i).msMsg.channelID = list.get(i).msMsg.fromUID;
                }
            }

            //将要存库的最后一条消息更新到会话记录表
            if (list.get(i).no_persist == 0
                    && list.get(i).msMsg.type != MSMsgContentType.MS_INSIDE_MSG
                    && list.get(i).msMsg.isDeleted == 0) {
                lastMsg = list.get(i).msMsg;
            }
            count = list.get(i).red_dot;
            if (lastMsg == null) {
                continue;
            }

            lastMsg = parsingMsg(lastMsg);
            boolean isSave = false;
            if (lastMsg.baseContentMsgModel != null && lastMsg.baseContentMsgModel.mentionAll == 1 && list.get(i).red_dot == 1) {
                isSave = true;
            } else {
                if (lastMsg.baseContentMsgModel != null && lastMsg.baseContentMsgModel.mentionInfo != null && MSCommonUtils.isNotEmpty(lastMsg.baseContentMsgModel.mentionInfo.uids) && count == 1) {
                    for (int j = 0, len = lastMsg.baseContentMsgModel.mentionInfo.uids.size(); j < len; j++) {
                        if (!TextUtils.isEmpty(lastMsg.baseContentMsgModel.mentionInfo.uids.get(j)) && !TextUtils.isEmpty(MSIMApplication.getInstance().getUid()) && lastMsg.baseContentMsgModel.mentionInfo.uids.get(j).equalsIgnoreCase(MSIMApplication.getInstance().getUid())) {
                            isSave = true;
                        }
                    }
                }
            }
            if (isSave) {
                //如果存在@情况直接将消息存储
                MSUIConversationMsg conversationMsg = ConversationDbManager.getInstance().insertOrUpdateWithMsg(lastMsg, 1);
                MSIM.getInstance().getConversationManager().setOnRefreshMsg(conversationMsg, "cutData");
                continue;
            }

            SavedMsg savedMsg = null;
            if (savedList.containsKey(lastMsg.channelID + "_" + lastMsg.channelType)) {
                savedMsg = savedList.get(lastMsg.channelID + "_" + lastMsg.channelType);
            }
            if (savedMsg == null) {
                savedMsg = new SavedMsg(lastMsg, count);
            } else {
                savedMsg.msMsg = lastMsg;
                savedMsg.redDot = savedMsg.redDot + count;
            }
            savedList.put(lastMsg.channelID + "_" + lastMsg.channelType, savedMsg);
        }

        List<MSUIConversationMsg> refreshList = new ArrayList<>();
        for (Map.Entry<String, SavedMsg> entry : savedList.entrySet()) {
            MSUIConversationMsg conversationMsg = ConversationDbManager.getInstance().insertOrUpdateWithMsg(entry.getValue().msMsg, entry.getValue().redDot);
            if (conversationMsg != null) {
                refreshList.add(conversationMsg);
            }
        }
        MSIM.getInstance().getConversationManager().setOnRefreshMsg(refreshList, "groupMsg");
    }

    public MSMsg parsingMsg(MSMsg message) {
        if (message.type == MSMsgContentType.MS_SIGNAL_DECRYPT_ERROR || message.type == MSMsgContentType.MS_CONTENT_FORMAT_ERROR) {
            return message;
        }
        JSONObject json = null;
        try {
            if (TextUtils.isEmpty(message.content)) return message;
            json = new JSONObject(message.content);
            if (json.has("type")) {
                message.content = json.toString();
                message.type = json.optInt("type");
            }
            if (TextUtils.isEmpty(message.fromUID)) {
                if (json.has("from_uid")) {
                    message.fromUID = json.optString("from_uid");
                } else {
                    message.fromUID = message.channelID;
                }
            }
            if (json.has("flame")) {
                message.flame = json.optInt("flame");
            }
            if (json.has("flame_second")) {
                message.flameSecond = json.optInt("flame_second");
            }
            if (json.has("root_id")) {
                message.robotID = json.optString("root_id");
            }
        } catch (JSONException e) {
            message.type = MSMsgContentType.MS_CONTENT_FORMAT_ERROR;
            MSLoggerUtils.getInstance().e(TAG, "消息体非json");
        }

        if (json == null) {
            if (message.type != MSMsgContentType.MS_SIGNAL_DECRYPT_ERROR)
                message.type = MSMsgContentType.MS_CONTENT_FORMAT_ERROR;
        }

        if (message.type == MSMsgContentType.MS_INSIDE_MSG) {
            CMDManager.getInstance().handleCMD(json, message.channelID, message.channelType);
            return message;
        }

        message.baseContentMsgModel = MSIM.getInstance().getMsgManager().getMsgContentModel(message.type, json);
        //如果是单聊先将channelId改成发送者ID
        if (!TextUtils.isEmpty(message.channelID)
                && !TextUtils.isEmpty(message.fromUID)
                && message.channelType == MSChannelType.PERSONAL
                && message.channelID.equals(MSIMApplication.getInstance().getUid())) {
            message.channelID = message.fromUID;
        }
        return message;
    }

    public void updateLastSendingMsgFail() {
        MsgDbManager.getInstance().updateAllMsgSendFail();
    }
}
