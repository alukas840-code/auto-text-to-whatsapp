package com.dauren.users;

import com.dauren.services.AuditLogService;
import com.dauren.storage.SqliteStorage;

import java.util.List;

public class ProfileService {
    private final SqliteStorage storage;
    private final AccessService access;
    private final AuditLogService audit;

    public ProfileService(SqliteStorage storage, AccessService access, AuditLogService audit) {
        this.storage = storage;
        this.access = access;
        this.audit = audit;
    }

    public List<SqliteStorage.UserProfileRow> list(String chatId) {
        return storage.listGroupProfiles(chatId);
    }

    public SqliteStorage.UserProfileRow profile(String userId) {
        return storage.getProfile(userId).orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));
    }

    public void setSurname(String actorId, String userId, String surname) {
        access.require(actorId, "/surname");
        storage.setSurname(userId, surname);
        audit.log(actorId, "set-surname", "user", userId, "{}");
    }

    public void setRole(String actorId, String userId, String role) {
        access.require(actorId, "/role");
        storage.setRole(userId, role);
        audit.log(actorId, "set-role", "user", userId, "{}");
    }

    public void setProfile(String actorId, String userId, String surname, String role) {
        access.require(actorId, "/setprofile");
        storage.setProfile(userId, surname, role);
        audit.log(actorId, "set-profile", "user", userId, "{}");
    }
}
