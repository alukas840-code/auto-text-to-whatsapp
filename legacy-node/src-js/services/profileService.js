import { BotError } from '../domain.js';

export class ProfileService {
  constructor(storage, accessService) {
    this.storage = storage;
    this.accessService = accessService;
  }

  list(chatId) {
    return this.storage.listGroupMembers(chatId);
  }

  who(targetId) {
    const user = this.storage.getUser(targetId);
    if (!user) throw new BotError('Пользователь не найден.', 'NOT_FOUND');
    return user;
  }

  profile(targetId) {
    const user = this.storage.getUser(targetId);
    if (!user) throw new BotError('Пользователь не найден.', 'NOT_FOUND');
    return user;
  }

  setSurname(actorId, targetId, surname) {
    this.accessService.requireCommandAccess(actorId, '/surname');
    if (!surname?.trim()) throw new BotError('Фамилия не может быть пустой.');
    this.storage.ensureUser(targetId);
    const updated = this.storage.setProfile(targetId, { surname: surname.trim() });
    this.storage.writeAudit({ actorId, action: 'set-surname', objectType: 'user', objectId: targetId, details: { surname: updated.surname } });
    return updated;
  }

  setRole(actorId, targetId, role) {
    this.accessService.requireCommandAccess(actorId, '/role');
    if (!role?.trim()) throw new BotError('Должность не может быть пустой.');
    this.storage.ensureUser(targetId);
    const updated = this.storage.setProfile(targetId, { role: role.trim() });
    this.storage.writeAudit({ actorId, action: 'set-role', objectType: 'user', objectId: targetId, details: { role: updated.role } });
    return updated;
  }

  setProfile(actorId, targetId, surname, role) {
    this.accessService.requireCommandAccess(actorId, '/setprofile');
    if (!surname?.trim() || !role?.trim()) {
      throw new BotError('Формат: /setprofile @user <фамилия> | <должность>.');
    }
    this.storage.ensureUser(targetId);
    const updated = this.storage.setProfile(targetId, { surname: surname.trim(), role: role.trim() });
    this.storage.writeAudit({ actorId, action: 'set-profile', objectType: 'user', objectId: targetId, details: { surname, role } });
    return updated;
  }
}
