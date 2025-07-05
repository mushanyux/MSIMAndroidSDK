package com.mushanyux.mushanim.interfaces;


import com.mushanyux.mushanim.msgmodel.MSMessageContent;

public interface IUploadAttacResultListener {
    /**
     * 上传附件返回结果
     **/
    void onUploadResult(boolean isSuccess, MSMessageContent messageContent);
}
