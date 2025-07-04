package com.mushanyux.mushanim.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.text.TextUtils;

import com.mushanyux.mushanim.utils.MSLoggerUtils;

import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteOpenHelper;

import java.util.Map;

public class MSDBHelper {
    private static final String TAG = "MSDBHelper";
    private DatabaseHelper mDbHelper;
    private SQLiteDatabase mDb;

    public SQLiteDatabase getDb() {
        return mDb;
    }

    private volatile static MSDBHelper openHelper = null;
    // 数据库版本
    private final static int version = 1;
    private static String myDBName;
    private static String uid;

    private MSDBHelper(Context ctx, String uid) {
        MSDBHelper.uid = uid;
        myDBName = "ms_" + uid + ".db";

        try {
            mDbHelper = new DatabaseHelper(ctx);
            mDb = mDbHelper.getWritableDatabase(uid);
            MSDBUpgrade.getInstance().onUpgrade(mDb);
        } catch (Exception e) {
            MSLoggerUtils.getInstance().e(TAG + " init MSDBHelper error");
        }
    }

    /**
     * 创建数据库实例
     *
     * @param context 上下文
     * @param _uid    用户ID
     * @return db
     */
    public synchronized static MSDBHelper getInstance(Context context, String _uid) {
        if (TextUtils.isEmpty(uid) || !uid.equals(_uid) || openHelper == null) {
            synchronized (MSDBHelper.class) {
                if (openHelper != null) {
                    openHelper.close();
                    openHelper = null;
                }
                openHelper = new MSDBHelper(context, _uid);
            }
        }
        return openHelper;
    }

    public static class DatabaseHelper extends SQLiteOpenHelper {
        DatabaseHelper(Context context) {
            super(context, myDBName, null, version);
            //不可忽略的 进行so库加载
            SQLiteDatabase.loadLibs(context);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int arg1, int arg2) {
        }
    }

    /**
     * 关闭数据库
     */
    public void close() {
        try {
            uid = "";
            if (mDb != null) {
                mDb.close();
                mDb = null;
            }
            myDBName = "";
            if (mDbHelper != null) {
                mDbHelper.close();
                mDbHelper = null;
            }
        } catch (Exception e) {
            MSLoggerUtils.getInstance().e(TAG + " close MSDBHelper error");
        }
    }

    void insertSql(String tab, ContentValues cv) {
        if (mDb == null) {
            return;
        }
        mDb.insertWithOnConflict(tab, "", cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public Cursor rawQuery(String sql) {
        if (mDb == null) {
            return null;
        }
        return mDb.rawQuery(sql, null);
    }

    public Cursor rawQuery(String sql, Object[] selectionArgs) {
        if (mDb == null) {
            return null;
        }
        return mDb.rawQuery(sql, selectionArgs);
    }

    public Cursor select(String table, String selection,
                         String[] selectionArgs,
                         String orderBy) {
        if (mDb == null) return null;
        Cursor cursor;
        try {
            cursor = mDb.query(table, null, selection, selectionArgs,
                    null, null, orderBy);
        } catch (Exception e) {
            MSLoggerUtils.getInstance().e(TAG + " select MSDBHelper error");
            return null;
        }
        return cursor;
    }

    public long insert(String table, ContentValues cv) {
        if (mDb == null) return 0;
        long count = 0;
        try {
            count = mDb.insert(table, SQLiteDatabase.CONFLICT_REPLACE, cv);
        } catch (Exception e) {
            StringBuilder fields = new StringBuilder();
            for (Map.Entry<String, Object> item : cv.valueSet()) {
                if (!TextUtils.isEmpty(fields)) {
                    fields.append(",");
                }
                fields.append(item.getKey()).append(":").append(item.getValue());
            }
            MSLoggerUtils.getInstance().e(TAG, "Database insertion exception，Table：" + table + "，Fields：" + fields);
        }
        return count;
    }

    public boolean delete(String tableName, String where, String[] whereValue) {
        if (mDb == null) return false;
        int count = mDb.delete(tableName, where, whereValue);
        return count > 0;
    }

    public int update(String table, String[] updateFields,
                      String[] updateValues, String where, String[] whereValue) {
        if (mDb == null) return 0;
        ContentValues cv = new ContentValues();
        for (int i = 0; i < updateFields.length; i++) {
            cv.put(updateFields[i], updateValues[i]);
        }
        int count = 0;
        try {
            count = mDb.update(table, cv, where, whereValue);
        } catch (Exception e) {
            MSLoggerUtils.getInstance().e(TAG, "update MSDBHelper error");
        }
        return count;
    }

    public boolean update(String tableName, ContentValues cv, String where,
                          String[] whereValue) {
        if (mDb == null) return false;
        boolean flag = false;
        try {
            flag = mDb.update(tableName, cv, where, whereValue) > 0;
        } catch (Exception e) {
            MSLoggerUtils.getInstance().e(TAG, "update MSDBHelper error");
        }
        return flag;
    }

    public boolean update(String tableName, String whereClause,
                          ContentValues args) {
        if (mDb == null) return false;
        boolean flag = false;
        try {
            flag = mDb.update(tableName, args, whereClause, null) > 0;
        } catch (Exception e) {
            MSLoggerUtils.getInstance().e(TAG + " update MSDBHelper error");
        }
        return flag;
    }

}