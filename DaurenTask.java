package com.dauren;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Represents a task created via the /task command.  A task can be
 * simple (no due date), dated (dueDate non-null) or weekly recurring
 * (weeklyDay non-null).  Each task belongs to an assignee and
 * optionally a creator.  The remindTime defines the time of day when
 * reminders should be sent.  A single logical task can spawn
 * multiple DaurenTask instances if it is assigned to multiple users.
 */
public class DaurenTask {
    private final int id;
    private final String assignee;
    private final String creator;
    private final String text;
    private final LocalDateTime dueDate;
    private final DayOfWeek weeklyDay;
    private final LocalTime remindTime;
    private boolean completed;

    public DaurenTask(int id, String assignee, String creator, String text,
                      LocalDateTime dueDate, DayOfWeek weeklyDay, LocalTime remindTime) {
        this.id = id;
        this.assignee = assignee;
        this.creator = creator;
        this.text = text;
        this.dueDate = dueDate;
        this.weeklyDay = weeklyDay;
        this.remindTime = remindTime;
        this.completed = false;
    }

    public int getId() {
        return id;
    }

    public String getAssignee() {
        return assignee;
    }

    public String getCreator() {
        return creator;
    }

    public String getText() {
        return text;
    }

    public LocalDateTime getDueDate() {
        return dueDate;
    }

    public DayOfWeek getWeeklyDay() {
        return weeklyDay;
    }

    public LocalTime getRemindTime() {
        return remindTime;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }
}