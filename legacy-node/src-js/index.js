import { defaultConfig } from './config.js';
import { SqliteStorage } from './repositories/sqliteStorage.js';
import { AccessService } from './services/accessService.js';
import { ProfileService } from './services/profileService.js';
import { TaskService } from './services/taskService.js';
import { AntiSpamService } from './services/antiSpamService.js';
import { BotService } from './services/botService.js';
import { SchedulerService } from './services/schedulerService.js';
import { MockAIProvider } from './providers/aiProvider.js';
import { MockSTTProvider } from './providers/sttProvider.js';
import { MockWhatsAppBridgeAdapter } from './bridge/whatsAppBridge.js';

export function createBot({ config = defaultConfig, aiProvider, sttProvider, bridge, dbPath } = {}) {
  const storage = new SqliteStorage(dbPath);
  const accessService = new AccessService(storage);
  const profileService = new ProfileService(storage, accessService);
  const taskService = new TaskService(storage, accessService, config);
  const antiSpamService = new AntiSpamService(storage, config.antiSpam);
  const bridgeAdapter = bridge || new MockWhatsAppBridgeAdapter();

  const botService = new BotService({
    config,
    storage,
    accessService,
    profileService,
    taskService,
    antiSpamService,
    aiProvider: aiProvider || new MockAIProvider(),
    sttProvider: sttProvider || new MockSTTProvider(),
    bridge: bridgeAdapter
  });

  const schedulerService = new SchedulerService(taskService, bridgeAdapter);

  return {
    botService,
    schedulerService,
    storage,
    accessService,
    profileService,
    taskService,
    bridge: bridgeAdapter
  };
}
