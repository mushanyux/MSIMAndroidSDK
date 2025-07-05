package com.mushanyux.mushanim.message.type;

public class MSMsgContentType {
    //文本
    public static final int MS_TEXT = 1;
    //图片
    public static final int MS_IMAGE = 2;
    //GIF
    public static final int MS_GIF = 3;
    //语音
    public static final int MS_VOICE = 4;
    //视频
    public static final int MS_VIDEO = 5;
    //位置
    public static final int MS_LOCATION = 6;
    //名片
    public static final int MS_CARD = 7;
    //文件
    public static final int MS_FILE = 8;
    //合并转发消息
    public static final int MS_MULTIPLE_FORWARD = 11;
    //矢量贴图
    public static final int MS_VECTOR_STICKER = 12;
    //emoji 贴图
    public static final int MS_EMOJI_STICKER = 13;
    // content 格式错误
    public static final int MS_CONTENT_FORMAT_ERROR = 97;
    // signal 解密失败
    public static final int MS_SIGNAL_DECRYPT_ERROR = 98;
    //内部消息，无需存储到数据库
    public static final int MS_INSIDE_MSG = 99;
}
