package com.dauren.services;

import com.dauren.storage.SqliteStorage;

public class AuditLogService {
    private final SqliteStorage storage;

    public AuditLogService(SqliteStorage storage) {
        this.storage = storage;
    }

    public void log(String actorId, String action, String objectType, String objectId, String detailsJson) {
        storage.audit(actorId, action, objectType, objectId, detailsJson);
    }
}
