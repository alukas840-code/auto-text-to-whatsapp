import { BotError } from '../domain.js';

export class WhatsAppBridgeAdapter {
  supportsDeleteMessage() { return false; }
  supportsKick() { return false; }
  supportsAdd() { return false; }

  async deleteMessage(_chatId, _messageId) {
    throw new BotError('Bridge не поддерживает удаление сообщений.', 'NOT_SUPPORTED');
  }

  async kickUser(_chatId, _userId) {
    throw new BotError('Bridge не поддерживает исключение участников.', 'NOT_SUPPORTED');
  }

  async addUser(_chatId, _phone) {
    throw new BotError('Bridge не поддерживает добавление участников.', 'NOT_SUPPORTED');
  }

  async sendGroupMessage(_chatId, _text) {}
  async sendDirectMessage(_userId, _text) {}
}

export class MockWhatsAppBridgeAdapter extends WhatsAppBridgeAdapter {
  constructor({ canDelete = true, canKick = false, canAdd = false } = {}) {
    super();
    this.canDelete = canDelete;
    this.canKick = canKick;
    this.canAdd = canAdd;
    this.calls = [];
  }

  supportsDeleteMessage() { return this.canDelete; }
  supportsKick() { return this.canKick; }
  supportsAdd() { return this.canAdd; }

  async deleteMessage(chatId, messageId) {
    if (!this.canDelete) return super.deleteMessage(chatId, messageId);
    this.calls.push({ action: 'delete', chatId, messageId });
    return true;
  }

  async kickUser(chatId, userId) {
    if (!this.canKick) return super.kickUser(chatId, userId);
    this.calls.push({ action: 'kick', chatId, userId });
    return true;
  }

  async addUser(chatId, phone) {
    if (!this.canAdd) return super.addUser(chatId, phone);
    this.calls.push({ action: 'add', chatId, phone });
    return true;
  }

  async sendGroupMessage(chatId, text) {
    this.calls.push({ action: 'send-group', chatId, text });
  }

  async sendDirectMessage(userId, text) {
    this.calls.push({ action: 'send-dm', userId, text });
  }
}
