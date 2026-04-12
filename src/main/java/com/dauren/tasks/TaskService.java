package com.dauren.tasks;

import com.dauren.model.TaskSpec;
import com.dauren.model.TaskType;
import com.dauren.services.AuditLogService;
import com.dauren.storage.SqliteStorage;
import com.dauren.users.AccessService;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class TaskService {
    private final SqliteStorage storage;
    private final AccessService accessService;
    private final TaskCommandParser parser;
    private final AuditLogService audit;
    private final String defaultReminder;

    public TaskService(SqliteStorage storage, AccessService accessService, TaskCommandParser parser, AuditLogService audit, String defaultReminder) {
        this.storage = storage;
        this.accessService = accessService;
        this.parser = parser;
        this.audit = audit;
        this.defaultReminder = defaultReminder;
    }

    public int create(String actorId, String chatId, List<String> args) {
        accessService.require(actorId, "/task");
        TaskSpec spec = parser.parse(args, LocalDate.now());
        String remind = spec.reminderTime() == null ? defaultReminder : spec.reminderTime();
        int count = 0;
        for (String assignee : spec.assignees()) {
            storage.ensureUser(assignee, null);
            long id = storage.createTask(chatId, assignee, actorId, spec.text(), spec.type(), spec.dueDate(), spec.weekday(), remind);
            storage.addTaskEvent(id, "created", "{}");
            count++;
        }
        audit.log(actorId, "task-create", "chat", chatId, "{\"count\":" + count + "}");
        return count;
    }

    public List<SqliteStorage.TaskRow> list(String chatId) {
        return storage.listActiveTasks(chatId);
    }

    public void done(String actorId, long taskId, String cycleKey) {
        var task = storage.getTask(taskId).orElseThrow(() -> new IllegalArgumentException("Задача не найдена"));
        int level = accessService.levelOf(actorId);
        if (!actorId.equals(task.assigneeId()) && level < 2) throw new IllegalArgumentException("Можно отмечать только свои задачи");
        storage.markTaskDone(taskId, cycleKey, actorId);
        audit.log(actorId, "task-done", "task", String.valueOf(taskId), "{}");
    }

    public void delete(String actorId, long taskId) {
        var task = storage.getTask(taskId).orElseThrow(() -> new IllegalArgumentException("Задача не найдена"));
        int level = accessService.levelOf(actorId);
        if (!actorId.equals(task.createdBy()) && level < 2) throw new IllegalArgumentException("Удалять может создатель или уровень 2+");
        storage.deleteTask(taskId);
        audit.log(actorId, "task-delete", "task", String.valueOf(taskId), "{}");
    }

    public List<SqliteStorage.TaskRow> dueTasks(String date, String time, String weekday) {
        return storage.dueTasks(date, time, weekday);
    }

    public boolean doneInCycle(long taskId, String cycleKey) {
        return storage.hasEvent(taskId, "done", cycleKey);
    }

    public boolean reminderSent(long taskId, String cycleKey) {
        return storage.hasEvent(taskId, "reminder-sent", cycleKey);
    }

    public void markReminder(long taskId, String cycleKey) {
        storage.addTaskEvent(taskId, "reminder-sent", "{\"cycle\":\"" + cycleKey + "\"}");
    }
}
