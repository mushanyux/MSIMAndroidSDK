package com.mushanyux.mushanim.message;

class MSTimers {
    private MSTimers() {
    }

    private static class ConnectionTimerHandlerBinder {
        static final MSTimers timeHandle = new MSTimers();
    }

    public static MSTimers getInstance() {
        return ConnectionTimerHandlerBinder.timeHandle;
    }

}
