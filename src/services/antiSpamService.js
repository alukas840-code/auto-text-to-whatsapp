import { BotError } from '../domain.js';

export class AntiSpamService {
  constructor(storage, config) {
    this.storage = storage;
    this.config = config;
  }

  assertInputLength(text) {
    if ((text || '').length > this.config.maxInputChars) {
      throw new BotError(`Слишком длинный запрос. Лимит: ${this.config.maxInputChars} символов.`, 'TOO_LARGE');
    }
  }

  enforceOutputLength(text) {
    if ((text || '').length <= this.config.maxOutputChars) return text;
    return `${text.slice(0, this.config.maxOutputChars)}…`;
  }

  enforceCooldown(userId, scope = 'ai') {
    const now = Math.floor(Date.now() / 1000);
    const key = `cooldown:${scope}:${userId}`;
    const row = this.storage.getRateLimit(key);
    if (row && Number(row.cooldown_until) > now) {
      const wait = Number(row.cooldown_until) - now;
      throw new BotError(`Слишком часто. Повторите через ${wait} сек.`, 'RATE_LIMITED');
    }
    this.storage.saveRateLimit(key, now, 0, now + this.config.aiCooldownSec);
  }

  bumpWindow(userId, command, maxPerWindow, windowSec) {
    const now = Math.floor(Date.now() / 1000);
    const key = `window:${command}:${userId}`;
    const row = this.storage.getRateLimit(key);
    const start = row ? Number(row.window_start) : now;
    let count = row ? Number(row.count) : 0;

    if (now - start >= windowSec) {
      count = 0;
    }

    count += 1;
    if (count > maxPerWindow) {
      throw new BotError(`Слишком частое использование ${command}. Попробуйте позже.`, 'RATE_LIMITED');
    }

    this.storage.saveRateLimit(key, now - (now - start >= windowSec ? 0 : now - start), count, 0);
  }
}
