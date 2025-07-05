package com.mushanyux.mushanim.message.timer;

import com.mushanyux.mushanim.message.MSConnection;
import com.mushanyux.mushanim.protocol.MSPingMsg;

import java.util.concurrent.locks.ReentrantLock;

public class HeartbeatManager {
    private final ReentrantLock heartbeatLock = new ReentrantLock();
    public void startHeartbeat() {
        TimerManager.getInstance().addTask(
                TimerTasks.HEARTBEAT,
                () -> {
                    heartbeatLock.lock();
                    try {
                        MSConnection.getInstance().sendMessage(new MSPingMsg());
                    } finally {
                        heartbeatLock.unlock();
                    }
                },
                0,
                1000 * 60
        );
    }
}
