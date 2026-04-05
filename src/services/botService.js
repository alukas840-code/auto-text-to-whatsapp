import { CHAT_TYPES, BotError } from '../domain.js';

function isCommand(text) {
  return text?.trim().startsWith('/');
}

function parseMention(token) {
  if (!token?.startsWith('@')) throw new BotError('Укажите пользователя в формате @user.');
  return token.slice(1);
}

export class BotService {
  constructor({ config, storage, accessService, taskService, profileService, antiSpamService, aiProvider, sttProvider, bridge }) {
    this.config = config;
    this.storage = storage;
    this.accessService = accessService;
    this.taskService = taskService;
    this.profileService = profileService;
    this.antiSpamService = antiSpamService;
    this.aiProvider = aiProvider;
    this.sttProvider = sttProvider;
    this.bridge = bridge;
  }

  canProcess(message) {
    if (message.chatType === CHAT_TYPES.DM) return true;
    if (message.chatType === CHAT_TYPES.GROUP) return this.config.allowedGroups.includes(message.chatId);
    return false;
  }

  ensureKnownUser(message) {
    this.storage.ensureUser(message.userId, message.userName || null);
    if (message.chatType === CHAT_TYPES.GROUP) {
      this.storage.rememberGroupMember(message.chatId, message.userId);
    }
  }

  async handleMessage(message) {
    if (!this.canProcess(message)) return null;
    this.ensureKnownUser(message);

    const text = (message.text || '').trim();
    if (!isCommand(text) && message.chatType === CHAT_TYPES.GROUP && !(message.mentionsBot || message.replyToBot)) {
      return null;
    }

    this.antiSpamService.assertInputLength(text);

    if (!isCommand(text) && message.chatType === CHAT_TYPES.DM) {
      this.antiSpamService.enforceCooldown(message.userId, 'ai');
      const greeting = this.ensureDmGreeting(message.userId);
      const response = await this.aiProvider.generateText(text, {
        persona: 'Дәурен, виртуальный коллега',
        style: 'уважительный, деловой, обращение только на Вы'
      });
      this.pushDmContext(message.userId, { role: 'user', text });
      this.pushDmContext(message.userId, { role: 'assistant', text: response });
      return this.antiSpamService.enforceOutputLength(greeting ? `${greeting}\n\n${response}` : response);
    }

    if (!isCommand(text)) return null;

    const [command, ...args] = text.split(/\s+/);
    this.accessService.requireCommandAccess(message.userId, command);

    if (command === '/g') {
      this.antiSpamService.bumpWindow(message.userId, '/g', this.config.antiSpam.gMaxPerWindow, this.config.antiSpam.gWindowSec);
    }
    if (command === '/task') {
      this.antiSpamService.bumpWindow(message.userId, '/task', this.config.antiSpam.taskMaxPerWindow, this.config.antiSpam.taskWindowSec);
    }
    if (['/ai', '/sum', '/rw', '/tr'].includes(command)) {
      this.antiSpamService.enforceCooldown(message.userId, 'ai');
    }

    switch (command) {
      case '/ping':
        return 'На связи.';
      case '/help':
        return this.helpForUser(message.userId, message.chatType);
      case '/clear':
        return this.clearContext(message);
      case '/ai':
        return this.antiSpamService.enforceOutputLength(await this.aiProvider.generateText(args.join(' ')));
      case '/sum':
        return this.antiSpamService.enforceOutputLength(await this.aiProvider.summarize(message.recentMessages ?? []));
      case '/rw':
        return this.antiSpamService.enforceOutputLength(await this.aiProvider.rewriteStyle(args.join(' '), 'вежливый деловой'));
      case '/tr': {
        const [direction, ...rest] = args;
        if (!['ru-kz', 'kz-ru'].includes(direction)) throw new BotError('Направление перевода должно быть ru-kz или kz-ru.');
        return this.antiSpamService.enforceOutputLength(`[${direction}] ${rest.join(' ')}`);
      }
      case '/g': {
        const hint = args[0] ?? null;
        if (hint && !['ru', 'kz'].includes(hint)) throw new BotError('Язык для /g должен быть ru или kz.');
        if (!message.replyAudioFile) throw new BotError('Команда /g работает только reply на голосовое сообщение.');
        return this.antiSpamService.enforceOutputLength(await this.sttProvider.transcribe(message.replyAudioFile, hint));
      }
      case '/dostup':
        return this.handleDostup(message, args);
      case '/list': {
        const users = this.profileService.list(message.chatId);
        if (!users.length) return 'Участники ещё не зарегистрированы в контексте бота.';
        return users.map((u) => `${u.surname || u.whatsapp_name || u.id} — ${u.role || 'не указано'}`).join('\n');
      }
      case '/who': {
        const target = parseMention(args[0]);
        const u = this.profileService.who(target);
        return `${u.surname || u.whatsapp_name || u.id}; должность: ${u.role || 'не указана'}; уровень: ${u.access_level}`;
      }
      case '/profile': {
        const target = parseMention(args[0]);
        const u = this.profileService.profile(target);
        return `Профиль @${u.id}\nwhatsapp_name: ${u.whatsapp_name || '-'}\nsurname: ${u.surname || '-'}\nrole: ${u.role || '-'}\naccess_level: ${u.access_level}`;
      }
      case '/surname': {
        const target = parseMention(args[0]);
        const surname = args.slice(1).join(' ');
        const u = this.profileService.setSurname(message.userId, target, surname);
        return `Фамилия обновлена: @${u.id} -> ${u.surname}`;
      }
      case '/role': {
        const target = parseMention(args[0]);
        const role = args.slice(1).join(' ');
        const u = this.profileService.setRole(message.userId, target, role);
        return `Должность обновлена: @${u.id} -> ${u.role}`;
      }
      case '/setprofile': {
        const target = parseMention(args[0]);
        const payload = args.slice(1).join(' ');
        const [surname, role] = payload.split('|').map((s) => s?.trim());
        const u = this.profileService.setProfile(message.userId, target, surname, role);
        return `Профиль обновлён: @${u.id} (${u.surname} | ${u.role})`;
      }
      case '/task': {
        const tasks = this.taskService.createFromCommand({ actorId: message.userId, chatId: message.chatId, args, now: message.now || new Date() });
        return `Создано задач: ${tasks.length}.`;
      }
      case '/tasks': {
        const tasks = this.taskService.list(message.chatId);
        if (!tasks.length) return 'Активных задач нет.';
        return tasks.map((t) => `#${t.id} @${t.assignee_id}: ${t.text}`).join('\n');
      }
      case '/done': {
        const taskId = Number(args[0]);
        if (!taskId) throw new BotError('Укажите ID задачи: /done <id>.');
        this.taskService.done({ actorId: message.userId, taskId, now: message.now || new Date() });
        return `Задача #${taskId} отмечена выполненной.`;
      }
      case '/taskdel': {
        const taskId = Number(args[0]);
        if (!taskId) throw new BotError('Укажите ID задачи: /taskdel <id>.');
        this.taskService.remove({ actorId: message.userId, taskId, now: message.now || new Date() });
        return `Задача #${taskId} удалена.`;
      }
      case '/del':
        return this.handleDelete(message);
      case '/kick':
        return this.handleKick(message, args);
      case '/add':
        return this.handleAdd(message, args);
      default:
        return 'Неизвестная команда. Используйте /help.';
    }
  }

  handleDostup(message, args) {
    if (args.length === 0) {
      return `Ваш уровень доступа: ${this.accessService.getOwnAccess(message.userId)}.`;
    }
    if (args.length !== 2) throw new BotError('Формат: /dostup или /dostup @user <1|2|3>.');
    const target = parseMention(args[0]);
    const level = Number(args[1]);
    this.accessService.setAccess(message.userId, target, level);
    return `Пользователю @${target} установлен уровень ${level}.`;
  }

  async handleDelete(message) {
    if (!message.replyToMessageId) throw new BotError('Команда /del работает только reply на удаляемое сообщение.');
    if (message.replyToMessageType && !['text', 'image', 'audio', 'video', 'document'].includes(message.replyToMessageType)) {
      throw new BotError('Нельзя удалить сообщение данного типа.');
    }
    if (!this.bridge.supportsDeleteMessage()) throw new BotError('Удаление сообщений не поддерживается текущим bridge.');
    await this.bridge.deleteMessage(message.chatId, message.replyToMessageId);
    this.storage.writeAudit({ actorId: message.userId, action: 'moderation-delete', objectType: 'message', objectId: String(message.replyToMessageId), details: { chatId: message.chatId } });
    return 'Сообщение удалено.';
  }

  async handleKick(message, args) {
    const target = parseMention(args[0]);
    if (!this.bridge.supportsKick()) throw new BotError('Исключение участника не поддерживается текущим bridge.');
    await this.bridge.kickUser(message.chatId, target);
    this.storage.writeAudit({ actorId: message.userId, action: 'moderation-kick', objectType: 'user', objectId: target, details: { chatId: message.chatId } });
    return `Пользователь @${target} исключён.`;
  }

  async handleAdd(message, args) {
    const phone = args[0];
    if (!/^\+\d{10,15}$/.test(phone || '')) throw new BotError('Формат номера: /add +70000000000');
    if (!this.bridge.supportsAdd()) throw new BotError('Добавление участника не поддерживается текущим bridge.');
    await this.bridge.addUser(message.chatId, phone);
    this.storage.writeAudit({ actorId: message.userId, action: 'moderation-add', objectType: 'phone', objectId: phone, details: { chatId: message.chatId } });
    return `Номер ${phone} добавлен в группу.`;
  }

  clearContext(message) {
    if (message.chatType === CHAT_TYPES.DM) {
      this.storage.clearContext('dm', message.userId);
      return 'Контекст личного диалога очищен.';
    }
    this.storage.clearContext('group', message.chatId);
    return 'Групповой AI-контекст очищен.';
  }

  ensureDmGreeting(userId) {
    const ctx = this.storage.getContext('dm', userId);
    if (ctx?.greeted) return null;
    this.storage.saveContext('dm', userId, [], true);
    return 'Здравствуйте, я Дәурен — ваш виртуальный коллега. Помогу с задачами, текстами, переводом и ответами на вопросы.';
  }

  pushDmContext(userId, message) {
    const ctx = this.storage.getContext('dm', userId);
    const arr = ctx?.context_json ? JSON.parse(ctx.context_json) : [];
    arr.push(message);
    this.storage.saveContext('dm', userId, arr.slice(-20), true);
  }

  helpForUser(userId, chatType) {
    const level = this.accessService.getLevel(userId);
    const base = ['/help', '/ai', '/sum', '/rw', '/tr', '/g', '/list', '/who', '/profile', '/dostup', '/tasks', '/done', '/ping', '/clear'];
    const lvl2 = ['/task', '/taskdel', '/del', '/kick', '/add'];
    const lvl3 = ['/surname', '/role', '/setprofile', '/dostup @user 1|2|3'];
    const commands = [...base, ...(level >= 2 ? lvl2 : []), ...(level >= 3 ? lvl3 : [])];

    if (chatType === CHAT_TYPES.DM) {
      return `Здравствуйте, я Дәурен — ваш виртуальный коллега.\nДоступные команды: ${commands.join(', ')}`;
    }
    return `Список доступных команд: ${commands.join(', ')}`;
  }
}
