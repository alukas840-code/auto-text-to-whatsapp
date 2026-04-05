import test from 'node:test';
import assert from 'node:assert/strict';
import { rmSync } from 'node:fs';
import { createBot } from '../src/index.js';
import { MockWhatsAppBridgeAdapter } from '../src/bridge/whatsAppBridge.js';

function build(name) {
  const dbPath = `.data/test-${name}.sqlite`;
  rmSync(dbPath, { force: true });
  const bridge = new MockWhatsAppBridgeAdapter();
  return createBot({ dbPath, bridge });
}

test('scheduler sends reminders for dated and weekly tasks', async () => {
  const bot = build('scheduler-1');
  bot.storage.ensureUser('admin');
  bot.storage.setAccessLevel('admin', 3);
  bot.storage.setAccessLevel('manager', 2);

  await bot.botService.handleMessage({
    chatType: 'group',
    chatId: '1203630XXXXXXXX@g.us',
    userId: 'manager',
    text: '/task 09:00 @user1 06.04.2026 Подготовить отчёт'
  });

  await bot.botService.handleMessage({
    chatType: 'group',
    chatId: '1203630XXXXXXXX@g.us',
    userId: 'manager',
    text: '/task 09:00 @user2 понедельник Еженедельная сводка'
  });

  const reminders = await bot.schedulerService.tick(new Date('2026-04-06T04:05:00.000Z'));
  assert.equal(reminders.length, 2);

  const groupCalls = bot.bridge.calls.filter((c) => c.action === 'send-group');
  const dmCalls = bot.bridge.calls.filter((c) => c.action === 'send-dm');
  assert.equal(groupCalls.length, 2);
  assert.equal(dmCalls.length, 2);
});
