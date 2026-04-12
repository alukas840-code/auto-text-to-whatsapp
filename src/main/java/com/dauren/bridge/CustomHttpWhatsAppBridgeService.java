package com.dauren.bridge;

import com.dauren.model.IncomingMessage;

import java.util.List;

public class CustomHttpWhatsAppBridgeService implements WhatsAppBridgeService {
    @Override public boolean supportsDelete() { return true; }
    @Override public boolean supportsKick() { return true; }
    @Override public boolean supportsAdd() { return true; }
    @Override public void sendGroupMessage(String chatId, String text) {}
    @Override public void sendDirectMessage(String userId, String text) {}
    @Override public void deleteMessage(String chatId, String messageId) {}
    @Override public void kickUser(String chatId, String userId) {}
    @Override public void addUser(String chatId, String phone) {}
    @Override public List<IncomingMessage> pollUpdates() { return List.of(); }
    @Override public byte[] downloadMedia(String mediaId) { return new byte[0]; }
}
