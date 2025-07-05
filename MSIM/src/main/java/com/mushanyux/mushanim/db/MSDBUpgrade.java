package com.mushanyux.mushanim.db;

import android.content.res.AssetManager;
import android.text.TextUtils;

import com.mushanyux.mushanim.MSIMApplication;
import com.mushanyux.mushanim.utils.MSLoggerUtils;

import net.sqlcipher.database.SQLiteDatabase;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MSDBUpgrade {
    private static final String TAG = "MSDBUpgrade";

    private MSDBUpgrade() {
    }

    static class DBUpgradeBinder {
        final static MSDBUpgrade db = new MSDBUpgrade();
    }

    public static MSDBUpgrade getInstance() {
        return DBUpgradeBinder.db;
    }

    void onUpgrade(SQLiteDatabase db) {
        long maxIndex = MSIMApplication.getInstance().getDBUpgradeIndex();
        long tempIndex = maxIndex;
        List<MSDBSql> list = getExecSQL();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).index > maxIndex && list.get(i).sqlList != null && !list.get(i).sqlList.isEmpty()) {
                for (String sql : list.get(i).sqlList) {
                    if (!TextUtils.isEmpty(sql)) {
                        db.execSQL(sql);
                    }
                }
                if (list.get(i).index > tempIndex) {
                    tempIndex = list.get(i).index;
                }
            }
        }
        MSIMApplication.getInstance().setDBUpgradeIndex(tempIndex);
    }

    private List<MSDBSql> getExecSQL() {
        List<MSDBSql> sqlList = new ArrayList<>();

        AssetManager assetManager = MSIMApplication.getInstance().getContext().getAssets();
        if (assetManager != null) {
            try {
                String[] strings = assetManager.list("ms_sql");
                if (strings == null || strings.length == 0) {
                    MSLoggerUtils.getInstance().e(TAG,"Failed to read SQL");
                }
                assert strings != null;
                for (String str : strings) {
                    StringBuilder stringBuilder = new StringBuilder();
                    BufferedReader bf = new BufferedReader(new InputStreamReader(
                            assetManager.open("ms_sql/" + str)));
                    String line;
                    while ((line = bf.readLine()) != null) {
                        stringBuilder.append(line);
                    }

                    String temp = str.replaceAll(".sql", "");
                    List<String> list = new ArrayList<>();
                    if (stringBuilder.toString().contains(";")) {
                        list = Arrays.asList(stringBuilder.toString().split(";"));
                    } else list.add(stringBuilder.toString());
                    sqlList.add(new MSDBSql(Long.parseLong(temp), list));
                }
            } catch (IOException e) {
                MSLoggerUtils.getInstance().e(TAG , "getExecSQL error");
            }
        }
        return sqlList;
    }
}
