# DaurenBot — Paper 1.20.4 plugin

Этот проект теперь является **Java-плагином для Minecraft Paper 1.20.4**, который работает как runtime-хаб для WhatsApp-бота «Дәурен».

## Что делает плагин

- принимает входящие update/webhook события через bridge-layer;
- маршрутизирует команды и сообщения (DM/group логика);
- применяет RBAC (уровни 1/2/3);
- хранит профили, уровни доступа, задачи, контексты и audit-логи в SQLite;
- запускает scheduler напоминаний по задачам (group + DM);
- поддерживает AI/STT provider abstraction (mock, custom-http, cloud-api заготовка для bridge).

## Структура

- `src/main/java/com/dauren/DaurenBotPlugin.java` — main class Paper plugin.
- `src/main/java/com/dauren/core/BotRouterService.java` — роутинг команд и поведения DM/group.
- `src/main/java/com/dauren/tasks/*` — parser `/task`, task service, scheduler.
- `src/main/java/com/dauren/users/*` — доступы, профили, участники групп.
- `src/main/java/com/dauren/providers/*` — AI/STT abstractions и mock/http реализации.
- `src/main/java/com/dauren/bridge/*` — bridge abstraction и режимы mock/custom-http/cloud-api.
- `src/main/java/com/dauren/storage/*` — SQLite init и storage DAO.
- `src/main/resources/plugin.yml` — Paper metadata.
- `src/main/resources/config.yml` — runtime настройки.
- `src/main/resources/secrets.example.yml` — шаблон секретов.
- `src/main/resources/schema.sql` — схема SQLite.

## Сборка

```bash
./gradlew clean build
```

Ожидаемый артефакт:

```text
build/libs/dauren-bot-1.0.0.jar
```

## Установка

1. Соберите `.jar`.
2. Положите файл в папку `plugins/` Paper-сервера.
3. Запустите сервер.
4. Проверьте, что плагин загрузился без ошибок структуры (`plugin.yml`, классы, ресурсы).

## Конфигурация

### config.yml

Содержит:
- timezone, allowed-groups;
- default reminder time;
- logging flags;
- persona DM параметры;
- anti-spam лимиты;
- access-levels;
- provider modes (`whatsapp`, `ai`, `stt`).

### secrets.yml

При первом старте, если `plugins/DaurenBot/secrets.yml` отсутствует:
- плагин автоматически создаёт файл из `secrets.example.yml`;
- выводится одно короткое предупреждение;
- плагин не падает stacktrace’ом только из-за отсутствия секрета.

## Команды логики

- AI/системные: `/help`, `/ai`, `/sum`, `/rw`, `/tr`, `/g`, `/ping`, `/clear`
- Профили: `/list`, `/who`, `/profile`, `/surname`, `/role`, `/setprofile`
- Доступы: `/dostup`, `/dostup @user 1|2|3`
- Задачи: `/task`, `/tasks`, `/done`, `/taskdel`
- Модерация: `/del`, `/kick`, `/add`

## Логирование и безопасность

Плагин сделан «тихим»:
- не логирует payload/update содержимое;
- не печатает тексты пользователей;
- не печатает токены/секреты.

Логируются только важные lifecycle-события (startup/shutdown/error).

## Legacy Node.js код

Предыдущая Node.js реализация перенесена в `legacy-node/` для истории миграции и не участвует в Java runtime/архитектуре.
