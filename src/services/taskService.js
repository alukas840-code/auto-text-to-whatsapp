import { TASK_TYPES, BotError, WEEKDAYS_RU } from '../domain.js';
import { parseTaskArgs } from '../parsers/taskCommandParser.js';

function formatDateForTask(now, timezone) {
  const parts = new Intl.DateTimeFormat('en-GB', {
    timeZone: timezone,
    day: '2-digit',
    month: '2-digit',
    year: 'numeric'
  }).formatToParts(now);
  const dd = parts.find((p) => p.type === 'day').value;
  const mm = parts.find((p) => p.type === 'month').value;
  const yyyy = parts.find((p) => p.type === 'year').value;
  return `${dd}.${mm}.${yyyy}`;
}

function formatTime(now, timezone) {
  return new Intl.DateTimeFormat('en-GB', {
    timeZone: timezone,
    hour: '2-digit',
    minute: '2-digit',
    hour12: false
  }).format(now);
}

function weekdayRu(now, timezone) {
  const wd = new Intl.DateTimeFormat('ru-RU', { timeZone: timezone, weekday: 'long' }).format(now).toLowerCase();
  if (!WEEKDAYS_RU.includes(wd)) return WEEKDAYS_RU[0];
  return wd;
}

export class TaskService {
  constructor(storage, accessService, config) {
    this.storage = storage;
    this.accessService = accessService;
    this.config = config;
  }

  createFromCommand({ actorId, chatId, args, now = new Date() }) {
    this.accessService.requireCommandAccess(actorId, '/task');
    const parsed = parseTaskArgs(args, now);
    const remindTime = parsed.reminderTime ?? this.config.defaultReminderTime;

    const tasks = parsed.assignees.map((assigneeId) => {
      this.storage.ensureUser(assigneeId);
      const task = this.storage.createTask({
        type: parsed.type,
        chatId,
        assigneeId,
        createdBy: actorId,
        text: parsed.text,
        remindTime,
        dueDate: parsed.type === TASK_TYPES.DATED ? parsed.dueDate : null,
        weekday: parsed.type === TASK_TYPES.WEEKLY ? parsed.weekday : null
      });
      this.storage.logTaskEvent(task.id, 'created', actorId, { assigneeId });
      this.storage.writeAudit({ actorId, action: 'task-create', objectType: 'task', objectId: String(task.id), details: { assigneeId } });
      return task;
    });

    return tasks;
  }

  list(chatId) {
    return this.storage.listActiveTasks(chatId);
  }

  done({ actorId, taskId, now = new Date() }) {
    const task = this.storage.getTask(taskId);
    if (!task || task.deleted_at) throw new BotError('Задача не найдена.', 'NOT_FOUND');

    const actorLevel = this.accessService.getLevel(actorId);
    const canMark = actorId === task.assignee_id || actorLevel >= 2;
    if (!canMark) throw new BotError('Можно отмечать только свои задачи.', 'FORBIDDEN');

    this.storage.markTaskDone(task.id, now.toISOString());
    this.storage.logTaskEvent(task.id, 'done', actorId);
    this.storage.writeAudit({ actorId, action: 'task-done', objectType: 'task', objectId: String(task.id) });
    return this.storage.getTask(task.id);
  }

  remove({ actorId, taskId, now = new Date() }) {
    const task = this.storage.getTask(taskId);
    if (!task || task.deleted_at) throw new BotError('Задача не найдена.', 'NOT_FOUND');
    const actorLevel = this.accessService.getLevel(actorId);
    if (!(task.created_by === actorId || actorLevel >= 2)) {
      throw new BotError('Удалять задачу может создатель или уровень 2+.', 'FORBIDDEN');
    }

    this.storage.deleteTask(task.id, now.toISOString());
    this.storage.logTaskEvent(task.id, 'deleted', actorId);
    this.storage.writeAudit({ actorId, action: 'task-delete', objectType: 'task', objectId: String(task.id) });
    return true;
  }

  async processDueReminders(now, bridge) {
    const dateStr = formatDateForTask(now, this.config.timezone);
    const timeStr = formatTime(now, this.config.timezone);
    const weekday = weekdayRu(now, this.config.timezone);

    const tasks = this.storage.getDueTasks(dateStr, timeStr, weekday);
    const reminders = [];

    for (const task of tasks) {
      const periodKey = task.type === TASK_TYPES.DATED ? dateStr : `${dateStr}:${weekday}`;
      if (this.storage.hasTaskEvent(task.id, 'reminder-sent', periodKey)) continue;

      const msg = `Напоминание по задаче #${task.id}: ${task.text}`;
      await bridge.sendGroupMessage(task.chat_id, `@${task.assignee_id}, ${msg}`);
      await bridge.sendDirectMessage(task.assignee_id, msg);
      this.storage.logTaskEvent(task.id, 'reminder-sent', null, { periodKey });
      reminders.push(task.id);
    }

    return reminders;
  }
}
