package com.dauren;

import org.bukkit.plugin.java.JavaPlugin;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Main entry point for the DaurenBot plugin.  This plugin acts as a hub
 * between WhatsApp and an AI backend.  It does not interact with the
 * Minecraft world and intentionally suppresses console output to avoid
 * cluttering the server log.  All configuration values are read from
 * the plugin's config.yml on startup.  The plugin instantiates a
 * bridge service to handle incoming/outgoing messages and a task
 * manager to schedule reminders.
 */
public final class DaurenBotPlugin extends JavaPlugin {
    private BridgeService bridgeService;
    private TaskManager taskManager;
    private AccessManager accessManager;
    private ProfileManager profileManager;

    @Override
    public void onEnable() {
        // Disable logging to console; avoid polluting the Paper console.
        Logger logger = this.getLogger();
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.OFF);

        // Save default config if it does not exist
        saveDefaultConfig();

        // Read important config values
        String timezone = getConfig().getString("timezone", "Asia/Almaty");
        String defaultReminderTime = getConfig().getString("default-reminder-time", "09:00");

        // Instantiate providers.  These dummy implementations do nothing
        // beyond returning placeholder responses.  Replace them with real
        // implementations that connect to your backend.
        AiProvider aiProvider = new DummyAiProvider();
        SttProvider sttProvider = new DummySttProvider();

        // Create the task manager to track tasks and schedule reminders
        taskManager = new TaskManager(this, timezone, defaultReminderTime);

        // Instantiate managers for access levels and profiles
        accessManager = new AccessManager();
        profileManager = new ProfileManager();

        // Create the bridge service.  It should be responsible for
        // communicating with WhatsApp and the AI backend, managing
        // access levels and profiles, and routing commands to the
        // appropriate managers.  Replace this stub with a real
        // implementation once a bridge becomes available.
        bridgeService = new BridgeService(this, aiProvider, sttProvider,
                                          taskManager, accessManager, profileManager);

        // Inject the bridge into the task manager so it can deliver reminders
        taskManager.setBridge(bridgeService);

        // Start the bridge.  In the current stub this does nothing
        bridgeService.start();

        // Start the reminder scheduler.  It runs asynchronously and
        // periodically checks for due tasks and sends reminders via the
        // bridge.  This will have no effect until tasks are created.
        taskManager.startReminderScheduler();
    }

    @Override
    public void onDisable() {
        // Gracefully stop bridge and scheduler
        if (bridgeService != null) {
            bridgeService.stop();
        }
        if (taskManager != null) {
            taskManager.stopScheduler();
        }
    }
}