package com.dauren.bridge;

import com.dauren.model.IncomingMessage;

import java.util.List;

public interface WhatsAppBridgeService {
    boolean supportsDelete();
    boolean supportsKick();
    boolean supportsAdd();

    void sendGroupMessage(String chatId, String text);
    void sendDirectMessage(String userId, String text);

    void deleteMessage(String chatId, String messageId);
    void kickUser(String chatId, String userId);
    void addUser(String chatId, String phone);

    List<IncomingMessage> pollUpdates();
    byte[] downloadMedia(String mediaId);
}
