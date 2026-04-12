package com.dauren.bridge;

import com.dauren.model.IncomingMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MockWhatsAppBridgeService implements WhatsAppBridgeService {
    private final List<String> calls = new ArrayList<>();
    private final boolean canDelete;
    private final boolean canKick;
    private final boolean canAdd;

    public MockWhatsAppBridgeService(boolean canDelete, boolean canKick, boolean canAdd) {
        this.canDelete = canDelete;
        this.canKick = canKick;
        this.canAdd = canAdd;
    }

    public List<String> getCalls() {
        return Collections.unmodifiableList(calls);
    }

    @Override public boolean supportsDelete() { return canDelete; }
    @Override public boolean supportsKick() { return canKick; }
    @Override public boolean supportsAdd() { return canAdd; }

    @Override public void sendGroupMessage(String chatId, String text) { calls.add("group:" + chatId + ":" + text); }
    @Override public void sendDirectMessage(String userId, String text) { calls.add("dm:" + userId + ":" + text); }

    @Override public void deleteMessage(String chatId, String messageId) { if (!canDelete) throw new IllegalStateException("Bridge does not support delete"); calls.add("delete:" + messageId); }
    @Override public void kickUser(String chatId, String userId) { if (!canKick) throw new IllegalStateException("Bridge does not support kick"); calls.add("kick:" + userId); }
    @Override public void addUser(String chatId, String phone) { if (!canAdd) throw new IllegalStateException("Bridge does not support add"); calls.add("add:" + phone); }

    @Override public List<IncomingMessage> pollUpdates() { return List.of(); }
    @Override public byte[] downloadMedia(String mediaId) { return new byte[0]; }
}
