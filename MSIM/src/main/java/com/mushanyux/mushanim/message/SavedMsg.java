package com.mushanyux.mushanim.message;


import com.mushanyux.mushanim.entity.MSMsg;

class SavedMsg {
    public MSMsg msMsg;
    public int redDot;

    public SavedMsg(MSMsg msg, int redDot) {
        this.redDot = redDot;
        this.msMsg = msg;
    }
}
