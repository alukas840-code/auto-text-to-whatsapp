package com.dauren;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages tasks created via the WhatsApp bridge.  Tasks are stored in
 * memory and scheduled for reminders at their due date/time or on
 * their weekly recurrence.  This manager is intentionally kept
 * independent of any chat or command logic; the bridge should call
 * these methods when it receives appropriate commands from users.
 */
public class TaskManager {
    private final DaurenBotPlugin plugin;
    private final ZoneId zoneId;
    private final LocalTime defaultReminderTime;
    private final List<DaurenTask> tasks = new CopyOnWriteArrayList<>();
    private int nextId = 1;
    private BukkitTask schedulerTask;
    private BridgeService bridge;

    public TaskManager(DaurenBotPlugin plugin, String timezone, String defaultReminderTime) {
        this.plugin = plugin;
        this.zoneId = ZoneId.of(timezone);
        this.defaultReminderTime = LocalTime.parse(defaultReminderTime);
    }

    /**
     * Inject the bridge after construction.  This setter allows the
     * plugin to create the TaskManager before the BridgeService and
     * then supply the bridge instance once it has been constructed.
     *
     * @param bridge the BridgeService to use for delivering reminders
     */
    public void setBridge(BridgeService bridge) {
        this.bridge = bridge;
    }

    /**
     * Start the reminder scheduler.  This schedules a repeating task that
     * runs every minute and checks whether any tasks are due.  When a
     * task becomes due a reminder is sent via the bridge service.  If
     * the scheduler is already running this method has no effect.
     */
    public void startReminderScheduler() {
        if (schedulerTask != null) {
            return;
        }
        schedulerTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
            plugin,
            () -> {
                if (bridge == null || !bridge.isRunning()) {
                    return;
                }
                ZonedDateTime now = ZonedDateTime.now(zoneId);
                List<DaurenTask> dueTasks = new ArrayList<>();
                for (DaurenTask task : tasks) {
                    if (task.isCompleted()) {
                        continue;
                    }
                    // Check date-based task
                    if (task.getDueDate() != null) {
                        ZonedDateTime due = task.getDueDate().atZone(zoneId);
                        // Only send reminder if date/time is in the past or now
                        if (!now.isBefore(due)) {
                            dueTasks.add(task);
                        }
                    } else if (task.getWeeklyDay() != null) {
                        // Weekly recurring task: check if today is the day and time has passed
                        DayOfWeek today = now.getDayOfWeek();
                        if (today == task.getWeeklyDay()) {
                            LocalTime remind = task.getRemindTime();
                            if (remind == null) {
                                remind = defaultReminderTime;
                            }
                            LocalTime nowTime = now.toLocalTime();
                            if (!nowTime.isBefore(remind)) {
                                // Create a synthetic date/time for comparison
                                LocalDateTime thisCycle = LocalDateTime.of(now.toLocalDate(), remind);
                                ZonedDateTime cycleZdt = thisCycle.atZone(zoneId);
                                if (!now.isBefore(cycleZdt)) {
                                    dueTasks.add(task);
                                }
                            }
                        }
                    }
                }
                if (!dueTasks.isEmpty()) {
                    for (DaurenTask task : dueTasks) {
                        // Send reminder via bridge.  The bridge should implement the
                        // actual delivery to WhatsApp (group + DM).  If not
                        // implemented nothing happens.
                        if (bridge != null) {
                            bridge.sendTaskReminder(task);
                        }
                        // Mark one-off tasks as completed to avoid repeat reminders
                        if (task.getDueDate() != null) {
                            task.setCompleted(true);
                        }
                    }
                }
            },
            20L, // initial delay 1 second
            1200L // repeat every minute (1200 ticks)
        );
    }

    /**
     * Stop the reminder scheduler if it is running.
     */
    public void stopScheduler() {
        if (schedulerTask != null) {
            schedulerTask.cancel();
            schedulerTask = null;
        }
    }

    /**
     * Create tasks based on input.  For each assignee specified a
     * separate DaurenTask instance is created and stored.  The due
     * date, weekly day and remind time are optional; at most one of
     * dueDate or weeklyDay should be non-null.  This method returns a
     * list of created tasks for further processing (for example, to
     * acknowledge creation back to the user).
     *
     * @param assignees list of user identifiers (WhatsApp IDs)
     * @param creator the creator identifier
     * @param text the task description
     * @param dueDate the due date/time if provided
     * @param weeklyDay the recurring day of week if provided
     * @param remindTime the time of day for reminders (may be null)
     * @return list of created tasks
     */
    public List<DaurenTask> createTasks(List<String> assignees, String creator,
                                        LocalDateTime dueDate, DayOfWeek weeklyDay,
                                        LocalTime remindTime, String text) {
        if (assignees == null || assignees.isEmpty()) {
            return Collections.emptyList();
        }
        List<DaurenTask> created = new ArrayList<>();
        for (String assignee : assignees) {
            DaurenTask task = new DaurenTask(nextId++, assignee, creator, text, dueDate, weeklyDay,
                                             remindTime != null ? remindTime : defaultReminderTime);
            tasks.add(task);
            created.add(task);
        }
        return created;
    }

    /**
     * Retrieve a snapshot of current tasks.  The returned list is not
     * live and modifications to it will not affect the manager state.
     */
    public List<DaurenTask> getTasks() {
        return new ArrayList<>(tasks);
    }

    /**
     * Mark a task completed by id.  Returns true if a task was found
     * and updated, false otherwise.
     */
    public boolean markCompleted(int id) {
        for (DaurenTask task : tasks) {
            if (task.getId() == id) {
                task.setCompleted(true);
                return true;
            }
        }
        return false;
    }

    /**
     * Delete a task by id.  Returns true if a task was found and
     * removed, false otherwise.
     */
    public boolean deleteTask(int id) {
        Iterator<DaurenTask> it = tasks.iterator();
        while (it.hasNext()) {
            DaurenTask task = it.next();
            if (task.getId() == id) {
                it.remove();
                return true;
            }
        }
        return false;
    }
}