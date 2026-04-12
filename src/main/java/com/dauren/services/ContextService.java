package com.dauren.services;

import com.dauren.storage.SqliteStorage;

public class ContextService {
    private final SqliteStorage storage;

    public ContextService(SqliteStorage storage) {
        this.storage = storage;
    }

    public String clearDm(String userId) {
        storage.saveContext("dm", userId, "[]", true);
        return "Контекст личного диалога очищен.";
    }

    public String clearGroup(String groupId) {
        storage.saveContext("group", groupId, "[]", false);
        return "Групповой AI-контекст очищен.";
    }

    public boolean isGreetingSent(String userId) {
        return storage.getContext("dm", userId).map(SqliteStorage.ContextRow::greeted).orElse(false);
    }

    public void markGreetingSent(String userId) {
        storage.saveContext("dm", userId, "[]", true);
    }
}
