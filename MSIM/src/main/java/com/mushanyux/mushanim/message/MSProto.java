package com.mushanyux.mushanim.message;

import android.text.TextUtils;

import com.mushanyux.mushanim.MSIM;
import com.mushanyux.mushanim.MSIMApplication;
import com.mushanyux.mushanim.db.MSDBColumns;
import com.mushanyux.mushanim.entity.MSMsg;
import com.mushanyux.mushanim.message.type.MSMsgType;
import com.mushanyux.mushanim.message.type.MSSendMsgResult;
import com.mushanyux.mushanim.msgmodel.MSMediaMessageContent;
import com.mushanyux.mushanim.msgmodel.MSMessageContent;
import com.mushanyux.mushanim.msgmodel.MSMsgEntity;
import com.mushanyux.mushanim.protocol.MSBaseMsg;
import com.mushanyux.mushanim.protocol.MSConnectAckMsg;
import com.mushanyux.mushanim.protocol.MSConnectMsg;
import com.mushanyux.mushanim.protocol.MSDisconnectMsg;
import com.mushanyux.mushanim.protocol.MSPingMsg;
import com.mushanyux.mushanim.protocol.MSPongMsg;
import com.mushanyux.mushanim.protocol.MSReceivedAckMsg;
import com.mushanyux.mushanim.protocol.MSReceivedMsg;
import com.mushanyux.mushanim.protocol.MSSendAckMsg;
import com.mushanyux.mushanim.protocol.MSSendMsg;
import com.mushanyux.mushanim.utils.CryptoUtils;
import com.mushanyux.mushanim.utils.MSCommonUtils;
import com.mushanyux.mushanim.utils.MSLoggerUtils;
import com.mushanyux.mushanim.utils.MSTypeUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;

class MSProto {
    private final String TAG = "MSProto";

    private MSProto() {
    }

    private static class MessageConvertHandlerBinder {
        static final MSProto msgConvert = new MSProto();
    }

    public static MSProto getInstance() {
        return MessageConvertHandlerBinder.msgConvert;
    }

    byte[] encodeMsg(MSBaseMsg msg) {
        byte[] bytes = null;
        if (msg.packetType == MSMsgType.CONNECT) {
            // 连接
            bytes = MSProto.getInstance().enConnectMsg((MSConnectMsg) msg);
        } else if (msg.packetType == MSMsgType.REVACK) {
            // 收到消息回执
            bytes = MSProto.getInstance().enReceivedAckMsg((MSReceivedAckMsg) msg);
        } else if (msg.packetType == MSMsgType.SEND) {
            // 发送聊天消息
            bytes = MSProto.getInstance().enSendMsg((MSSendMsg) msg);
        } else if (msg.packetType == MSMsgType.PING) {
            // 发送心跳
            bytes = MSProto.getInstance().enPingMsg((MSPingMsg) msg);
            MSLoggerUtils.getInstance().e("ping...");
        }
        return bytes;
    }

    byte[] enConnectMsg(MSConnectMsg connectMsg) {
        CryptoUtils.getInstance().initKey();
        byte[] remainingBytes = MSTypeUtils.getInstance().getRemainingLengthByte(connectMsg.getRemainingLength());
        int totalLen = connectMsg.getTotalLen();
        MSWrite msWrite = new MSWrite(totalLen);
        try {
            msWrite.writeByte(MSTypeUtils.getInstance().getHeader(connectMsg.packetType, connectMsg.flag, 0, 0));
            msWrite.writeBytes(remainingBytes);
            msWrite.writeByte(MSIMApplication.getInstance().protocolVersion);
            msWrite.writeByte(connectMsg.deviceFlag);
            msWrite.writeString(connectMsg.deviceID);
            msWrite.writeString(MSIMApplication.getInstance().getUid());
            msWrite.writeString(MSIMApplication.getInstance().getToken());
            msWrite.writeLong(connectMsg.clientTimestamp);
            msWrite.writeString(CryptoUtils.getInstance().getPublicKey());
        } catch (UnsupportedEncodingException e) {
            MSLoggerUtils.getInstance().e(TAG, "编码连接包错误");
        }
        return msWrite.getWriteBytes();
    }

    synchronized byte[] enReceivedAckMsg(MSReceivedAckMsg receivedAckMsg) {
        byte[] remainingBytes = MSTypeUtils.getInstance().getRemainingLengthByte(8 + 4);

        int totalLen = 1 + remainingBytes.length + 8 + 4;
        MSWrite msWrite = new MSWrite(totalLen);
        msWrite.writeByte(MSTypeUtils.getInstance().getHeader(receivedAckMsg.packetType, receivedAckMsg.no_persist ? 1 : 0, receivedAckMsg.red_dot ? 1 : 0, receivedAckMsg.sync_once ? 1 : 0));
        msWrite.writeBytes(remainingBytes);
        BigInteger bigInteger = new BigInteger(receivedAckMsg.messageID);
        msWrite.writeLong(bigInteger.longValue());
        msWrite.writeInt(receivedAckMsg.messageSeq);
        return msWrite.getWriteBytes();
    }

    byte[] enPingMsg(MSPingMsg pingMsg) {
        MSWrite msWrite = new MSWrite(1);
        msWrite.writeByte(MSTypeUtils.getInstance().getHeader(pingMsg.packetType, pingMsg.flag, 0, 0));
        return msWrite.getWriteBytes();
    }

    byte[] enSendMsg(MSSendMsg sendMsg) {
        // 先加密内容
        String sendContent = sendMsg.getSendContent();
        String msgKeyContent = sendMsg.getMsgKey();
        byte[] remainingBytes = MSTypeUtils.getInstance().getRemainingLengthByte(sendMsg.getRemainingLength());
        int totalLen = sendMsg.getTotalLength();
        MSWrite msWrite = new MSWrite(totalLen);
        try {
            msWrite.writeByte(MSTypeUtils.getInstance().getHeader(sendMsg.packetType, sendMsg.no_persist ? 1 : 0, sendMsg.red_dot ? 1 : 0, sendMsg.sync_once ? 1 : 0));
            msWrite.writeBytes(remainingBytes);
            msWrite.writeByte(MSTypeUtils.getInstance().getMsgSetting(sendMsg.setting));
            msWrite.writeInt(sendMsg.clientSeq);
            msWrite.writeString(sendMsg.clientMsgNo);
            msWrite.writeString(sendMsg.channelId);
            msWrite.writeByte(sendMsg.channelType);
            if (MSIMApplication.getInstance().protocolVersion >= 3) {
                msWrite.writeInt(sendMsg.expire);
            }
            msWrite.writeString(msgKeyContent);
            if (sendMsg.setting.topic == 1) {
                msWrite.writeString(sendMsg.topicID);
            }
            msWrite.writePayload(sendContent);

        } catch (UnsupportedEncodingException e) {
            MSLoggerUtils.getInstance().e(TAG, "编码发送包错误");
        }
        return msWrite.getWriteBytes();
    }

    private MSConnectAckMsg deConnectAckMsg(MSRead msRead, int hasServerVersion) {
        MSConnectAckMsg connectAckMsg = new MSConnectAckMsg();
        try {
            if (hasServerVersion == 1) {
                connectAckMsg.serviceProtoVersion = msRead.readByte();
               // byte serverVersion = msRead.readByte();
                if (connectAckMsg.serviceProtoVersion != 0) {
                    MSIMApplication.getInstance().protocolVersion = (byte) Math.min(connectAckMsg.serviceProtoVersion, MSIMApplication.getInstance().protocolVersion);
                }
            }
            long time = msRead.readLong();
            short reasonCode = msRead.readByte();
            String serverKey = msRead.readString();
            String salt = msRead.readString();
            if (connectAckMsg.serviceProtoVersion >= 4){
                connectAckMsg.nodeId = (int) msRead.readLong();
            }
            connectAckMsg.serverKey = serverKey;
            connectAckMsg.salt = salt;
            //保存公钥和安全码
            CryptoUtils.getInstance().setServerKeyAndSalt(connectAckMsg.serverKey, connectAckMsg.salt);
            connectAckMsg.timeDiff = time;
            connectAckMsg.reasonCode = reasonCode;
        } catch (IOException e) {
            MSLoggerUtils.getInstance().e(TAG, "解码连接ack包错误");
        }

        return connectAckMsg;
    }

    private MSSendAckMsg deSendAckMsg(MSRead msRead) {
        MSSendAckMsg sendAckMsg = new MSSendAckMsg();
        try {
            sendAckMsg.messageID = msRead.readMsgID();
            sendAckMsg.clientSeq = msRead.readInt();
            sendAckMsg.messageSeq = msRead.readInt();
            sendAckMsg.reasonCode = msRead.readByte();
        } catch (IOException e) {
            MSLoggerUtils.getInstance().e(TAG, "解码发送ack错误");
        }
        return sendAckMsg;
    }

    private MSDisconnectMsg deDisconnectMsg(MSRead msRead) {
        MSDisconnectMsg disconnectMsg = new MSDisconnectMsg();
        try {
            disconnectMsg.reasonCode = msRead.readByte();
            disconnectMsg.reason = msRead.readString();
            MSLoggerUtils.getInstance().e(TAG, "断开消息code:" + disconnectMsg.reasonCode + ",reason:" + disconnectMsg.reason);
            return disconnectMsg;
        } catch (IOException e) {
            MSLoggerUtils.getInstance().e(TAG, "解码断开包错误");
        }
        return disconnectMsg;
    }

    private MSReceivedMsg deReceivedMsg(MSRead msRead) {
        MSReceivedMsg receivedMsg = new MSReceivedMsg();
        try {
            byte settingByte = msRead.readByte();
            receivedMsg.setting = MSTypeUtils.getInstance().getMsgSetting(settingByte);
            receivedMsg.msgKey = msRead.readString();
            receivedMsg.fromUID = msRead.readString();
            receivedMsg.channelID = msRead.readString();
            receivedMsg.channelType = msRead.readByte();
            if (MSIMApplication.getInstance().protocolVersion >= 3) {
                receivedMsg.expire = msRead.readInt();
            }
            receivedMsg.clientMsgNo = msRead.readString();
            if (receivedMsg.setting.stream == 1) {
                receivedMsg.streamNO = msRead.readString();
                receivedMsg.streamSeq = msRead.readInt();
                receivedMsg.streamFlag = msRead.readByte();
            }
            receivedMsg.messageID = msRead.readMsgID();
            receivedMsg.messageSeq = msRead.readInt();
            receivedMsg.messageTimestamp = msRead.readInt();
            if (receivedMsg.setting.topic == 1) {
                receivedMsg.topicID = msRead.readString();
            }
            String content = msRead.readPayload();
            String msgKey = receivedMsg.messageID
                    + receivedMsg.messageSeq
                    + receivedMsg.clientMsgNo
                    + receivedMsg.messageTimestamp
                    + receivedMsg.fromUID
                    + receivedMsg.channelID
                    + receivedMsg.channelType
                    + content;
            byte[] result = CryptoUtils.getInstance().aesEncrypt(msgKey);
            if (result == null) {
                return null;
            }
            String base64Result = CryptoUtils.getInstance().base64Encode(result);
            String localMsgKey = CryptoUtils.getInstance().digestMD5(base64Result);
            if (!localMsgKey.equals(receivedMsg.msgKey)) {
                MSLoggerUtils.getInstance().e("非法消息,本地消息key:" + localMsgKey + ",期望key:" + msgKey);
                return null;
            }
            receivedMsg.payload = CryptoUtils.getInstance().aesDecrypt(CryptoUtils.getInstance().base64Decode(content));
            MSLoggerUtils.getInstance().e(receivedMsg.toString());
            return receivedMsg;
        } catch (IOException e) {
            MSLoggerUtils.getInstance().e(TAG, "解码收到的消息错误");
            return null;
        }
    }

    MSBaseMsg decodeMessage(byte[] bytes) {
        try {
            MSRead msRead = new MSRead(bytes);
            int packetType = msRead.readPacketType();
            msRead.readRemainingLength();
            if (packetType == MSMsgType.CONNACK) {
                int hasServerVersion = MSTypeUtils.getInstance().getBit(bytes[0], 0);
                return deConnectAckMsg(msRead, hasServerVersion);
            } else if (packetType == MSMsgType.SENDACK) {
                return deSendAckMsg(msRead);
            } else if (packetType == MSMsgType.DISCONNECT) {
                return deDisconnectMsg(msRead);
            } else if (packetType == MSMsgType.RECEIVED) {
                return deReceivedMsg(msRead);
            } else if (packetType == MSMsgType.PONG) {
                return new MSPongMsg();
            } else {
                MSLoggerUtils.getInstance().e("解码未知消息包类型：" + packetType);
                return null;
            }
        } catch (IOException e) {
            MSLoggerUtils.getInstance().e("解码消息错误：" + e.getMessage());
            return null;
        }
    }

    JSONObject getSendPayload(MSMsg msg) {
        JSONObject jsonObject = null;
        if (msg.baseContentMsgModel != null) {
            jsonObject = msg.baseContentMsgModel.encodeMsg();
        } else {
            msg.baseContentMsgModel = new MSMessageContent();
        }
        try {
            if (jsonObject == null) jsonObject = new JSONObject();
            jsonObject.put(MSDBColumns.MSMessageColumns.type, msg.type);
            //判断@情况
            if (msg.baseContentMsgModel.mentionInfo != null
                    && msg.baseContentMsgModel.mentionInfo.uids != null
                    && !msg.baseContentMsgModel.mentionInfo.uids.isEmpty()) {
                JSONArray jsonArray = new JSONArray();
                for (int i = 0, size = msg.baseContentMsgModel.mentionInfo.uids.size(); i < size; i++) {
                    jsonArray.put(msg.baseContentMsgModel.mentionInfo.uids.get(i));
                }
                if (!jsonObject.has("mention")) {
                    JSONObject mentionJson = new JSONObject();
                    mentionJson.put("all", msg.baseContentMsgModel.mentionAll);
                    mentionJson.put("uids", jsonArray);
                    jsonObject.put("mention", mentionJson);
                }

            } else {
                if (msg.baseContentMsgModel.mentionAll == 1) {
                    JSONObject mentionJson = new JSONObject();
                    mentionJson.put("all", msg.baseContentMsgModel.mentionAll);
                    jsonObject.put("mention", mentionJson);
                }
            }
            // 被回复消息
            if (msg.baseContentMsgModel.reply != null) {
                jsonObject.put("reply", msg.baseContentMsgModel.reply.encodeMsg());
            }
            // 机器人ID
            if (!TextUtils.isEmpty(msg.baseContentMsgModel.robotID)) {
                jsonObject.put("robot_id", msg.baseContentMsgModel.robotID);
            }
            if (!TextUtils.isEmpty(msg.robotID)) {
                jsonObject.put("robot_id", msg.robotID);
            }
            if (MSCommonUtils.isNotEmpty(msg.baseContentMsgModel.entities)) {
                JSONArray jsonArray = new JSONArray();
                for (MSMsgEntity entity : msg.baseContentMsgModel.entities) {
                    JSONObject jo = new JSONObject();
                    jo.put("offset", entity.offset);
                    jo.put("length", entity.length);
                    jo.put("type", entity.type);
                    jo.put("value", entity.value);
                    jsonArray.put(jo);
                }
                jsonObject.put("entities", jsonArray);
            }
            if (msg.flame != 0) {
                jsonObject.put("flame_second", msg.flameSecond);
                jsonObject.put("flame", msg.flame);
            }
        } catch (JSONException e) {
            MSLoggerUtils.getInstance().e(TAG, "获取消息体错误");
        }
        return jsonObject;
    }

    /**
     * 获取发送的消息
     *
     * @param msg 本地消息
     * @return 网络消息
     */
    MSSendMsg getSendBaseMsg(MSMsg msg) {
        //发送消息
        JSONObject jsonObject = getSendPayload(msg);
        MSSendMsg sendMsg = new MSSendMsg();
        // 默认先设置clientSeq，因为有可能本条消息并不需要入库，UI上自己设置了clientSeq
        sendMsg.clientSeq = (int) msg.clientSeq;
        sendMsg.sync_once = msg.header.syncOnce;
        sendMsg.no_persist = msg.header.noPersist;
        sendMsg.red_dot = msg.header.redDot;
        sendMsg.clientMsgNo = msg.clientMsgNO;
        sendMsg.channelId = msg.channelID;
        sendMsg.channelType = msg.channelType;
        sendMsg.topicID = msg.topicID;
        sendMsg.setting = msg.setting;
        sendMsg.expire = msg.expireTime;
        if (MSMediaMessageContent.class.isAssignableFrom(msg.baseContentMsgModel.getClass())) {
            //多媒体数据
            if (jsonObject.has("localPath")) {
                jsonObject.remove("localPath");
            }
            //视频地址
            if (jsonObject.has("videoLocalPath")) {
                jsonObject.remove("videoLocalPath");
            }
        }
        sendMsg.payload = jsonObject.toString();
        return sendMsg;
    }

    MSMsg baseMsg2MSMsg(MSBaseMsg baseMsg) {
        MSReceivedMsg receivedMsg = (MSReceivedMsg) baseMsg;
        MSMsg msg = new MSMsg();
        msg.channelType = receivedMsg.channelType;
        msg.channelID = receivedMsg.channelID;
        msg.content = receivedMsg.payload;
        msg.messageID = receivedMsg.messageID;
        msg.messageSeq = receivedMsg.messageSeq;
        msg.timestamp = receivedMsg.messageTimestamp;
        msg.fromUID = receivedMsg.fromUID;
        msg.setting = receivedMsg.setting;
        msg.clientMsgNO = receivedMsg.clientMsgNo;
        msg.status = MSSendMsgResult.send_success;
        msg.topicID = receivedMsg.topicID;
        msg.expireTime = receivedMsg.expire;
        if (msg.expireTime > 0) {
            msg.expireTimestamp = msg.expireTime + msg.timestamp;
        }
        msg.orderSeq = MSIM.getInstance().getMsgManager().getMessageOrderSeq(msg.messageSeq, msg.channelID, msg.channelType);
        msg.isDeleted = isDelete(msg.content);
        return msg;
    }

    private int isDelete(String contentJson) {
        int isDelete = 0;
        if (!TextUtils.isEmpty(contentJson)) {
            try {
                JSONObject jsonObject = new JSONObject(contentJson);
                isDelete = MSIM.getInstance().getMsgManager().isDeletedMsg(jsonObject);
            } catch (JSONException e) {
                MSLoggerUtils.getInstance().e(TAG, "获取消息是否删除时发现消息体非json");
            }
        }
        return isDelete;
    }
}
