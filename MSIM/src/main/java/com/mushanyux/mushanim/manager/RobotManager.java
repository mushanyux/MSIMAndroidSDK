package com.mushanyux.mushanim.manager;

import android.text.TextUtils;

import com.mushanyux.mushanim.db.RobotDBManager;
import com.mushanyux.mushanim.entity.MSRobot;
import com.mushanyux.mushanim.entity.MSRobotMenu;
import com.mushanyux.mushanim.interfaces.IRefreshRobotMenu;
import com.mushanyux.mushanim.utils.MSCommonUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RobotManager extends BaseManager {

    private RobotManager() {
    }

    private static class RobotManagerBinder {
        final static RobotManager manager = new RobotManager();
    }

    public static RobotManager getInstance() {
        return RobotManagerBinder.manager;
    }

    private ConcurrentHashMap<String, IRefreshRobotMenu> refreshRobotMenu;

    public MSRobot getWithRobotID(String robotID) {
        return RobotDBManager.getInstance().query(robotID);
    }

    public MSRobot getWithUsername(String username) {
        return RobotDBManager.getInstance().queryWithUsername(username);
    }

    public List<MSRobot> getWithRobotIds(List<String> robotIds) {
        return RobotDBManager.getInstance().queryRobots(robotIds);
    }

    public List<MSRobotMenu> getRobotMenus(String robotID) {
        return RobotDBManager.getInstance().queryRobotMenus(robotID);
    }

    public List<MSRobotMenu> getRobotMenus(List<String> robotIds) {
        return RobotDBManager.getInstance().queryRobotMenus(robotIds);
    }

    public void saveOrUpdateRobots(List<MSRobot> list) {
        if (MSCommonUtils.isNotEmpty(list)) {
            RobotDBManager.getInstance().insertOrUpdateRobots(list);
        }
    }

    public void saveOrUpdateRobotMenus(List<MSRobotMenu> list) {
        if (MSCommonUtils.isNotEmpty(list)) {
            RobotDBManager.getInstance().insertOrUpdateMenus(list);
        }
        setRefreshRobotMenu();
    }

    public void addOnRefreshRobotMenu(String key, IRefreshRobotMenu iRefreshRobotMenu) {
        if (TextUtils.isEmpty(key) || iRefreshRobotMenu == null) return;
        if (refreshRobotMenu == null) refreshRobotMenu = new ConcurrentHashMap<>();
        refreshRobotMenu.put(key, iRefreshRobotMenu);
    }

    public void removeRefreshRobotMenu(String key) {
        if (TextUtils.isEmpty(key) || refreshRobotMenu == null) return;
        refreshRobotMenu.remove(key);
    }

    private void setRefreshRobotMenu() {
        runOnMainThread(() -> {
            for (Map.Entry<String, IRefreshRobotMenu> entry : refreshRobotMenu.entrySet()) {
                entry.getValue().onRefreshRobotMenu();
            }
        });
    }
}
