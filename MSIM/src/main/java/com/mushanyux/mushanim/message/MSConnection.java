package com.mushanyux.mushanim.message;

import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import com.mushanyux.mushanim.MSIM;
import com.mushanyux.mushanim.MSIMApplication;
import com.mushanyux.mushanim.db.MsgDbManager;
import com.mushanyux.mushanim.entity.MSChannel;
import com.mushanyux.mushanim.entity.MSChannelType;
import com.mushanyux.mushanim.entity.MSConversationMsgExtra;
import com.mushanyux.mushanim.entity.MSMsg;
import com.mushanyux.mushanim.entity.MSMsgSetting;
import com.mushanyux.mushanim.entity.MSSyncMsgMode;
import com.mushanyux.mushanim.entity.MSUIConversationMsg;
import com.mushanyux.mushanim.interfaces.IReceivedMsgListener;
import com.mushanyux.mushanim.manager.ConnectionManager;
import com.mushanyux.mushanim.message.timer.HeartbeatManager;
import com.mushanyux.mushanim.message.timer.NetworkChecker;
import com.mushanyux.mushanim.message.timer.TimerManager;
import com.mushanyux.mushanim.message.type.MSConnectReason;
import com.mushanyux.mushanim.message.type.MSConnectStatus;
import com.mushanyux.mushanim.message.type.MSMsgType;
import com.mushanyux.mushanim.message.type.MSSendMsgResult;
import com.mushanyux.mushanim.message.type.MSSendingMsg;
import com.mushanyux.mushanim.msgmodel.MSImageContent;
import com.mushanyux.mushanim.msgmodel.MSMediaMessageContent;
import com.mushanyux.mushanim.msgmodel.MSVideoContent;
import com.mushanyux.mushanim.protocol.MSBaseMsg;
import com.mushanyux.mushanim.protocol.MSConnectAckMsg;
import com.mushanyux.mushanim.protocol.MSConnectMsg;
import com.mushanyux.mushanim.protocol.MSDisconnectMsg;
import com.mushanyux.mushanim.protocol.MSPongMsg;
import com.mushanyux.mushanim.protocol.MSSendAckMsg;
import com.mushanyux.mushanim.protocol.MSSendMsg;
import com.mushanyux.mushanim.utils.DateUtils;
import com.mushanyux.mushanim.utils.DispatchQueuePool;
import com.mushanyux.mushanim.utils.FileUtils;
import com.mushanyux.mushanim.utils.MSLoggerUtils;

import org.json.JSONObject;
import org.xsocket.connection.IConnection;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.NonBlockingConnection;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;


public class MSConnection {
    private final String TAG = "MSConnection";

    private MSConnection() {
    }

    private static class ConnectHandleBinder {
        private static final MSConnection CONNECT = new MSConnection();
    }

    public static MSConnection getInstance() {
        return ConnectHandleBinder.CONNECT;
    }

    private final DispatchQueuePool dispatchQueuePool = new DispatchQueuePool(3);
    // 正在发送的消息
    private final ConcurrentHashMap<Integer, MSSendingMsg> sendingMsgHashMap = new ConcurrentHashMap<>();
    // 正在重连中
    public boolean isReConnecting = false;
    // 连接状态
    private int connectStatus;
    private long lastMsgTime = 0;
    private String ip;
    private int port;
    public volatile INonBlockingConnection connection;
    volatile ConnectionClient connectionClient;
    private long requestIPTime;
    private long connAckTime;
    private final long requestIPTimeoutTime = 6;
    private final long connAckTimeoutTime = 10;
    public String socketSingleID;
    private String lastRequestId;
    public volatile Handler reconnectionHandler = new Handler(Objects.requireNonNull(Looper.myLooper()));
    Runnable reconnectionRunnable = this::reconnection;
    private int connCount = 0;
    private HeartbeatManager heartbeatManager;
    private NetworkChecker networkChecker;

    private final Handler checkRequestAddressHandler = new Handler(Looper.getMainLooper());
    private final Runnable checkRequestAddressRunnable = new Runnable() {
        @Override
        public void run() {
            long nowTime = DateUtils.getInstance().getCurrentSeconds();
            if (nowTime - requestIPTime >= requestIPTimeoutTime) {
                if (TextUtils.isEmpty(ip) || port == 0) {
                    MSLoggerUtils.getInstance().e(TAG, "获取连接地址超时");
                    isReConnecting = false;
                    reconnection();
                }
            } else {
                if (TextUtils.isEmpty(ip) || port == 0) {
                    MSLoggerUtils.getInstance().e(TAG, "请求连接地址--->" + (nowTime - requestIPTime));
                    // 继续检查
                    checkRequestAddressHandler.postDelayed(this, 1000);
                }
            }
        }
    };

    private final Handler checkConnAckHandler = new Handler(Looper.getMainLooper());
    private final Runnable checkConnAckRunnable = new Runnable() {
        @Override
        public void run() {
            long nowTime = DateUtils.getInstance().getCurrentSeconds();
            if (nowTime - connAckTime > connAckTimeoutTime && connectStatus != MSConnectStatus.success && connectStatus != MSConnectStatus.syncMsg) {
                MSLoggerUtils.getInstance().e(TAG, "连接确认超时");
                isReConnecting = false;
                closeConnect();
                reconnection();
            } else {
                if (connectStatus == MSConnectStatus.success || connectStatus == MSConnectStatus.syncMsg) {
                    MSLoggerUtils.getInstance().e(TAG, "连接确认成功");
                } else {
                    MSLoggerUtils.getInstance().e(TAG, "等待连接确认--->" + (nowTime - connAckTime));
                    // 继续检查
                    checkConnAckHandler.postDelayed(this, 1000);
                }
            }
        }
    };

    // 替换原有的 Object 锁
    public final ReentrantLock connectionLock = new ReentrantLock(true); // 使用公平锁
    private static final long LOCK_TIMEOUT = 3000; // 3秒超时
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final long CONNECTION_CLOSE_TIMEOUT = 5000; // 5 seconds timeout

    public final AtomicBoolean isClosing = new AtomicBoolean(false);

    private final int maxReconnectAttempts = 5;
    private final long baseReconnectDelay = 500;

    private final Object connectionStateLock = new Object();
    private volatile boolean isConnecting = false;

    private final Object reconnectLock = new Object();
    private volatile boolean isReconnectScheduled = false;
    private final Object executorLock = new Object();
    private volatile ExecutorService connectionExecutor;

    private ExecutorService getOrCreateExecutor() {
        synchronized (executorLock) {
            if (connectionExecutor == null || connectionExecutor.isShutdown() || connectionExecutor.isTerminated()) {
                connectionExecutor = Executors.newSingleThreadExecutor(r -> {
                    Thread thread = new Thread(r, "MSConnection-Worker");
                    thread.setDaemon(true);
                    return thread;
                });
                MSLoggerUtils.getInstance().i(TAG, "创建新的连接线程池");
            }
            return connectionExecutor;
        }
    }

    private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);

    private void shutdownExecutor() {
        if (!isShuttingDown.compareAndSet(false, true)) {
            MSLoggerUtils.getInstance().w(TAG, "Executor is already shutting down");
            return;
        }

        ExecutorService executorToShutdown;
        synchronized (executorLock) {
            executorToShutdown = connectionExecutor;
            connectionExecutor = null;
        }

        if (executorToShutdown != null && !executorToShutdown.isShutdown()) {
            dispatchQueuePool.execute(() -> {
                try {
                    MSLoggerUtils.getInstance().i(TAG, "Starting executor shutdown");
                    executorToShutdown.shutdown();

                    if (!executorToShutdown.awaitTermination(3, TimeUnit.SECONDS)) {
                        MSLoggerUtils.getInstance().w(TAG, "Executor did not terminate in time, forcing shutdown");
                        executorToShutdown.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    MSLoggerUtils.getInstance().e(TAG, "Executor shutdown interrupted: " + e.getMessage());
                    executorToShutdown.shutdownNow();
                    Thread.currentThread().interrupt();
                } finally {
                    isShuttingDown.set(false);
                    MSLoggerUtils.getInstance().i(TAG, "Executor shutdown completed");
                }
            });
        }
    }

    private void startAll() {
        heartbeatManager = new HeartbeatManager();
        networkChecker = new NetworkChecker();
        heartbeatManager.startHeartbeat();
        networkChecker.startNetworkCheck();
    }

    public synchronized void forcedReconnection() {
        synchronized (reconnectLock) {
            if (isReconnectScheduled) {
                MSLoggerUtils.getInstance().w(TAG, "已经在重连计划中，忽略重复请求");
                return;
            }

            // 检查线程池状态
            ExecutorService executor = getOrCreateExecutor();
            if (executor.isShutdown() || executor.isTerminated()) {
                MSLoggerUtils.getInstance().e(TAG, "线程池已关闭，无法执行重连");
                return;
            }

            connCount++;
            if (connCount > maxReconnectAttempts) {
                MSLoggerUtils.getInstance().e(TAG, "达到最大重连次数，停止重连");
                stopAll();
                return;
            }

            isReconnectScheduled = true;
            isReConnecting = false;
            requestIPTime = 0;

            // 使用指数退避延迟，最大延迟改为8秒
            long delay = Math.min(baseReconnectDelay * (1L << (connCount - 1)), 8000);
            MSLoggerUtils.getInstance().e(TAG, "重连延迟: " + delay + "ms");

            try {
                // 使用单独的线程池处理重连
                executor.execute(() -> {
                    try {
                        Thread.sleep(delay);
                        if (MSIMApplication.getInstance().isCanConnect &&
                                !executor.isShutdown()) {
                            reconnection();
                        }
                    } catch (InterruptedException e) {
                        MSLoggerUtils.getInstance().e(TAG, "重连等待被中断");
                        Thread.currentThread().interrupt();
                    } finally {
                        isReconnectScheduled = false;
                    }
                });
            } catch (RejectedExecutionException e) {
                MSLoggerUtils.getInstance().e(TAG, "重连任务被拒绝执行: " + e.getMessage());
                isReconnectScheduled = false;
            }
        }
    }

    public synchronized void reconnection() {
        // 如果正在关闭连接，等待关闭完成
        if (isClosing.get()) {
            MSLoggerUtils.getInstance().e(TAG, "等待连接关闭完成后再重连");
            mainHandler.postDelayed(this::reconnection, 500);
            return;
        }

        if (!MSIMApplication.getInstance().isCanConnect) {
            MSLoggerUtils.getInstance().e(TAG, "断开");
            stopAll();
            return;
        }

        ip = "";
        port = 0;
        if (isReConnecting) {
            long nowTime = DateUtils.getInstance().getCurrentSeconds();
            if (nowTime - requestIPTime > requestIPTimeoutTime) {
                MSLoggerUtils.getInstance().e("重置了正在连接");
                isReConnecting = false;
            }
            return;
        }

        connectStatus = MSConnectStatus.fail;
        reconnectionHandler.removeCallbacks(reconnectionRunnable);
        boolean isHaveNetwork = MSIMApplication.getInstance().isNetworkConnected();
        if (isHaveNetwork) {
            closeConnect();
            isReConnecting = true;
            requestIPTime = DateUtils.getInstance().getCurrentSeconds();
            getConnAddress();
        } else {
            if (networkChecker != null && networkChecker.checkNetWorkTimerIsRunning) {
                MSIM.getInstance().getConnectionManager().setConnectionStatus(MSConnectStatus.noNetwork, MSConnectReason.NoNetwork);
                forcedReconnection();
            }
        }
    }

    private void getConnAddress() {
        ExecutorService executor = getOrCreateExecutor();
        if (executor.isShutdown()) {
            MSLoggerUtils.getInstance().e(TAG, "线程池已关闭，重新初始化后重试");
            executor = getOrCreateExecutor();
        }

        try {
            executor.execute(() -> {
                try {
                    if (!MSIMApplication.getInstance().isCanConnect) {
                        MSLoggerUtils.getInstance().e(TAG, "不允许连接");
                        return;
                    }

                    final long startTime = System.currentTimeMillis();
                    final long ADDRESS_TIMEOUT = 10000; // 10秒超时

                    MSIM.getInstance().getConnectionManager().setConnectionStatus(MSConnectStatus.connecting, MSConnectReason.Connecting);
                    String currentRequestId = UUID.randomUUID().toString().replace("-", "");
                    lastRequestId = currentRequestId;

                    CountDownLatch addressLatch = new CountDownLatch(1);
                    AtomicReference<String> receivedIp = new AtomicReference<>();
                    AtomicInteger receivedPort = new AtomicInteger();

                    ConnectionManager.getInstance().getIpAndPort(currentRequestId, (requestId, ip, port) -> {
                        if (!currentRequestId.equals(requestId)) {
                            MSLoggerUtils.getInstance().w(TAG, "收到过期的地址响应");
                            addressLatch.countDown();
                            return;
                        }

                        receivedIp.set(ip);
                        receivedPort.set(port);
                        addressLatch.countDown();
                    });

                    // 等待地址响应或超时
                    boolean gotAddress = addressLatch.await(ADDRESS_TIMEOUT, TimeUnit.MILLISECONDS);
                    if (!gotAddress) {
                        MSLoggerUtils.getInstance().e(TAG, "获取连接地址超时");
                        isReConnecting = false;
                        forcedReconnection();
                        return;
                    }

                    String ip = receivedIp.get();
                    int port = receivedPort.get();

                    if (TextUtils.isEmpty(ip) || port == 0) {
                        MSLoggerUtils.getInstance().e(TAG, "无效的连接地址");
                        isReConnecting = false;
                        forcedReconnection();
                        return;
                    }

                    MSConnection.this.ip = ip;
                    MSConnection.this.port = port;
                    if (connectionIsNull()) {
                        connSocket();
                    }
                } catch (Exception e) {
                    MSLoggerUtils.getInstance().e(TAG, "获取地址异常: " + e.getMessage());
                    isReConnecting = false;
                    forcedReconnection();
                }
            });
        } catch (RejectedExecutionException e) {
            MSLoggerUtils.getInstance().e(TAG, "任务提交被拒绝，重试: " + e.getMessage());
            isReConnecting = false;
            // 短暂延迟后重试
            mainHandler.postDelayed(this::reconnection, 1000);
        }
    }

    private void connSocket() {
        // 检查线程池状态
        ExecutorService executor = getOrCreateExecutor();
        if (executor.isShutdown() || executor.isTerminated()) {
            MSLoggerUtils.getInstance().e(TAG, "线程池已关闭，无法执行连接");
            return;
        }

        // 使用CAS操作检查连接状态
        if (!setConnectingState(true)) {
            MSLoggerUtils.getInstance().e(TAG, "已经在连接中，忽略重复连接请求");
            return;
        }

        try {
            executor.execute(() -> {
                try {
                    // 关闭现有连接
                    closeConnect();

                    // 生成新的连接ID
                    String newSocketId = UUID.randomUUID().toString().replace("-", "");

                    CountDownLatch connectLatch = new CountDownLatch(1);
                    AtomicBoolean connectSuccess = new AtomicBoolean(false);

                    ConnectionClient newClient = new ConnectionClient(iNonBlockingConnection -> {
                        INonBlockingConnection currentConn = null;
                        synchronized (connectionLock) {
                            currentConn = connection;
                        }

                        if (iNonBlockingConnection == null || currentConn == null ||
                                !currentConn.getId().equals(iNonBlockingConnection.getId())) {
                            MSLoggerUtils.getInstance().e(TAG, "无效的连接回调");
                            connectLatch.countDown();
                            return;
                        }

                        try {
                            iNonBlockingConnection.setIdleTimeoutMillis(1000 * 3);
                            iNonBlockingConnection.setConnectionTimeoutMillis(1000 * 3);
                            iNonBlockingConnection.setFlushmode(IConnection.FlushMode.ASYNC);
                            iNonBlockingConnection.setAutoflush(true);

                            connectSuccess.set(true);
                            isReConnecting = false;
                            connCount = 0;
                        } catch (Exception e) {
                            MSLoggerUtils.getInstance().e(TAG, "设置连接参数失败: " + e.getMessage());
                        } finally {
                            connectLatch.countDown();
                        }
                    });

                    // 创建新连接
                    INonBlockingConnection newConnection = new NonBlockingConnection(ip, port, newClient);
                    newConnection.setAttachment(newSocketId);

                    // 原子性地更新连接相关的字段
                    synchronized (connectionLock) {
                        connectionClient = newClient;
                        connection = newConnection;
                        socketSingleID = newSocketId;
                    }

                    // 等待连接完成或超时
                    boolean connected = connectLatch.await(5000, TimeUnit.MILLISECONDS);

                    if (!connected || !connectSuccess.get()) {
                        MSLoggerUtils.getInstance().e(TAG, "连接建立超时或失败");
                        closeConnect();
                        if (!executor.isShutdown()) {
                            forcedReconnection();
                        }
                    } else {
                        sendConnectMsg();
                    }
                } catch (Exception e) {
                    MSLoggerUtils.getInstance().e(TAG, "连接异常: " + e.getMessage() + "连接地址：" + ip + ":" + port);
                    if (!executor.isShutdown()) {
                        forcedReconnection();
                    }
                } finally {
                    setConnectingState(false);
                }
            });
        } catch (RejectedExecutionException e) {
            MSLoggerUtils.getInstance().e(TAG, "连接任务被拒绝执行: " + e.getMessage());
            setConnectingState(false);
        }
    }

    // 使用CAS操作设置连接状态
    private boolean setConnectingState(boolean connecting) {
        synchronized (connectionLock) {
            if (connecting && isConnecting) {
                return false;
            }
            isConnecting = connecting;
            return true;
        }
    }

    //发送连接消息
    void sendConnectMsg() {
        startConnAckTimer();
        sendMessage(new MSConnectMsg());
    }

    void receivedData(byte[] data) {
        MessageHandler.getInstance().cutBytes(data,
                new IReceivedMsgListener() {

                    public void sendAckMsg(
                            MSSendAckMsg talkSendStatus) {
                        // 删除队列中正在发送的消息对象
                        MSSendingMsg object = sendingMsgHashMap.get(talkSendStatus.clientSeq);
                        if (object != null) {
                            object.isCanResend = false;
                            sendingMsgHashMap.put(talkSendStatus.clientSeq, object);
                        }
                    }


                    @Override
                    public void reconnect() {
                        MSIMApplication.getInstance().isCanConnect = true;
                        reconnection();
                    }

                    @Override
                    public void loginStatusMsg(MSConnectAckMsg connectAckMsg) {
                        handleLoginStatus(connectAckMsg);
                    }

                    @Override
                    public void pongMsg(MSPongMsg msgHeartbeat) {
                        // 心跳消息
                        lastMsgTime = DateUtils.getInstance().getCurrentSeconds();
                    }

                    @Override
                    public void kickMsg(MSDisconnectMsg disconnectMsg) {
                        MSIM.getInstance().getConnectionManager().disconnect(true);
                        MSIM.getInstance().getConnectionManager().setConnectionStatus(MSConnectStatus.kicked, MSConnectReason.ReasonConnectKick);
                    }

                });
    }


    //重发未发送成功的消息
    public void resendMsg() {
        removeSendingMsg();
        new Thread(() -> {
            for (Map.Entry<Integer, MSSendingMsg> entry : sendingMsgHashMap.entrySet()) {
                if (entry.getValue().isCanResend) {
                    sendMessage(Objects.requireNonNull(sendingMsgHashMap.get(entry.getKey())).msSendMsg);
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ignored) {
                    }
                }
            }
        }).start();
    }

    //将要发送的消息添加到队列
    private synchronized void addSendingMsg(MSSendMsg sendingMsg) {
        removeSendingMsg();
        sendingMsgHashMap.put(sendingMsg.clientSeq, new MSSendingMsg(1, sendingMsg, true));
    }

    //处理登录消息状态
    private void handleLoginStatus(MSConnectAckMsg connectAckMsg) {
        short status = connectAckMsg.reasonCode;
        boolean locked = false;
        MSLoggerUtils.getInstance().e(TAG, "连接状态：" + status + "，连接节点：" + connectAckMsg.nodeId);
        try {
            locked = tryLockWithTimeout();
            if (!locked) {
                MSLoggerUtils.getInstance().e(TAG, "获取锁超时，handleLoginStatus失败");
                return;
            }

            MSLoggerUtils.getInstance().e(TAG, "Connection state transition: " + connectStatus + " -> " + status);
            String reason = MSConnectReason.ConnectSuccess;
            if (status == MSConnectStatus.kicked) {
                reason = MSConnectReason.ReasonAuthFail;
            }

            if (!isValidStateTransition(connectStatus, status)) {
                MSLoggerUtils.getInstance().e(TAG, "Invalid state transition attempted: " + connectStatus + " -> " + status);
                return;
            }

            connectStatus = status;
            MSIM.getInstance().getConnectionManager().setConnectionStatus(status, reason);

            if (status == MSConnectStatus.success) {
                connCount = 0;
                isReConnecting = false;
                connectStatus = MSConnectStatus.syncMsg;
                MSIM.getInstance().getConnectionManager().setConnectionStatus(MSConnectStatus.syncMsg, MSConnectReason.SyncMsg);
                startAll();

                if (MSIMApplication.getInstance().getSyncMsgMode() == MSSyncMsgMode.WRITE) {
                    MSIM.getInstance().getMsgManager().setSyncOfflineMsg((isEnd, list) -> {
                        if (isEnd) {
                            boolean innerLocked = false;
                            try {
                                innerLocked = tryLockWithTimeout();
                                if (!innerLocked) {
                                    MSLoggerUtils.getInstance().e(TAG, "获取锁超时，setSyncOfflineMsg回调处理失败");
                                    return;
                                }
                                if (connection != null && !isClosing.get()) {
                                    connectStatus = MSConnectStatus.success;
                                    MessageHandler.getInstance().saveReceiveMsg();
                                    MSIMApplication.getInstance().isCanConnect = true;
                                    MessageHandler.getInstance().sendAck();
                                    resendMsg();
                                    MSIM.getInstance().getConnectionManager().setConnectionStatus(MSConnectStatus.success, MSConnectReason.ConnectSuccess);
                                }
                            } finally {
                                if (innerLocked) {
                                    connectionLock.unlock();
                                }
                            }
                        }
                    });
                } else {
                    MSIM.getInstance().getConversationManager().setSyncConversationListener(syncChat -> {
                        boolean innerLocked = false;
                        try {
                            innerLocked = tryLockWithTimeout();
                            if (!innerLocked) {
                                MSLoggerUtils.getInstance().e(TAG, "获取锁超时，setSyncConversationListener回调处理失败");
                                return;
                            }
                            if (connection != null && !isClosing.get()) {
                                connectStatus = MSConnectStatus.success;
                                MSIMApplication.getInstance().isCanConnect = true;
                                MessageHandler.getInstance().sendAck();
                                resendMsg();
                                MSIM.getInstance().getConnectionManager().setConnectionStatus(MSConnectStatus.success, MSConnectReason.ConnectSuccess);
                            }
                        } finally {
                            if (innerLocked) {
                                connectionLock.unlock();
                            }
                        }
                    });
                }
            } else if (status == MSConnectStatus.kicked) {
                MSLoggerUtils.getInstance().e(TAG, "Received kick message");
                MessageHandler.getInstance().updateLastSendingMsgFail();
                MSIMApplication.getInstance().isCanConnect = false;
                stopAll();
            } else {
                if (MSIMApplication.getInstance().isCanConnect) {
                    reconnection();
                }
                MSLoggerUtils.getInstance().e(TAG, "Login status: " + status);
                stopAll();
            }
        } finally {
            if (locked) {
                connectionLock.unlock();
            }
        }
    }

    private boolean isValidStateTransition(int currentState, int newState) {
        // Define valid state transitions
        return switch (currentState) {
            case MSConnectStatus.fail ->
                // From fail state, can move to connecting or success
                    newState == MSConnectStatus.connecting ||
                            newState == MSConnectStatus.success;
            case MSConnectStatus.connecting ->
                // From connecting, can move to success, fail, or no network
                    newState == MSConnectStatus.success ||
                            newState == MSConnectStatus.fail ||
                            newState == MSConnectStatus.noNetwork;
            case MSConnectStatus.success ->
                // From success, can move to syncMsg, kicked, or fail
                    newState == MSConnectStatus.syncMsg ||
                            newState == MSConnectStatus.kicked ||
                            newState == MSConnectStatus.fail;
            case MSConnectStatus.syncMsg ->
                // From syncMsg, can move to success or fail
                    newState == MSConnectStatus.success ||
                            newState == MSConnectStatus.fail;
            case MSConnectStatus.noNetwork ->
                // From noNetwork, can move to connecting or fail
                    newState == MSConnectStatus.connecting ||
                            newState == MSConnectStatus.fail;
            default ->
                // For any other state, allow transition to fail state
                    newState == MSConnectStatus.fail;
        };
    }

    public void sendMessage(MSBaseMsg mBaseMsg) {
        if (mBaseMsg == null) {
            MSLoggerUtils.getInstance().w(TAG, "sendMessage called with null mBaseMsg.");
            return;
        }

        boolean locked = false;
        try {
            locked = tryLockWithTimeout();
            if (!locked) {
                MSLoggerUtils.getInstance().e(TAG, "获取锁超时，sendMessage失败");
                return;
            }

            if (mBaseMsg.packetType != MSMsgType.CONNECT) {
                if (connectStatus == MSConnectStatus.syncMsg) {
                    MSLoggerUtils.getInstance().i(TAG, " sendMessage: In syncMsg status, message not sent: " + mBaseMsg.packetType);
                    return;
                }
                if (connectStatus != MSConnectStatus.success) {
                    MSLoggerUtils.getInstance().w(TAG, " sendMessage: Not in success status (is " + connectStatus + "), attempting reconnection for: " + mBaseMsg.packetType);
                    reconnection();
                    return;
                }
            }

            INonBlockingConnection currentConnection = this.connection;
            if (currentConnection == null || !currentConnection.isOpen()) {
                MSLoggerUtils.getInstance().w(TAG, " sendMessage: Connection is null or not open, attempting reconnection for: " + mBaseMsg.packetType);
                reconnection();
                return;
            }

            int status = MessageHandler.getInstance().sendMessage(currentConnection, mBaseMsg);
            if (status == 0) {
                MSLoggerUtils.getInstance().e(TAG, "发消息失败 (status 0 from MessageHandler), attempting reconnection for: " + mBaseMsg.packetType);
                reconnection();
            }
        } finally {
            if (locked) {
                connectionLock.unlock();
            }
        }
    }

    private void removeSendingMsg() {
        if (!sendingMsgHashMap.isEmpty()) {
            Iterator<Map.Entry<Integer, MSSendingMsg>> it = sendingMsgHashMap.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Integer, MSSendingMsg> entry = it.next();
                if (!entry.getValue().isCanResend) {
                    it.remove();
                }
            }
        }
    }

    //检测正在发送的消息
    public synchronized void checkSendingMsg() {
        removeSendingMsg();
        if (!sendingMsgHashMap.isEmpty()) {
            Iterator<Map.Entry<Integer, MSSendingMsg>> it = sendingMsgHashMap.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Integer, MSSendingMsg> item = it.next();
                MSSendingMsg msSendingMsg = sendingMsgHashMap.get(item.getKey());
                if (msSendingMsg != null) {
                    if (msSendingMsg.sendCount == 5 && msSendingMsg.isCanResend) {
                        //标示消息发送失败
                        MsgDbManager.getInstance().updateMsgStatus(item.getKey(), MSSendMsgResult.send_fail);
                        it.remove();
                        msSendingMsg.isCanResend = false;
                    } else {
                        long nowTime = DateUtils.getInstance().getCurrentSeconds();
                        if (nowTime - msSendingMsg.sendTime > 10) {
                            msSendingMsg.sendTime = DateUtils.getInstance().getCurrentSeconds();
                            sendingMsgHashMap.put(item.getKey(), msSendingMsg);
                            msSendingMsg.sendCount++;
                            sendMessage(Objects.requireNonNull(sendingMsgHashMap.get(item.getKey())).msSendMsg);
                        }
                    }
                }
            }
        }
    }


    public void sendMessage(MSMsg msg) {
        if (TextUtils.isEmpty(msg.fromUID)) {
            msg.fromUID = MSIMApplication.getInstance().getUid();
        }
        if (msg.expireTime > 0) {
            msg.expireTimestamp = DateUtils.getInstance().getCurrentSeconds() + msg.expireTime;
        }
        boolean hasAttached = false;
        //如果是图片消息
        if (msg.baseContentMsgModel instanceof MSImageContent imageContent) {
            if (!TextUtils.isEmpty(imageContent.localPath)) {
//                try {
//                    File file = new File(imageContent.localPath);
//                    if (file.exists() && file.length() > 0) {
//                        hasAttached = true;
//                        Bitmap bitmap = BitmapFactory.decodeFile(imageContent.localPath);
//                        if (bitmap != null) {
//                            imageContent.width = bitmap.getWidth();
//                            imageContent.height = bitmap.getHeight();
//                            msg.baseContentMsgModel = imageContent;
//                        }
//                    }
//                } catch (Exception ignored) {
//                }

                try {
                    File file = new File(imageContent.localPath);
                    if (file.exists() && file.length() > 0) {
                        hasAttached = true;
                        // 使用 Options 只解码尺寸信息
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inJustDecodeBounds = true; // 只获取图片信息,不加载到内存
                        BitmapFactory.decodeFile(imageContent.localPath, options);

                        imageContent.width = options.outWidth;
                        imageContent.height = options.outHeight;
                        msg.baseContentMsgModel = imageContent;
                    }
                } catch (Exception e) {
                    MSLoggerUtils.getInstance().e("MSConnection", "Get image size failed: " + e.getMessage());
                }
            }
        }
        //视频消息
        if (msg.baseContentMsgModel instanceof MSVideoContent videoContent) {
            if (!TextUtils.isEmpty(videoContent.localPath)) {
                try {
                    File file = new File(videoContent.coverLocalPath);
                    if (file.exists() && file.length() > 0) {
                        hasAttached = true;
//                        Bitmap bitmap = BitmapFactory.decodeFile(videoContent.coverLocalPath);
//                        if (bitmap != null) {
//                            videoContent.width = bitmap.getWidth();
//                            videoContent.height = bitmap.getHeight();
//                            msg.baseContentMsgModel = videoContent;
//                        }

                        // 使用 Options 只解码尺寸信息
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inJustDecodeBounds = true; // 只获取图片信息,不加载到内存
                        BitmapFactory.decodeFile(videoContent.coverLocalPath, options);

                        videoContent.width = options.outWidth;
                        videoContent.height = options.outHeight;
                        msg.baseContentMsgModel = videoContent;
                    }
                } catch (Exception ignored) {

                }
            }

        }
        saveSendMsg(msg);
        MSSendMsg sendMsg = MSProto.getInstance().getSendBaseMsg(msg);
        if (MSMediaMessageContent.class.isAssignableFrom(msg.baseContentMsgModel.getClass())) {
            //如果是多媒体消息类型说明存在附件
            String url = ((MSMediaMessageContent) msg.baseContentMsgModel).url;
            if (TextUtils.isEmpty(url)) {
                String localPath = ((MSMediaMessageContent) msg.baseContentMsgModel).localPath;
                if (!TextUtils.isEmpty(localPath)) {
                    hasAttached = true;
                    ((MSMediaMessageContent) msg.baseContentMsgModel).localPath = FileUtils.getInstance().saveFile(localPath, msg.channelID, msg.channelType, msg.clientSeq + "");
                }
            }
            if (msg.baseContentMsgModel instanceof MSVideoContent) {
                String coverLocalPath = ((MSVideoContent) msg.baseContentMsgModel).coverLocalPath;
                if (!TextUtils.isEmpty(coverLocalPath)) {
                    ((MSVideoContent) msg.baseContentMsgModel).coverLocalPath = FileUtils.getInstance().saveFile(coverLocalPath, msg.channelID, msg.channelType, msg.clientSeq + "_1");
                    hasAttached = true;
                }
            }
            if (hasAttached) {
                JSONObject jsonObject = MSProto.getInstance().getSendPayload(msg);
                if (jsonObject != null) {
                    msg.content = jsonObject.toString();
                } else {
                    msg.content = msg.baseContentMsgModel.encodeMsg().toString();
                }
                MSIM.getInstance().getMsgManager().updateContentAndRefresh(msg.clientMsgNO, msg.content, false);
            }
        }
        //获取发送者信息
        MSChannel from = MSIM.getInstance().getChannelManager().getChannel(MSIMApplication.getInstance().getUid(), MSChannelType.PERSONAL);
        if (from == null) {
            MSIM.getInstance().getChannelManager().getChannel(MSIMApplication.getInstance().getUid(), MSChannelType.PERSONAL, channel -> MSIM.getInstance().getChannelManager().saveOrUpdateChannel(channel));
        } else {
            msg.setFrom(from);
        }
        //将消息push回UI层
        MSIM.getInstance().getMsgManager().setSendMsgCallback(msg);
        if (hasAttached) {
            //存在附件处理
            MSIM.getInstance().getMsgManager().setUploadAttachment(msg, (isSuccess, messageContent) -> {
                if (isSuccess) {
                    msg.baseContentMsgModel = messageContent;
                    JSONObject jsonObject = MSProto.getInstance().getSendPayload(msg);
                    if (jsonObject != null) {
                        msg.content = jsonObject.toString();
                    } else {
                        msg.content = msg.baseContentMsgModel.encodeMsg().toString();
                    }
                    MSIM.getInstance().getMsgManager().updateContentAndRefresh(msg.clientMsgNO, msg.content, false);
                    if (!sendingMsgHashMap.containsKey((int) msg.clientSeq)) {
                        MSSendMsg base1 = MSProto.getInstance().getSendBaseMsg(msg);
                        addSendingMsg(base1);
                        sendMessage(base1);
                    }
                } else {
                    MsgDbManager.getInstance().updateMsgStatus(msg.clientSeq, MSSendMsgResult.send_fail);
                }
            });
        } else {
            if (sendMsg != null) {
                if (msg.header != null && !msg.header.noPersist) {
                    addSendingMsg(sendMsg);
                }
                sendMessage(sendMsg);
            }
        }
    }

    public boolean connectionIsNull() {
        boolean locked = false;
        try {
            locked = tryLockWithTimeout();
            if (!locked) {
                MSLoggerUtils.getInstance().e(TAG, "获取锁超时，connectionIsNull检查失败");
                return true; // 保守起见，如果获取锁失败就认为连接为空
            }
            return connection == null || !connection.isOpen();
        } finally {
            if (locked) {
                connectionLock.unlock();
            }
        }
    }

    private synchronized void startConnAckTimer() {
        // 移除之前的回调
        checkConnAckHandler.removeCallbacks(checkConnAckRunnable);
        connAckTime = DateUtils.getInstance().getCurrentSeconds();
        // 开始新的检查
        checkConnAckHandler.postDelayed(checkConnAckRunnable, 1000);
    }

    private void saveSendMsg(MSMsg msg) {
        if (msg.setting == null) msg.setting = new MSMsgSetting();
        JSONObject jsonObject = MSProto.getInstance().getSendPayload(msg);
        msg.content = jsonObject.toString();
        long tempOrderSeq = MsgDbManager.getInstance().queryMaxOrderSeqWithChannel(msg.channelID, msg.channelType);
        msg.orderSeq = tempOrderSeq + 1;
        // 需要存储的消息入库后更改消息的clientSeq
        if (!msg.header.noPersist) {
            msg.clientSeq = (int) MsgDbManager.getInstance().insert(msg);
            if (msg.clientSeq > 0) {
                MSUIConversationMsg uiMsg = MSIM.getInstance().getConversationManager().updateWithMSMsg(msg);
                if (uiMsg != null) {
                    long browseTo = MSIM.getInstance().getMsgManager().getMaxMessageSeqWithChannel(uiMsg.channelID, uiMsg.channelType);
                    if (uiMsg.getRemoteMsgExtra() == null) {
                        uiMsg.setRemoteMsgExtra(new MSConversationMsgExtra());
                    }
                    uiMsg.getRemoteMsgExtra().browseTo = browseTo;
                    MSIM.getInstance().getConversationManager().setOnRefreshMsg(uiMsg, "getSendBaseMsg");
                }
            }
        }
    }

    public void stopAll() {
        boolean locked = false;
        try {
            locked = tryLockWithTimeout();
            if (!locked) {
                MSLoggerUtils.getInstance().e(TAG, "获取锁超时，stopAll失败");
                return;
            }

            // 先设置连接状态为失败
            MSIM.getInstance().getConnectionManager().setConnectionStatus(MSConnectStatus.fail, "");
            // 清理连接相关资源
            closeConnect();
            // 关闭定时器管理器
            TimerManager.getInstance().shutdown();
            MessageHandler.getInstance().clearCacheData();
            // 移除所有Handler回调
            if (checkRequestAddressHandler != null) {
                checkRequestAddressHandler.removeCallbacks(checkRequestAddressRunnable);
            }
            if (checkConnAckHandler != null) {
                checkConnAckHandler.removeCallbacks(checkConnAckRunnable);
            }
            if (reconnectionHandler != null) {
                reconnectionHandler.removeCallbacks(reconnectionRunnable);
            }

            // 重置所有状态
            connectStatus = MSConnectStatus.fail;
            isReConnecting = false;
            isConnecting = false;
            ip = "";
            port = 0;
            requestIPTime = 0;
            connAckTime = 0;
            lastMsgTime = 0;
            connCount = 0;

            // 清空发送消息队列
            if (sendingMsgHashMap != null) {
                sendingMsgHashMap.clear();
            }
            // 清理连接客户端
            connectionClient = null;

            // 关闭线程池
            shutdownExecutor();

            System.gc();
        } finally {
            if (locked) {
                connectionLock.unlock();
            }
        }
    }

    private void closeConnect() {
        final INonBlockingConnection connectionToCloseActual;

        if (!isClosing.compareAndSet(false, true)) {
            MSLoggerUtils.getInstance().i(TAG, " Close operation already in progress");
            return;
        }

        boolean locked = false;
        try {
            locked = tryLockWithTimeout();
            if (!locked) {
                MSLoggerUtils.getInstance().e(TAG, "获取锁超时，closeConnect失败");
                isClosing.set(false);
                return;
            }

            if (connection == null) {
                isClosing.set(false);
                MSLoggerUtils.getInstance().i(TAG, " closeConnect called but connection is already null.");
                return;
            }
            connectionToCloseActual = connection;
            String connId = connectionToCloseActual.getId();

            try {
                connectionToCloseActual.setAttachment("closing_" + System.currentTimeMillis() + "_" + connId);
            } catch (Exception e) {
                MSLoggerUtils.getInstance().e(TAG, "Failed to set closing attachment: " + e.getMessage());
            }

            connection = null;
            connectionClient = null;
            MSLoggerUtils.getInstance().i(TAG, " Connection object nulled, preparing for async close of: " + connId);
        } finally {
            if (locked) {
                connectionLock.unlock();
            }
        }

        // Create a timeout handler to force close after timeout
        final Runnable timeoutRunnable = () -> {
            try {
                if (connectionToCloseActual.isOpen()) {
                    String connId = connectionToCloseActual.getId();
                    MSLoggerUtils.getInstance().w(TAG, " Connection close timeout reached for: " + connId);
                    connectionToCloseActual.close();
                }
            } catch (Exception e) {
                MSLoggerUtils.getInstance().e(TAG, "Force close connection exception: " + e.getMessage());
            } finally {
                isClosing.set(false);
            }
        };

        // Schedule the timeout
        mainHandler.postDelayed(timeoutRunnable, CONNECTION_CLOSE_TIMEOUT);

        // Execute the close operation on a background thread
        Thread closeThread = new Thread(() -> {
            try {
                if (connectionToCloseActual.isOpen()) {
                    String connId = connectionToCloseActual.getId();
                    MSLoggerUtils.getInstance().i(TAG, " Attempting to close connection: " + connId);
                    connectionToCloseActual.close();
                    // Remove the timeout handler since we closed successfully
                    mainHandler.removeCallbacks(timeoutRunnable);
                    MSLoggerUtils.getInstance().i(TAG, " Successfully closed connection: " + connId);
                } else {
                    MSLoggerUtils.getInstance().i(TAG, " Connection was already closed or not open when async close executed: " + connectionToCloseActual.getId());
                }
            } catch (IOException e) {
                MSLoggerUtils.getInstance().e(TAG, "IOException during async connection close for " + connectionToCloseActual.getId() + ": " + e.getMessage());
            } catch (Exception e) {
                MSLoggerUtils.getInstance().e(TAG, "Exception during async connection close for " + connectionToCloseActual.getId() + ": " + e.getMessage());
            } finally {
                synchronized (connectionLock) {
                    isClosing.set(false);
                    // Only trigger reconnection if we're still supposed to be connected
                    if (MSIMApplication.getInstance().isCanConnect && connectStatus != MSConnectStatus.kicked) {
                        mainHandler.postDelayed(() -> {
                            if (connectionIsNull() && !isClosing.get()) {
                                reconnection();
                            }
                        }, 1000);
                    }
                }
            }
        }, "ConnectionCloser");
        closeThread.setDaemon(true);
        closeThread.start();
    }

    private boolean tryLockWithTimeout() {
        try {
            return connectionLock.tryLock(LOCK_TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            MSLoggerUtils.getInstance().e(TAG, "获取锁被中断: " + e.getMessage());
            Thread.currentThread().interrupt();
            return false;
        }
    }
}