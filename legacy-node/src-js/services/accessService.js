import { BotError } from '../domain.js';

const COMMAND_LEVELS = {
  '/help': 1,
  '/ai': 1,
  '/sum': 1,
  '/rw': 1,
  '/tr': 1,
  '/g': 1,
  '/list': 1,
  '/who': 1,
  '/profile': 1,
  '/dostup': 1,
  '/tasks': 1,
  '/done': 1,
  '/ping': 1,
  '/clear': 1,
  '/task': 2,
  '/taskdel': 2,
  '/del': 2,
  '/kick': 2,
  '/add': 2,
  '/surname': 3,
  '/role': 3,
  '/setprofile': 3
};

export class AccessService {
  constructor(storage) {
    this.storage = storage;
  }

  getLevel(userId) {
    return this.storage.getAccessLevel(userId);
  }

  requireCommandAccess(userId, command) {
    const level = this.getLevel(userId);
    const needed = COMMAND_LEVELS[command] ?? 3;
    if (level < needed) {
      throw new BotError(`Недостаточно прав для команды ${command}. Требуется уровень ${needed}.`, 'FORBIDDEN');
    }
  }

  getOwnAccess(userId) {
    return this.getLevel(userId);
  }

  setAccess(actorId, targetId, level) {
    this.requireCommandAccess(actorId, '/setprofile');
    if (![1, 2, 3].includes(level)) {
      throw new BotError('Уровень доступа должен быть 1, 2 или 3.');
    }
    this.storage.ensureUser(targetId);
    this.storage.setAccessLevel(targetId, level);
    this.storage.writeAudit({ actorId, action: 'set-access-level', objectType: 'user', objectId: targetId, details: { level } });
    return level;
  }
}
