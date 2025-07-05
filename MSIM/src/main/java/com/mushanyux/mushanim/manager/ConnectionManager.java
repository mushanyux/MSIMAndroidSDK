package com.mushanyux.mushanim.manager;

import android.text.TextUtils;

import com.mushanyux.mushanim.MSIM;
import com.mushanyux.mushanim.MSIMApplication;
import com.mushanyux.mushanim.interfaces.IConnectionStatus;
import com.mushanyux.mushanim.interfaces.IGetIpAndPort;
import com.mushanyux.mushanim.message.MessageHandler;
import com.mushanyux.mushanim.message.MSConnection;
import com.mushanyux.mushanim.utils.MSLoggerUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ConnectionManager extends BaseManager {
    private final String TAG = "ConnectionManager";
    private ConnectionManager() {

    }

    private static class ConnectionManagerBinder {
        static final ConnectionManager connectManager = new ConnectionManager();
    }

    public static ConnectionManager getInstance() {
        return ConnectionManagerBinder.connectManager;
    }


    private IGetIpAndPort iGetIpAndPort;
    private ConcurrentHashMap<String, IConnectionStatus> connectionListenerMap;

    // 连接
    public void connection() {
        if (TextUtils.isEmpty(MSIMApplication.getInstance().getToken()) || TextUtils.isEmpty(MSIMApplication.getInstance().getUid())) {
            MSLoggerUtils.getInstance().e(TAG,"connection Uninitialized UID and token");
            return;
        }
        MSIMApplication.getInstance().isCanConnect = true;
        if (MSConnection.getInstance().connectionIsNull()) {
            MSConnection.getInstance().reconnection();
        }
    }

    public void disconnect(boolean isLogout) {
        if (TextUtils.isEmpty(MSIMApplication.getInstance().getToken())) return;
        if (isLogout) {
            logoutChat();
        } else {
            stopConnect();
        }
    }

    /**
     * 断开连接
     */
    private void stopConnect() {
        MSIMApplication.getInstance().isCanConnect = false;
        MSConnection.getInstance().stopAll();
    }

    /**
     * 退出登录
     */
    private void logoutChat() {
        MSLoggerUtils.getInstance().e(TAG,"exit");
        MSIMApplication.getInstance().isCanConnect = false;
        MessageHandler.getInstance().saveReceiveMsg();

        MSIMApplication.getInstance().setToken("");
        MessageHandler.getInstance().updateLastSendingMsgFail();
        MSConnection.getInstance().stopAll();
        MSIM.getInstance().getChannelManager().clearARMCache();
        MSIMApplication.getInstance().closeDbHelper();
    }

    public interface IRequestIP {
        void onResult(String requestId, String ip, int port);
    }

    public void getIpAndPort(String requestId, IRequestIP iRequestIP) {
        if (iGetIpAndPort != null) {
            runOnMainThread(() -> iGetIpAndPort.getIP((ip, port) -> iRequestIP.onResult(requestId, ip, port)));
        } else {
            MSLoggerUtils.getInstance().e(TAG,"未注册获取连接地址的事件");
        }
    }

    // 监听获取IP和port
    public void addOnGetIpAndPortListener(IGetIpAndPort iGetIpAndPort) {
        this.iGetIpAndPort = iGetIpAndPort;
    }

    public void setConnectionStatus(int status, String reason) {
        if (connectionListenerMap != null && !connectionListenerMap.isEmpty()) {
            runOnMainThread(() -> {
                for (Map.Entry<String, IConnectionStatus> entry : connectionListenerMap.entrySet()) {
                    entry.getValue().onStatus(status, reason);
                }
            });
        }
    }

    // 监听连接状态
    public void addOnConnectionStatusListener(String key, IConnectionStatus iConnectionStatus) {
        if (iConnectionStatus == null || TextUtils.isEmpty(key)) return;
        if (connectionListenerMap == null) connectionListenerMap = new ConcurrentHashMap<>();
        connectionListenerMap.put(key, iConnectionStatus);
    }

    // 移除监听
    public void removeOnConnectionStatusListener(String key) {
        if (!TextUtils.isEmpty(key) && connectionListenerMap != null) {
            connectionListenerMap.remove(key);
        }
    }
}
