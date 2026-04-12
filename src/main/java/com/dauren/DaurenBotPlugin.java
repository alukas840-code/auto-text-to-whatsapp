package com.dauren;

import com.dauren.bridge.CloudApiWhatsAppBridgeService;
import com.dauren.bridge.CustomHttpWhatsAppBridgeService;
import com.dauren.bridge.MockWhatsAppBridgeService;
import com.dauren.bridge.WhatsAppBridgeService;
import com.dauren.core.BotRouterService;
import com.dauren.providers.AiProvider;
import com.dauren.providers.SttProvider;
import com.dauren.providers.http.HttpAiProvider;
import com.dauren.providers.http.HttpSttProvider;
import com.dauren.providers.mock.MockAiProvider;
import com.dauren.providers.mock.MockSttProvider;
import com.dauren.services.AntiSpamService;
import com.dauren.services.AuditLogService;
import com.dauren.services.ContextService;
import com.dauren.storage.DatabaseInitializer;
import com.dauren.storage.SqliteStorage;
import com.dauren.tasks.SchedulerService;
import com.dauren.tasks.TaskCommandParser;
import com.dauren.tasks.TaskService;
import com.dauren.users.AccessService;
import com.dauren.users.GroupMemberService;
import com.dauren.users.ProfileService;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

public class DaurenBotPlugin extends JavaPlugin {
    private BotRouterService routerService;
    private SchedulerService schedulerService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        ensureSecretsFile();

        try {
            Path dbPath = getDataFolder().toPath().resolve("dauren.sqlite");
            DatabaseInitializer initializer = new DatabaseInitializer(this, dbPath);
            initializer.initSchema();

            SqliteStorage storage = new SqliteStorage(initializer.jdbcUrl());
            AuditLogService audit = new AuditLogService(storage);
            AccessService accessService = new AccessService(storage, audit);
            ProfileService profileService = new ProfileService(storage, accessService, audit);
            GroupMemberService groupMemberService = new GroupMemberService(storage);
            ContextService contextService = new ContextService(storage);

            FileConfiguration cfg = getConfig();
            String timezone = cfg.getString("timezone", "Asia/Almaty");
            String defaultReminder = cfg.getString("default-reminder-time", "09:00");
            Set<String> allowedGroups = new HashSet<>(cfg.getStringList("allowed-groups"));

            AntiSpamService antiSpamService = new AntiSpamService(
                    storage,
                    cfg.getInt("anti-spam.max-input-chars", 2000),
                    cfg.getInt("anti-spam.max-output-chars", 3000),
                    cfg.getInt("anti-spam.ai-cooldown-sec", 3)
            );

            AiProvider aiProvider = createAiProvider();
            SttProvider sttProvider = createSttProvider();
            WhatsAppBridgeService bridgeService = createBridgeService();

            TaskService taskService = new TaskService(storage, accessService, new TaskCommandParser(), audit, defaultReminder);
            schedulerService = new SchedulerService(taskService, bridgeService, timezone);

            routerService = new BotRouterService(
                    allowedGroups,
                    cfg.getString("persona.dm-name", "Дәурен"),
                    cfg.getString("persona.dm-role", "виртуальный коллега"),
                    cfg.getBoolean("persona.dm-greeting-enabled", true),
                    accessService,
                    profileService,
                    groupMemberService,
                    taskService,
                    contextService,
                    antiSpamService,
                    aiProvider,
                    sttProvider,
                    storage,
                    bridgeService
            );

            getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
                try {
                    schedulerService.tick();
                    bridgeService.pollUpdates().forEach(msg -> {
                        try {
                            String result = routerService.handle(msg);
                            if (result != null && !result.isBlank()) {
                                if (msg.chatType().name().equals("DM")) {
                                    bridgeService.sendDirectMessage(msg.userId(), result);
                                } else {
                                    bridgeService.sendGroupMessage(msg.chatId(), result);
                                }
                            }
                        } catch (Exception ignored) {
                            // keep plugin quiet; no payload logging
                        }
                    });
                } catch (Exception ignored) {
                    // fatal bridge/scheduler errors intentionally suppressed from stacktrace spam
                }
            }, 20L, 20L * 30L);

            getLogger().info("DaurenBot enabled.");
        } catch (SQLException | IOException e) {
            getLogger().severe("Database init error. Plugin disabled.");
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("DaurenBot disabled.");
    }

    private void ensureSecretsFile() {
        Path secrets = getDataFolder().toPath().resolve("secrets.yml");
        if (Files.exists(secrets)) return;
        try {
            Files.createDirectories(getDataFolder().toPath());
            try (var in = getResource("secrets.example.yml")) {
                if (in != null) Files.copy(in, secrets);
            }
            getLogger().warning("secrets.yml not found, template created.");
        } catch (IOException ignored) {
            getLogger().warning("secrets.yml missing and could not be created.");
        }
    }

    private AiProvider createAiProvider() {
        String mode = getConfig().getString("ai.provider", "mock");
        if ("http".equalsIgnoreCase(mode) || "custom-http".equalsIgnoreCase(mode)) return new HttpAiProvider();
        return new MockAiProvider();
    }

    private SttProvider createSttProvider() {
        String mode = getConfig().getString("stt.provider", "mock");
        if ("http".equalsIgnoreCase(mode) || "custom-http".equalsIgnoreCase(mode)) return new HttpSttProvider();
        return new MockSttProvider();
    }

    private WhatsAppBridgeService createBridgeService() {
        String mode = getConfig().getString("whatsapp.mode", "mock");
        return switch (mode.toLowerCase()) {
            case "custom-http" -> new CustomHttpWhatsAppBridgeService();
            case "cloud-api" -> new CloudApiWhatsAppBridgeService();
            default -> new MockWhatsAppBridgeService(true, false, false);
        };
    }
}
