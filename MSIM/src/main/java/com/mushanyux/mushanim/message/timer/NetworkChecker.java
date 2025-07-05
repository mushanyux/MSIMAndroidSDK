package com.mushanyux.mushanim.message.timer;

import com.mushanyux.mushanim.MSIM;
import com.mushanyux.mushanim.MSIMApplication;
import com.mushanyux.mushanim.message.MSConnection;
import com.mushanyux.mushanim.message.type.MSConnectReason;
import com.mushanyux.mushanim.message.type.MSConnectStatus;
import com.mushanyux.mushanim.utils.MSLoggerUtils;

public class NetworkChecker {
    private final Object lock = new Object(); // 添加锁对象
    public boolean isForcedReconnect;
    public boolean checkNetWorkTimerIsRunning = false;

    public void startNetworkCheck() {
        TimerManager.getInstance().addTask(
                TimerTasks.NETWORK_CHECK,
                () -> {
                    synchronized (lock) {
                        checkNetworkStatus();
                    }
                },
                0,
                1000
        );
    }

    private void checkNetworkStatus() {
        boolean is_have_network = MSIMApplication.getInstance().isNetworkConnected();
        if (!is_have_network) {
            isForcedReconnect = true;
            MSIM.getInstance().getConnectionManager().setConnectionStatus(MSConnectStatus.noNetwork, MSConnectReason.NoNetwork);
            MSLoggerUtils.getInstance().e("无网络连接...");
            MSConnection.getInstance().checkSendingMsg();
        } else {
            //有网络
            if (MSConnection.getInstance().connectionIsNull() || isForcedReconnect) {
                MSConnection.getInstance().reconnection();
                isForcedReconnect = false;
            }
        }
        checkNetWorkTimerIsRunning = true;
    }
}
