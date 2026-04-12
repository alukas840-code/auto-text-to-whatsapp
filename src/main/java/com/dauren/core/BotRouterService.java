package com.dauren.core;

import com.dauren.bridge.WhatsAppBridgeService;
import com.dauren.model.ChatType;
import com.dauren.model.IncomingMessage;
import com.dauren.providers.AiProvider;
import com.dauren.providers.SttProvider;
import com.dauren.services.AntiSpamService;
import com.dauren.services.ContextService;
import com.dauren.storage.SqliteStorage;
import com.dauren.tasks.TaskService;
import com.dauren.users.AccessService;
import com.dauren.users.GroupMemberService;
import com.dauren.users.ProfileService;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class BotRouterService {
    private final Set<String> allowedGroups;
    private final String defaultPersonaName;
    private final String personaRole;
    private final boolean greetingEnabled;
    private final AccessService accessService;
    private final ProfileService profileService;
    private final GroupMemberService groupMemberService;
    private final TaskService taskService;
    private final ContextService contextService;
    private final AntiSpamService antiSpamService;
    private final AiProvider aiProvider;
    private final SttProvider sttProvider;
    private final SqliteStorage storage;
    private final WhatsAppBridgeService bridge;

    public BotRouterService(Set<String> allowedGroups,
                            String defaultPersonaName,
                            String personaRole,
                            boolean greetingEnabled,
                            AccessService accessService,
                            ProfileService profileService,
                            GroupMemberService groupMemberService,
                            TaskService taskService,
                            ContextService contextService,
                            AntiSpamService antiSpamService,
                            AiProvider aiProvider,
                            SttProvider sttProvider,
                            SqliteStorage storage,
                            WhatsAppBridgeService bridge) {
        this.allowedGroups = allowedGroups;
        this.defaultPersonaName = defaultPersonaName;
        this.personaRole = personaRole;
        this.greetingEnabled = greetingEnabled;
        this.accessService = accessService;
        this.profileService = profileService;
        this.groupMemberService = groupMemberService;
        this.taskService = taskService;
        this.contextService = contextService;
        this.antiSpamService = antiSpamService;
        this.aiProvider = aiProvider;
        this.sttProvider = sttProvider;
        this.storage = storage;
        this.bridge = bridge;
    }

    public String handle(IncomingMessage message) {
        if (!canProcess(message)) return null;

        storage.ensureUser(message.userId(), message.userName());
        if (message.chatType() == ChatType.GROUP) {
            groupMemberService.remember(message.chatId(), message.userId());
        }

        String text = message.text() == null ? "" : message.text().trim();
        antiSpamService.assertInputLength(text);

        if (!text.startsWith("/") && message.chatType() == ChatType.DM) {
            antiSpamService.enforceCooldown(message.userId(), "ai");
            String greeting = null;
            if (greetingEnabled && !contextService.isGreetingSent(message.userId())) {
                contextService.markGreetingSent(message.userId());
                greeting = "Здравствуйте, я " + defaultPersonaName + " — ваш " + personaRole + ".";
            }
            String answer = aiProvider.generateText(text, defaultPersonaName + ", обращение на Вы");
            return antiSpamService.trimOutput(greeting == null ? answer : greeting + "\n\n" + answer);
        }

        if (!text.startsWith("/")) return null;

        String[] split = text.split("\\s+");
        String cmd = split[0].toLowerCase();
        List<String> args = Arrays.stream(split).skip(1).toList();
        accessService.require(message.userId(), cmd);

        if (cmd.equals("/g")) antiSpamService.bumpWindow(message.userId(), cmd, 60, 5);
        if (cmd.equals("/task")) antiSpamService.bumpWindow(message.userId(), cmd, 60, 10);
        if (List.of("/ai", "/sum", "/rw", "/tr").contains(cmd)) antiSpamService.enforceCooldown(message.userId(), "ai");

        return switch (cmd) {
            case "/ping" -> "На связи.";
            case "/help" -> helpFor(message.userId(), message.chatType());
            case "/clear" -> message.chatType() == ChatType.DM ? contextService.clearDm(message.userId()) : contextService.clearGroup(message.chatId());
            case "/ai" -> antiSpamService.trimOutput(aiProvider.generateText(String.join(" ", args), null));
            case "/sum" -> antiSpamService.trimOutput(aiProvider.summarizeMessages(message.recentMessages()));
            case "/rw" -> antiSpamService.trimOutput(aiProvider.rewriteText(String.join(" ", args), "вежливый деловой"));
            case "/tr" -> handleTranslate(args);
            case "/g" -> handleG(message, args);
            case "/list" -> handleList(message.chatId());
            case "/who" -> handleWho(args);
            case "/profile" -> handleProfile(args);
            case "/surname" -> handleSurname(message.userId(), args);
            case "/role" -> handleRole(message.userId(), args);
            case "/setprofile" -> handleSetProfile(message.userId(), args);
            case "/dostup" -> handleDostup(message.userId(), args);
            case "/task" -> "Создано задач: " + taskService.create(message.userId(), message.chatId(), args) + ".";
            case "/tasks" -> handleTasks(message.chatId());
            case "/done" -> handleDone(message.userId(), args);
            case "/taskdel" -> handleTaskDel(message.userId(), args);
            case "/del" -> handleDelete(message);
            case "/kick" -> handleKick(message.chatId(), args);
            case "/add" -> handleAdd(message.chatId(), args);
            default -> "Неизвестная команда. Используйте /help.";
        };
    }

    private boolean canProcess(IncomingMessage message) {
        if (message.chatType() == ChatType.DM) return true;
        if (!allowedGroups.contains(message.chatId())) return false;
        String text = message.text() == null ? "" : message.text().trim();
        return text.startsWith("/") || message.mentionsBot() || message.replyToBot();
    }

    private String handleTranslate(List<String> args) {
        if (args.size() < 2) throw new IllegalArgumentException("Формат: /tr ru-kz <текст>");
        String dir = args.getFirst();
        if (!dir.equals("ru-kz") && !dir.equals("kz-ru")) throw new IllegalArgumentException("Направление ru-kz или kz-ru");
        return antiSpamService.trimOutput(aiProvider.translateText(dir, String.join(" ", args.subList(1, args.size()))));
    }

    private String handleG(IncomingMessage message, List<String> args) {
        if (message.replyAudioFile() == null || message.replyAudioFile().isBlank()) throw new IllegalArgumentException("/g только reply на голосовое");
        String hint = args.isEmpty() ? null : args.getFirst();
        if (hint != null && !(hint.equals("ru") || hint.equals("kz"))) throw new IllegalArgumentException("/g [ru|kz]");
        return antiSpamService.trimOutput(sttProvider.transcribe(message.replyAudioFile(), hint));
    }

    private String handleList(String chatId) {
        var rows = profileService.list(chatId);
        if (rows.isEmpty()) return "Участники ещё не зарегистрированы.";
        return rows.stream().map(r -> (r.surname() != null ? r.surname() : r.whatsappName()) + " — " + (r.role() == null ? "не указано" : r.role())).reduce((a, b) -> a + "\n" + b).orElse("-");
    }

    private String handleWho(List<String> args) {
        String user = mention(args);
        var p = profileService.profile(user);
        return (p.surname() != null ? p.surname() : p.whatsappName()) + "; должность: " + (p.role() == null ? "не указана" : p.role()) + "; уровень: " + p.accessLevel();
    }

    private String handleProfile(List<String> args) {
        String user = mention(args);
        var p = profileService.profile(user);
        return "Профиль @" + p.id() + "\nwhatsapp_name: " + p.whatsappName() + "\nsurname: " + p.surname() + "\nrole: " + p.role() + "\naccess_level: " + p.accessLevel();
    }

    private String handleSurname(String actorId, List<String> args) {
        String user = mention(args);
        profileService.setSurname(actorId, user, String.join(" ", args.subList(1, args.size())));
        return "Фамилия обновлена.";
    }

    private String handleRole(String actorId, List<String> args) {
        String user = mention(args);
        profileService.setRole(actorId, user, String.join(" ", args.subList(1, args.size())));
        return "Должность обновлена.";
    }

    private String handleSetProfile(String actorId, List<String> args) {
        String user = mention(args);
        String payload = String.join(" ", args.subList(1, args.size()));
        String[] split = payload.split("\\|");
        if (split.length != 2) throw new IllegalArgumentException("Формат: /setprofile @user <фамилия> | <должность>");
        profileService.setProfile(actorId, user, split[0].trim(), split[1].trim());
        return "Профиль обновлён.";
    }

    private String handleDostup(String actorId, List<String> args) {
        if (args.isEmpty()) return "Ваш уровень доступа: " + accessService.levelOf(actorId) + ".";
        String user = mention(args);
        int level = Integer.parseInt(args.get(1));
        accessService.setAccess(actorId, user, level);
        return "Пользователю @" + user + " установлен уровень " + level + ".";
    }

    private String handleTasks(String chatId) {
        var tasks = taskService.list(chatId);
        if (tasks.isEmpty()) return "Активных задач нет.";
        return tasks.stream().map(t -> "#" + t.id() + " @" + t.assigneeId() + ": " + t.text()).reduce((a, b) -> a + "\n" + b).orElse("");
    }

    private String handleDone(String actorId, List<String> args) {
        long id = Long.parseLong(args.getFirst());
        taskService.done(actorId, id, java.time.LocalDate.now().toString());
        return "Задача #" + id + " отмечена выполненной.";
    }

    private String handleTaskDel(String actorId, List<String> args) {
        long id = Long.parseLong(args.getFirst());
        taskService.delete(actorId, id);
        return "Задача #" + id + " удалена.";
    }

    private String handleDelete(IncomingMessage message) {
        if (message.replyToMessageId() == null) throw new IllegalArgumentException("/del только reply на сообщение");
        if (message.replyToMessageType() != null && !List.of("text", "image", "audio", "video", "document").contains(message.replyToMessageType())) {
            throw new IllegalArgumentException("Нельзя удалить сообщение данного типа");
        }
        if (!bridge.supportsDelete()) throw new IllegalArgumentException("Удаление не поддерживается bridge");
        bridge.deleteMessage(message.chatId(), message.replyToMessageId());
        return "Сообщение удалено.";
    }

    private String handleKick(String chatId, List<String> args) {
        if (!bridge.supportsKick()) throw new IllegalArgumentException("Исключение не поддерживается bridge");
        String user = mention(args);
        bridge.kickUser(chatId, user);
        return "Пользователь @" + user + " исключён.";
    }

    private String handleAdd(String chatId, List<String> args) {
        String phone = args.getFirst();
        if (!phone.matches("^\\+\\d{10,15}$")) throw new IllegalArgumentException("Формат: /add +70000000000");
        if (!bridge.supportsAdd()) throw new IllegalArgumentException("Добавление не поддерживается bridge");
        bridge.addUser(chatId, phone);
        return "Номер " + phone + " добавлен.";
    }

    private String mention(List<String> args) {
        if (args.isEmpty() || !args.getFirst().startsWith("@")) throw new IllegalArgumentException("Укажите @user");
        return args.getFirst().substring(1);
    }

    private String helpFor(String userId, ChatType chatType) {
        int level = accessService.levelOf(userId);
        String base = "/help, /ai, /sum, /rw, /tr, /g, /list, /who, /profile, /dostup, /tasks, /done, /ping, /clear";
        String lvl2 = ", /task, /taskdel, /del, /kick, /add";
        String lvl3 = ", /surname, /role, /setprofile, /dostup @user 1|2|3";
        String commands = base + (level >= 2 ? lvl2 : "") + (level >= 3 ? lvl3 : "");
        if (chatType == ChatType.DM) {
            return "Здравствуйте, я " + defaultPersonaName + " — ваш " + personaRole + ".\nДоступные команды: " + commands;
        }
        return "Список доступных команд: " + commands;
    }
}
