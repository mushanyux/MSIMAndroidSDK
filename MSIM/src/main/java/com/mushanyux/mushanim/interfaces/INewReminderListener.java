package com.mushanyux.mushanim.interfaces;

import com.mushanyux.mushanim.entity.MSReminder;

import java.util.List;

public interface INewReminderListener {
    void newReminder(List<MSReminder> list);
}
