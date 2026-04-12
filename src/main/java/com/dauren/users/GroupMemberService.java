package com.dauren.users;

import com.dauren.storage.SqliteStorage;

public class GroupMemberService {
    private final SqliteStorage storage;

    public GroupMemberService(SqliteStorage storage) {
        this.storage = storage;
    }

    public void remember(String chatId, String userId) {
        storage.rememberGroupMember(chatId, userId);
    }
}
