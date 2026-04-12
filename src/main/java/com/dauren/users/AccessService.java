package com.dauren.users;

import com.dauren.services.AuditLogService;
import com.dauren.storage.SqliteStorage;

import java.util.HashMap;
import java.util.Map;

public class AccessService {
    private final SqliteStorage storage;
    private final AuditLogService audit;
    private final Map<String, Integer> commandLevels = new HashMap<>();

    public AccessService(SqliteStorage storage, AuditLogService audit) {
        this.storage = storage;
        this.audit = audit;
        init();
    }

    private void init() {
        for (String c : new String[]{"/help","/ai","/sum","/rw","/tr","/g","/list","/who","/profile","/dostup","/tasks","/done","/ping","/clear"}) commandLevels.put(c, 1);
        for (String c : new String[]{"/task","/taskdel","/del","/kick","/add"}) commandLevels.put(c, 2);
        for (String c : new String[]{"/surname","/role","/setprofile"}) commandLevels.put(c, 3);
    }

    public int levelOf(String userId) {
        return storage.getAccessLevel(userId);
    }

    public void require(String userId, String command) {
        int need = commandLevels.getOrDefault(command, 3);
        if (levelOf(userId) < need) throw new IllegalArgumentException("Недостаточно прав для " + command);
    }

    public void setAccess(String actorId, String targetId, int level) {
        require(actorId, "/setprofile");
        if (level < 1 || level > 3) throw new IllegalArgumentException("Уровень должен быть 1..3");
        storage.ensureUser(targetId, null);
        storage.setAccessLevel(targetId, level);
        audit.log(actorId, "set-access", "user", targetId, "{\"level\":" + level + "}");
    }
}
