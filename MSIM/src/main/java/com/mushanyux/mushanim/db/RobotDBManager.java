package com.mushanyux.mushanim.db;

import static com.mushanyux.mushanim.db.MSDBColumns.TABLE.robot;
import static com.mushanyux.mushanim.db.MSDBColumns.TABLE.robotMenu;

import android.content.ContentValues;
import android.database.Cursor;

import com.mushanyux.mushanim.MSIMApplication;
import com.mushanyux.mushanim.entity.MSRobot;
import com.mushanyux.mushanim.entity.MSRobotMenu;
import com.mushanyux.mushanim.utils.MSCommonUtils;

import java.util.ArrayList;
import java.util.List;

public class RobotDBManager {

    private RobotDBManager() {
    }

    private static class RobotDBManagerBinder {
        private final static RobotDBManager db = new RobotDBManager();
    }

    public static RobotDBManager getInstance() {
        return RobotDBManagerBinder.db;
    }

    public void insertOrUpdateMenus(List<MSRobotMenu> list) {
        for (MSRobotMenu menu : list) {
            if (isExitMenu(menu.robotID, menu.cmd)) {
                update(menu);
            } else {
                MSIMApplication.getInstance().getDbHelper().insert(robotMenu, getCV(menu));
            }
        }
    }

    public boolean isExitMenu(String robotID, String cmd) {
        boolean isExist = false;
        try (Cursor cursor = MSIMApplication
                .getInstance()
                .getDbHelper().select(robotMenu, "robot_id =? and cmd=?", new String[]{robotID, cmd}, null)) {
            if (cursor != null && cursor.moveToLast()) {
                isExist = true;
            }
        }
        return isExist;
    }

    private void update(MSRobotMenu menu) {
        String[] updateKey = new String[3];
        String[] updateValue = new String[3];
        updateKey[0] = "type";
        updateValue[0] = menu.type;
        updateKey[1] = "remark";
        updateValue[1] = menu.remark;
        updateKey[2] = "updated_at";
        updateValue[2] = menu.updatedAT;
        String where = "robot_id=? and cmd=?";
        String[] whereValue = new String[2];
        whereValue[0] = menu.robotID;
        whereValue[1] = menu.cmd;
        MSIMApplication.getInstance().getDbHelper()
                .update(robotMenu, updateKey, updateValue, where, whereValue);
    }

    public void insertOrUpdateRobots(List<MSRobot> list) {
        for (MSRobot robot : list) {
            if (isExist(robot.robotID)) {
                update(robot);
            } else {
                insert(robot);
            }
        }
    }

    public boolean isExist(String robotID) {
        boolean isExist = false;
        try (Cursor cursor = MSIMApplication
                .getInstance()
                .getDbHelper().select(robot, "robot_id=?", new String[]{robotID}, null)) {
            if (cursor != null && cursor.moveToLast()) {
                isExist = true;
            }
        }
        return isExist;
    }

    private void update(MSRobot msRobot) {
        String[] updateKey = new String[6];
        String[] updateValue = new String[6];
        updateKey[0] = "status";
        updateValue[0] = String.valueOf(msRobot.status);
        updateKey[1] = "version";
        updateValue[1] = String.valueOf(msRobot.version);
        updateKey[2] = "updated_at";
        updateValue[2] = String.valueOf(msRobot.updatedAT);
        updateKey[3] = "username";
        updateValue[3] = msRobot.username;
        updateKey[4] = "placeholder";
        updateValue[4] = msRobot.placeholder;
        updateKey[5] = "inline_on";
        updateValue[5] = String.valueOf(msRobot.inlineOn);

        String where = "robot_id=?";
        String[] whereValue = new String[1];
        whereValue[0] = msRobot.robotID;
        MSIMApplication.getInstance().getDbHelper()
                .update(robot, updateKey, updateValue, where, whereValue);

    }

    private void insert(MSRobot robot1) {
        ContentValues cv = getCV(robot1);
        MSIMApplication.getInstance().getDbHelper().insert(robot, cv);
    }

    public void insertRobots(List<MSRobot> list) {
        if (MSCommonUtils.isEmpty(list)) return;
        List<ContentValues> cvList = new ArrayList<>();
        for (MSRobot robot : list) {
            cvList.add(getCV(robot));
        }
        try {
            MSIMApplication.getInstance().getDbHelper().getDb().beginTransaction();
            for (ContentValues cv : cvList) {
                MSIMApplication.getInstance().getDbHelper().insert(robot, cv);
            }
            MSIMApplication.getInstance().getDbHelper().getDb().setTransactionSuccessful();

        } finally {
            MSIMApplication.getInstance().getDbHelper().getDb().endTransaction();
        }
    }

    public MSRobot query(String robotID) {
        MSRobot msRobot = null;
        try (Cursor cursor = MSIMApplication
                .getInstance()
                .getDbHelper().select(robot, "robot_id =?", new String[]{robotID}, null)) {
            if (cursor == null) {
                return null;
            }
            if (cursor.moveToLast()) {
                msRobot = serializeRobot(cursor);
            }
        }
        return msRobot;
    }

    public MSRobot queryWithUsername(String username) {
        MSRobot msRobot = null;
        try (Cursor cursor = MSIMApplication
                .getInstance()
                .getDbHelper().select(robot, "username=?", new String[]{username}, null)) {
            if (cursor == null) {
                return null;
            }
            if (cursor.moveToLast()) {
                msRobot = serializeRobot(cursor);
            }
        }
        return msRobot;
    }

    public List<MSRobot> queryRobots(List<String> robotIds) {
        List<MSRobot> list = new ArrayList<>();
        try (Cursor cursor = MSIMApplication.getInstance().getDbHelper().select(robot, "robot_id in (" + MSCursor.getPlaceholders(robotIds.size()) + ")", robotIds.toArray(new String[0]), null)) {
            if (cursor == null) {
                return list;
            }
            for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                MSRobot robot = serializeRobot(cursor);
                list.add(robot);
            }
        }
        return list;
    }

    public List<MSRobotMenu> queryRobotMenus(List<String> robotIds) {
        List<MSRobotMenu> list = new ArrayList<>();
        try (Cursor cursor = MSIMApplication.getInstance().getDbHelper().select(robotMenu, "robot_id in (" + MSCursor.getPlaceholders(robotIds.size()) + ")", robotIds.toArray(new String[0]), null)) {
            if (cursor == null) {
                return list;
            }
            for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                MSRobotMenu robotMenu = serializeRobotMenu(cursor);
                list.add(robotMenu);
            }
        }
        return list;
    }

    public List<MSRobotMenu> queryRobotMenus(String robotID) {
        List<MSRobotMenu> list = new ArrayList<>();
        try (Cursor cursor = MSIMApplication.getInstance().getDbHelper().select(robotMenu, "robot_id=?", new String[]{robotID}, null)) {
            if (cursor == null) {
                return list;
            }
            for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                MSRobotMenu robotMenu = serializeRobotMenu(cursor);
                list.add(robotMenu);
            }
        }
        return list;
    }

    public void insertMenus(List<MSRobotMenu> list) {
        if (MSCommonUtils.isEmpty(list)) return;
        List<ContentValues> cvList = new ArrayList<>();
        for (MSRobotMenu robot : list) {
            cvList.add(getCV(robot));
        }
        try {
            MSIMApplication.getInstance().getDbHelper().getDb().beginTransaction();
            for (ContentValues cv : cvList) {
                MSIMApplication.getInstance().getDbHelper().insert(robotMenu, cv);
            }
            MSIMApplication.getInstance().getDbHelper().getDb().setTransactionSuccessful();

        } finally {
            MSIMApplication.getInstance().getDbHelper().getDb().endTransaction();
        }
    }

    private MSRobot serializeRobot(Cursor cursor) {
        MSRobot robot = new MSRobot();
        robot.robotID = MSCursor.readString(cursor, "robot_id");
        robot.status = MSCursor.readInt(cursor, "status");
        robot.version = MSCursor.readLong(cursor, "version");
        robot.username = MSCursor.readString(cursor, "username");
        robot.inlineOn = MSCursor.readInt(cursor, "inline_on");
        robot.placeholder = MSCursor.readString(cursor, "placeholder");
        robot.createdAT = MSCursor.readString(cursor, "created_at");
        robot.updatedAT = MSCursor.readString(cursor, "updated_at");
        return robot;
    }

    private MSRobotMenu serializeRobotMenu(Cursor cursor) {
        MSRobotMenu robot = new MSRobotMenu();
        robot.robotID = MSCursor.readString(cursor, "robot_id");
        robot.type = MSCursor.readString(cursor, "type");
        robot.cmd = MSCursor.readString(cursor, "cmd");
        robot.remark = MSCursor.readString(cursor, "remark");
        robot.createdAT = MSCursor.readString(cursor, "created_at");
        robot.updatedAT = MSCursor.readString(cursor, "updated_at");
        return robot;
    }

    private ContentValues getCV(MSRobot msRobot) {
        ContentValues contentValues = new ContentValues();
        contentValues.put("robot_id", msRobot.robotID);
        contentValues.put("inline_on", msRobot.inlineOn);
        contentValues.put("username", msRobot.username);
        contentValues.put("placeholder", msRobot.placeholder);
        contentValues.put("status", msRobot.status);
        contentValues.put("version", msRobot.version);
        contentValues.put("created_at", msRobot.createdAT);
        contentValues.put("updated_at", msRobot.updatedAT);
        return contentValues;
    }

    private ContentValues getCV(MSRobotMenu robotMenu) {
        ContentValues contentValues = new ContentValues();
        contentValues.put("robot_id", robotMenu.robotID);
        contentValues.put("cmd", robotMenu.cmd);
        contentValues.put("remark", robotMenu.remark);
        contentValues.put("type", robotMenu.type);
        contentValues.put("created_at", robotMenu.createdAT);
        contentValues.put("updated_at", robotMenu.updatedAT);
        return contentValues;
    }
}
