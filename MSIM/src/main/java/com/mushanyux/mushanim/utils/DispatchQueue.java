/*
 * This is the source code of Telegram for Android v. 7.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 */

package com.mushanyux.mushanim.utils;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;

import java.util.concurrent.CountDownLatch;

class DispatchQueue extends Thread {
    private volatile Handler handler = null;
    private final CountDownLatch syncLatch = new CountDownLatch(1);
    private long lastTaskTime;

    public DispatchQueue(final String threadName) {
        this(threadName, true);
    }

    public DispatchQueue(final String threadName, boolean start) {
        setName(threadName);
        if (start) {
            start();
        }
    }

    public void sendMessage(Message msg, int delay) {
        try {
            syncLatch.await();
            if (delay <= 0) {
                handler.sendMessage(msg);
            } else {
                handler.sendMessageDelayed(msg, delay);
            }
        } catch (Exception ignore) {

        }
    }

    public void cancelRunnable(Runnable runnable) {
        try {
            syncLatch.await();
            handler.removeCallbacks(runnable);
        } catch (Exception e) {
            //FileLog.e(e);
        }
    }

    public void cancelRunnables(Runnable[] runnables) {
        try {
            syncLatch.await();
            for (int i = 0; i < runnables.length; i++) {
                handler.removeCallbacks(runnables[i]);
            }
        } catch (Exception e) {
            //FileLog.e(e);
        }
    }

    public boolean postRunnable(Runnable runnable) {
        lastTaskTime = SystemClock.elapsedRealtime();
        return postRunnable(runnable, 0);
    }

    public boolean postRunnable(Runnable runnable, long delay) {
        try {
            syncLatch.await();
        } catch (Exception e) {
            //FileLog.e(e);
        }
        if (delay <= 0) {
            return handler.post(runnable);
        } else {
            return handler.postDelayed(runnable, delay);
        }
    }

    public void cleanupQueue() {
        try {
            syncLatch.await();
            handler.removeCallbacksAndMessages(null);
        } catch (Exception e) {
            //FileLog.e(e);
        }
    }

    public void handleMessage(Message inputMessage) {

    }

    public long getLastTaskTime() {
        return lastTaskTime;
    }

    public void recycle() {
        handler.getLooper().quit();
    }

    @Override
    public void run() {
        Looper.prepare();
        handler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                DispatchQueue.this.handleMessage(msg);
            }
        };
        syncLatch.countDown();
        Looper.loop();
    }
}
