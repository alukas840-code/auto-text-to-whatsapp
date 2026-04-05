import test from 'node:test';
import assert from 'node:assert/strict';
import { rmSync } from 'node:fs';
import { createBot } from '../src/index.js';
import { MockWhatsAppBridgeAdapter } from '../src/bridge/whatsAppBridge.js';

function makeBot(name, bridgeOptions = {}) {
  const dbPath = `.data/test-${name}.sqlite`;
  rmSync(dbPath, { force: true });
  return createBot({ dbPath, bridge: new MockWhatsAppBridgeAdapter(bridgeOptions) });
}

async function msg(bot, payload) {
  return bot.botService.handleMessage({
    chatType: 'group',
    chatId: '1203630XXXXXXXX@g.us',
    userId: 'u1',
    userName: 'User One',
    text: '/ping',
    ...payload
  });
}

test('/dostup shows own level and admin can change', async () => {
  const bot = makeBot('dostup-1');
  bot.storage.ensureUser('admin', 'Admin');
  bot.storage.setAccessLevel('admin', 3);

  const own = await msg(bot, { userId: 'u2', userName: 'User 2', text: '/dostup' });
  assert.equal(own, 'Ваш уровень доступа: 1.');

  const set = await msg(bot, { userId: 'admin', text: '/dostup @u2 2' });
  assert.equal(set, 'Пользователю @u2 установлен уровень 2.');

  const own2 = await msg(bot, { userId: 'u2', text: '/dostup' });
  assert.equal(own2, 'Ваш уровень доступа: 2.');
});

test('/list /who /profile commands', async () => {
  const bot = makeBot('profiles-1');
  bot.storage.ensureUser('admin', 'Admin');
  bot.storage.setAccessLevel('admin', 3);

  await msg(bot, { userId: 'admin', text: '/setprofile @u5 Иванов | Аналитик' });
  await msg(bot, { userId: 'u5', userName: 'Ivan User', text: '/ping' });

  const list = await msg(bot, { userId: 'u1', text: '/list' });
  assert.match(list, /Иванов — Аналитик/);

  const who = await msg(bot, { userId: 'u1', text: '/who @u5' });
  assert.match(who, /уровень: 1/);

  const profile = await msg(bot, { userId: 'u1', text: '/profile @u5' });
  assert.match(profile, /whatsapp_name: Ivan User/);
  assert.match(profile, /surname: Иванов/);
});

test('/surname /role /setprofile only level 3', async () => {
  const bot = makeBot('profiles-2');
  bot.storage.ensureUser('admin', 'Admin');
  bot.storage.setAccessLevel('admin', 3);

  await assert.rejects(() => msg(bot, { userId: 'u1', text: '/surname @u2 Петров' }), /Недостаточно прав/);

  const ok = await msg(bot, { userId: 'admin', text: '/surname @u2 Петров' });
  assert.match(ok, /Петров/);

  const role = await msg(bot, { userId: 'admin', text: '/role @u2 Инженер' });
  assert.match(role, /Инженер/);

  const set = await msg(bot, { userId: 'admin', text: '/setprofile @u2 Сидоров | Руководитель' });
  assert.match(set, /Сидоров/);
});

test('/del validates reply and uses bridge', async () => {
  const bot = makeBot('moderation-1');
  bot.storage.ensureUser('admin', 'Admin');
  bot.storage.setAccessLevel('admin', 2);

  await assert.rejects(() => msg(bot, { userId: 'admin', text: '/del' }), /reply/);

  const ok = await msg(bot, { userId: 'admin', text: '/del', replyToMessageId: 'm-1' });
  assert.equal(ok, 'Сообщение удалено.');
});

test('/kick and /add return clear error when unsupported', async () => {
  const bot = makeBot('moderation-2');
  bot.storage.ensureUser('admin', 'Admin');
  bot.storage.setAccessLevel('admin', 2);

  await assert.rejects(() => msg(bot, { userId: 'admin', text: '/kick @u9' }), /не поддерживается/);

  await assert.rejects(() => msg(bot, { userId: 'admin', text: '/add +77000000000' }), /не поддерживается/);
});

test('persona behavior in DM and /clear', async () => {
  const bot = makeBot('persona-1');

  const first = await bot.botService.handleMessage({ chatType: 'dm', chatId: 'u1', userId: 'u1', userName: 'User One', text: 'Добрый день' });
  assert.match(first, /Здравствуйте, я Дәурен/);
  assert.match(first, /Понял Вас/);

  const clear = await bot.botService.handleMessage({ chatType: 'dm', chatId: 'u1', userId: 'u1', text: '/clear' });
  assert.equal(clear, 'Контекст личного диалога очищен.');
});

test('permission checks for levels 1/2/3', async () => {
  const bot = makeBot('levels-1');
  bot.storage.ensureUser('lvl3', 'L3');
  bot.storage.setAccessLevel('lvl3', 3);
  bot.storage.ensureUser('lvl2', 'L2');
  bot.storage.setAccessLevel('lvl2', 2);

  await assert.rejects(() => msg(bot, { userId: 'lvl1', text: '/task @u9 тест' }), /Требуется уровень 2/);

  const lvl2CanTask = await msg(bot, { userId: 'lvl2', text: '/task @u9 25.05.2026 задача' });
  assert.equal(lvl2CanTask, 'Создано задач: 1.');

  await assert.rejects(() => msg(bot, { userId: 'lvl2', text: '/setprofile @u9 A | B' }), /Требуется уровень 3/);

  const lvl3CanSetProfile = await msg(bot, { userId: 'lvl3', text: '/setprofile @u9 A | B' });
  assert.match(lvl3CanSetProfile, /Профиль обновлён/);
});
