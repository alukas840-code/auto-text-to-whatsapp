import { BotError, TASK_TYPES, WEEKDAYS_RU } from '../domain.js';

const TIME_RE = /^([01]\d|2[0-3]):([0-5]\d)$/;
const DATE_RE = /^(\d{2})\.(\d{2})(?:\.(\d{4}))?$/;

function parseDate(dateToken, now = new Date()) {
  const match = dateToken.match(DATE_RE);
  if (!match) return null;
  const [, dd, mm, yyyy] = match;
  const year = Number(yyyy || now.getUTCFullYear());
  const month = Number(mm);
  const day = Number(dd);
  const date = new Date(Date.UTC(year, month - 1, day, 0, 0, 0));
  if (date.getUTCFullYear() !== year || date.getUTCMonth() !== month - 1 || date.getUTCDate() !== day) {
    throw new BotError('Некорректная дата. Используйте формат DD.MM или DD.MM.YYYY.');
  }
  return `${String(day).padStart(2, '0')}.${String(month).padStart(2, '0')}.${year}`;
}

export function parseTaskArgs(args, now = new Date()) {
  if (!args.length) throw new BotError('Пустая команда /task.');

  let idx = 0;
  let reminderTime = null;
  if (TIME_RE.test(args[idx])) {
    reminderTime = args[idx++];
  }

  const assignees = [];
  while (idx < args.length && args[idx].startsWith('@')) {
    assignees.push(args[idx++].slice(1));
  }
  if (!assignees.length) {
    throw new BotError('Нужно указать хотя бы одного исполнителя в формате @user.');
  }

  let type = TASK_TYPES.SIMPLE;
  let dueDate = null;
  let weekday = null;

  if (idx < args.length) {
    const token = args[idx].toLowerCase();
    if (WEEKDAYS_RU.includes(token)) {
      type = TASK_TYPES.WEEKLY;
      weekday = token;
      idx += 1;
    } else {
      const date = parseDate(args[idx], now);
      if (date) {
        type = TASK_TYPES.DATED;
        dueDate = date;
        idx += 1;
      }
    }
  }

  const text = args.slice(idx).join(' ').trim();
  if (!text) {
    throw new BotError('Не указан текст задачи.');
  }

  return { type, reminderTime, assignees, dueDate, weekday, text };
}
