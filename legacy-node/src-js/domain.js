export const CHAT_TYPES = {
  DM: 'dm',
  GROUP: 'group'
};

export const TASK_TYPES = {
  SIMPLE: 'simple',
  DATED: 'dated',
  WEEKLY: 'weekly'
};

export const WEEKDAYS_RU = [
  'понедельник',
  'вторник',
  'среда',
  'четверг',
  'пятница',
  'суббота',
  'воскресенье'
];

export class BotError extends Error {
  constructor(message, code = 'BAD_REQUEST') {
    super(message);
    this.name = 'BotError';
    this.code = code;
  }
}
