package com.mushanyux.mushanim.message;

import android.text.TextUtils;

import com.mushanyux.mushanim.MSIMApplication;
import com.mushanyux.mushanim.utils.MSLoggerUtils;

import org.xsocket.connection.IConnectExceptionHandler;
import org.xsocket.connection.IConnectHandler;
import org.xsocket.connection.IConnectionTimeoutHandler;
import org.xsocket.connection.IDataHandler;
import org.xsocket.connection.IDisconnectHandler;
import org.xsocket.connection.IIdleTimeoutHandler;
import org.xsocket.connection.INonBlockingConnection;

import java.io.IOException;
import java.nio.BufferUnderflowException;

class ConnectionClient implements IDataHandler, IConnectHandler,
        IDisconnectHandler, IConnectExceptionHandler,
        IConnectionTimeoutHandler, IIdleTimeoutHandler {
    private final String TAG = "ConnectionClient";
    private boolean isConnectSuccess;
    private static final int MAX_TIMEOUT_RETRIES = 3;
    private int timeoutRetryCount = 0;

    interface IConnResult {
        void onResult(INonBlockingConnection iNonBlockingConnection);
    }
    IConnResult iConnResult;
    ConnectionClient(IConnResult iConnResult) {
        this.iConnResult = iConnResult;
        isConnectSuccess = false;
    }

    @Override
    public boolean onConnectException(INonBlockingConnection iNonBlockingConnection, IOException e) {
        MSLoggerUtils.getInstance().e(TAG,"连接异常");
        MSConnection.getInstance().forcedReconnection();
        close(iNonBlockingConnection);
        return true;
    }

    @Override
    public boolean onConnect(INonBlockingConnection iNonBlockingConnection) throws BufferUnderflowException {
        isConnectSuccess = true;
        iConnResult.onResult( iNonBlockingConnection);
        return false;
    }

    @Override
    public boolean onConnectionTimeout(INonBlockingConnection iNonBlockingConnection) {
        synchronized (MSConnection.getInstance().connectionLock) {
            if (!isConnectSuccess) {
                timeoutRetryCount++;
                MSLoggerUtils.getInstance().e(TAG, String.format("Connection timeout (attempt %d/%d)", timeoutRetryCount, MAX_TIMEOUT_RETRIES));
                
                // Check if this is the current connection
                if (MSConnection.getInstance().connection != null && 
                    MSConnection.getInstance().connection.getId().equals(iNonBlockingConnection.getId())) {
                    
                    if (timeoutRetryCount >= MAX_TIMEOUT_RETRIES) {
                        MSLoggerUtils.getInstance().e(TAG, "Maximum timeout retries reached, initiating reconnection");
                        timeoutRetryCount = 0;
                        MSConnection.getInstance().forcedReconnection();
                    } else {
                        // Log retry attempt
                        MSLoggerUtils.getInstance().i(TAG, "Retrying connection after timeout");
                        
                        // Attempt to reset connection state
                        try {
                            iNonBlockingConnection.setConnectionTimeoutMillis(
                                Math.min(3000 * (timeoutRetryCount + 1), 10000) // Increase timeout with each retry
                            );
                        } catch (Exception e) {
                            MSLoggerUtils.getInstance().e(TAG, "Failed to adjust connection timeout: " + e.getMessage());
                        }
                    }
                } else {
                    MSLoggerUtils.getInstance().w(TAG, "Timeout for old connection, ignoring");
                    timeoutRetryCount = 0;
                }
            } else {
                MSLoggerUtils.getInstance().i(TAG, "Connection timeout ignored - connection already successful");
            }
        }
        return true;
    }

    @Override
    public boolean onData(INonBlockingConnection iNonBlockingConnection) throws BufferUnderflowException {
        if (MSConnection.getInstance().connectionIsNull() || MSConnection.getInstance().isReConnecting) {
            return true;
        }
        Object id = iNonBlockingConnection.getAttachment();
        if (id instanceof String) {
            if (id.toString().startsWith("close")) {
                return true;
            }
            if (!TextUtils.isEmpty(MSConnection.getInstance().socketSingleID) && !MSConnection.getInstance().socketSingleID.equals(id)) {
                MSLoggerUtils.getInstance().e(TAG, "非当前连接的消息");
                try {
                    iNonBlockingConnection.close();
                    if (MSConnection.getInstance().connection != null) {
                        MSConnection.getInstance().connection.close();
                    }
                } catch (IOException e) {
                    MSLoggerUtils.getInstance().e(TAG, "关闭连接异常");
                }
                if (MSIMApplication.getInstance().isCanConnect) {
                    MSConnection.getInstance().reconnection();
                }
                return true;
            }
        }
        MessageHandler.getInstance().handlerOnlineBytes(iNonBlockingConnection);
        return true;
    }

    @Override
    public boolean onDisconnect(INonBlockingConnection iNonBlockingConnection) {
        try {
            if (iNonBlockingConnection != null && !TextUtils.isEmpty(iNonBlockingConnection.getId()) && iNonBlockingConnection.getAttachment() != null) {
                String id = iNonBlockingConnection.getId();
                Object attachmentObject = iNonBlockingConnection.getAttachment();
                if (attachmentObject instanceof String) {
                    String att = (String) attachmentObject;
                    // Check if this is a planned closure
                    if (att.startsWith("closing_") || att.equals("close" + id)) {
                        MSLoggerUtils.getInstance().e("主动断开不重连");
                        return true;
                    }
                }
            }
            
            // Reset timeout counter on disconnect
            timeoutRetryCount = 0;
            
            // Only attempt reconnection if we're allowed to connect and it's not a planned closure
            if (MSIMApplication.getInstance().isCanConnect && !MSConnection.getInstance().isClosing.get()) {
                MSLoggerUtils.getInstance().e("连接断开需要重连");
                MSConnection.getInstance().forcedReconnection();
            }
            close(iNonBlockingConnection);
        } catch (Exception ignored) {
        }
        return true;
    }

    @Override
    public boolean onIdleTimeout(INonBlockingConnection iNonBlockingConnection) {
        if (!isConnectSuccess) {
            MSConnection.getInstance().forcedReconnection();
            close(iNonBlockingConnection);
        }
        return true;
    }

    private void close(INonBlockingConnection iNonBlockingConnection) {
        try {
            if (iNonBlockingConnection != null)
                iNonBlockingConnection.close();
        } catch (IOException e) {
            MSLoggerUtils.getInstance().e(TAG, "关闭连接异常");
        }
    }
}