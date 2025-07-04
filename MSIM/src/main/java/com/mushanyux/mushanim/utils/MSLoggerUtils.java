package com.mushanyux.mushanim.utils;

import android.annotation.SuppressLint;
import android.util.Log;

import com.mushanyux.mushanim.MSIM;
import com.mushanyux.mushanim.MSIMApplication;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

public class MSLoggerUtils {

    /**
     * log TAG
     */
    private final String TAG = "MSLogger" + MSIM.getInstance().getVersion();

    private final String FILE_NAME = "msLogger_" + MSIM.getInstance().getVersion() + ".log";

    private String getLogFilePath() {
        final String ROOT = Objects.requireNonNull(MSIMApplication.getInstance().getContext().getExternalFilesDir(null)).getAbsolutePath() + "/";
        return ROOT + FILE_NAME;
    }

    private MSLoggerUtils() {

    }

    private static class LoggerUtilsBinder {
        private final static MSLoggerUtils utils = new MSLoggerUtils();
    }

    public static MSLoggerUtils getInstance() {
        return LoggerUtilsBinder.utils;
    }

    /**
     * 获取函数名称
     */
    private String getFunctionName() {
        StackTraceElement[] sts = Thread.currentThread().getStackTrace();

        for (StackTraceElement st : sts) {
            if (st.isNativeMethod()) {
                continue;
            }

            if (st.getClassName().equals(Thread.class.getName())) {
                continue;
            }

            if (st.getClassName().equals(this.getClass().getName())) {
                continue;
            }

            return "[" + Thread.currentThread().getName() + "(" + Thread.currentThread().getId()
                    + "): " + st.getFileName() + ":" + st.getLineNumber() + "]";
        }

        return null;
    }

    private String createMessage(String msg) {
        String functionName = getFunctionName();
        return (functionName == null ? msg : (functionName + " - " + msg));
    }

    /**
     * log.i
     */
    private void info(String tag, String msg) {
        String message = createMessage(msg);
        if (MSIM.getInstance().isDebug()) {
            Log.i(TAG + " " + tag, message);
        }
        if (MSIM.getInstance().isDebug()) {
            writeLog(message);
        }
    }

    public void i(String tag, String msg) {
        info(tag, msg);
    }

    public void i(String tag, Exception e) {
        info(tag, e != null ? e.toString() : "null");
    }

    /**
     * log.v
     */
    private void verbose(String msg) {
        String message = createMessage(msg);
        if (MSIM.getInstance().isDebug()) {
            Log.v(TAG, message);
        }
        if (MSIM.getInstance().isDebug()) {
            writeLog(message);
        }
    }

    public void v(String msg) {
        if (MSIM.getInstance().isDebug()) {
            verbose(msg);
        }
        if (MSIM.getInstance().isDebug()) {
            writeLog(msg);
        }
    }

    public void v(Exception e) {
        if (MSIM.getInstance().isDebug()) {
            verbose(e != null ? e.toString() : "null");
        }
        if (MSIM.getInstance().isDebug()) {
            writeLog(e.toString());
        }
    }

    /**
     * log.d
     */
    private void debug(String msg) {
        if (MSIM.getInstance().isDebug()) {
            String message = createMessage(msg);
            Log.d(TAG, message);
        }
        if (MSIM.getInstance().isDebug()) {
            writeLog(msg);
        }
    }

    /**
     * log.e
     */
    public void error(String tag, String msg) {
        String message = createMessage(msg);
        if (MSIM.getInstance().isDebug()) {
            Log.e(TAG + " " + tag, message);
        }
        if (MSIM.getInstance().isDebug()) {
            writeLog(message);
        }
    }

    /**
     * log.error
     */
    public void error(Exception e) {
        StringBuilder sb = new StringBuilder();
        String name = getFunctionName();
        StackTraceElement[] sts = e.getStackTrace();

        if (name != null) {
            sb.append(name).append(" - ").append(e).append("\r\n");
        } else {
            sb.append(e).append("\r\n");
        }
        if (sts.length > 0) {
            for (StackTraceElement st : sts) {
                if (st != null) {
                    sb.append("[ ").append(st.getFileName()).append(":").append(st.getLineNumber()).append(" ]\r\n");
                }
            }
        }
        if (MSIM.getInstance().isDebug()) {
            Log.e(TAG, sb.toString());
        }
        if (MSIM.getInstance().isDebug()) {
            writeLog(sb.toString());
        }
    }

    /**
     * log.warn
     */
    private void warn(String tag, String msg) {
        String message = createMessage(msg);
        if (MSIM.getInstance().isDebug()) {
            System.out.println(message);
        } else {
            Log.w(TAG + " " + tag, message);
        }
        if (MSIM.getInstance().isDebug()) {
            writeLog(message);
        }
    }

    public void d(String msg) {
        debug(msg);

    }

    public void d(Exception e) {
        debug(e != null ? e.toString() : "null");
    }


    public void e(String msg) {
        error("", msg);
    }

    public void e(String tag, String msg) {
        error(tag, msg);
    }

    public void e(Exception e) {
        error(e);
    }

    /**
     * log.w
     */
    public void w(String tag, String msg) {
        warn(tag, msg);
    }

    public void w(String tag,Exception e) {
        warn(tag,e != null ? e.toString() : "null");
    }

    @SuppressLint("SimpleDateFormat")
    private void writeLog(String content) {
        try {
            if (MSIMApplication.getInstance().getContext() == null || !MSIM.getInstance().isWriteLog()) {
                return;
            }
            File file = new File(getLogFilePath());
            if (!file.exists()) {
                file.createNewFile();
            }
            SimpleDateFormat formate = new SimpleDateFormat(
                    "yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            FileWriter write = new FileWriter(file, true);
            write.write(formate.format(new Date()) + "   " +
                    content + "\n");
            write.flush();
            write.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
