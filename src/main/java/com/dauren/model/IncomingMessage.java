package com.dauren.model;

import java.time.Instant;
import java.util.List;

public record IncomingMessage(
        String userId,
        String userName,
        String chatId,
        ChatType chatType,
        String text,
        boolean mentionsBot,
        boolean replyToBot,
        String replyToMessageId,
        String replyToMessageType,
        String replyAudioFile,
        List<String> recentMessages,
        Instant now
) {
    public IncomingMessage {
        if (now == null) now = Instant.now();
        if (recentMessages == null) recentMessages = List.of();
    }
}
