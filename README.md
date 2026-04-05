# WhatsApp-бот «Дәурен» — backend core

Репозиторий содержит backend-ядро бота для WhatsApp-групп и личных сообщений.

## Реализованные команды

### AI/системные
- `/help`
- `/ai <текст>`
- `/sum [N]`
- `/rw <текст>`
- `/tr ru-kz|kz-ru <текст>`
- `/g [ru|kz]` (reply на голосовое)
- `/ping`
- `/clear`

### Профили
- `/list`
- `/who @user`
- `/profile @user`
- `/surname @user <фамилия>` (уровень 3)
- `/role @user <должность>` (уровень 3)
- `/setprofile @user <фамилия> | <должность>` (уровень 3)

### Доступы
- `/dostup`
- `/dostup @user 1|2|3` (только уровень 3)

### Задачи
- `/task ...`
- `/tasks`
- `/done <id>`
- `/taskdel <id>`

### Модерация
- `/del` (reply + уровни 2/3)
- `/kick @user` (уровни 2/3)
- `/add +70000000000` (уровни 2/3)

## Архитектура

- **Bridge adapter**: `src/bridge/whatsAppBridge.js`
  - Абстракция операций WhatsApp API: delete/kick/add/send.
  - `MockWhatsAppBridgeAdapter` хранит вызовы и может имитировать неподдерживаемые действия.
- **AI/STT providers**: `src/providers/*`
  - Интерфейсы + mock реализации.
- **Storage**: `src/repositories/sqliteStorage.js`
  - SQLite с таблицами: `users`, `profiles`, `access_levels`, `group_members`, `tasks`, `task_events`, `audit_logs`, `conversation_contexts`, `rate_limits`.
- **Scheduler**: `src/services/schedulerService.js`
  - На каждом `tick()` запускает обработку напоминаний dated/weekly задач и доставляет их в группу + личку.
- **Core services**: `src/services/*`
  - RBAC, профили, задачи, антиспам и маршрутизация команд.

## Поведение DM persona

- В личке бот работает как «Дәурен, виртуальный коллега».
- На первое сообщение отправляет приветствие.
- Обычный текст без `/` интерпретируется как AI prompt.
- `/clear` очищает только DM-контекст пользователя.

## Ограничения mock режима

- Mock bridge по умолчанию не поддерживает `kick` и `add`, поэтому команды возвращают понятную ошибку.
- Mock AI/STT не делают реальных сетевых запросов; ответы предсказуемые для тестов.

## Запуск

```bash
npm test
```

Тесты используют Node test runner и SQLite CLI (`sqlite3` должен быть установлен в окружении).
