package com.dauren.services;

import com.dauren.storage.SqliteStorage;

public class AntiSpamService {
    private final SqliteStorage storage;
    private final int maxInputChars;
    private final int maxOutputChars;
    private final int aiCooldownSec;

    public AntiSpamService(SqliteStorage storage, int maxInputChars, int maxOutputChars, int aiCooldownSec) {
        this.storage = storage;
        this.maxInputChars = maxInputChars;
        this.maxOutputChars = maxOutputChars;
        this.aiCooldownSec = aiCooldownSec;
    }

    public void assertInputLength(String text) {
        if (text != null && text.length() > maxInputChars) {
            throw new IllegalArgumentException("Слишком длинный запрос.");
        }
    }

    public String trimOutput(String text) {
        if (text == null || text.length() <= maxOutputChars) return text;
        return text.substring(0, maxOutputChars) + "…";
    }

    public void enforceCooldown(String userId, String scope) {
        long now = System.currentTimeMillis() / 1000;
        String key = "cooldown:" + scope + ":" + userId;
        var row = storage.getRate(key).orElse(null);
        if (row != null && row.cooldownUntil() > now) {
            throw new IllegalArgumentException("Слишком часто. Повторите позже.");
        }
        storage.upsertRate(key, now, 0, now + aiCooldownSec);
    }

    public void bumpWindow(String userId, String command, int windowSec, int maxCount) {
        long now = System.currentTimeMillis() / 1000;
        String key = "window:" + command + ":" + userId;
        var row = storage.getRate(key).orElse(null);
        long start = row == null ? now : row.windowStart();
        int count = row == null ? 0 : row.count();
        if (now - start >= windowSec) {
            start = now;
            count = 0;
        }
        count++;
        if (count > maxCount) throw new IllegalArgumentException("Слишком частое использование " + command);
        storage.upsertRate(key, start, count, 0);
    }
}
