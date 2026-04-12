package com.dauren.tasks;

import com.dauren.bridge.WhatsAppBridgeService;
import com.dauren.model.TaskType;
import com.dauren.storage.SqliteStorage;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class SchedulerService {
    private final TaskService taskService;
    private final WhatsAppBridgeService bridge;
    private final ZoneId zoneId;

    public SchedulerService(TaskService taskService, WhatsAppBridgeService bridge, String timezone) {
        this.taskService = taskService;
        this.bridge = bridge;
        this.zoneId = ZoneId.of(timezone);
    }

    public int tick() {
        LocalDate date = LocalDate.now(zoneId);
        LocalTime time = LocalTime.now(zoneId).withSecond(0).withNano(0);
        String dateStr = date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        String timeStr = time.format(DateTimeFormatter.ofPattern("HH:mm"));
        String weekday = date.getDayOfWeek().getDisplayName(java.time.format.TextStyle.FULL, new Locale("ru")).toLowerCase(Locale.ROOT);
        String cycle = date.toString();

        int count = 0;
        for (SqliteStorage.TaskRow task : taskService.dueTasks(dateStr, timeStr, weekday)) {
            String currentCycle = task.taskType() == TaskType.WEEKLY ? cycle + ":" + weekday : cycle;
            if (task.taskType() == TaskType.WEEKLY && taskService.doneInCycle(task.id(), currentCycle)) continue;
            if (taskService.reminderSent(task.id(), currentCycle)) continue;
            String msg = "Напоминание по задаче #" + task.id() + ": " + task.text();
            bridge.sendGroupMessage(task.chatId(), "@" + task.assigneeId() + ", " + msg);
            bridge.sendDirectMessage(task.assigneeId(), msg);
            taskService.markReminder(task.id(), currentCycle);
            count++;
        }
        return count;
    }
}
