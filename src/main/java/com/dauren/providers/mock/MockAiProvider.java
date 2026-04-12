package com.dauren.providers.mock;

import com.dauren.providers.AiProvider;

import java.util.List;

public class MockAiProvider implements AiProvider {
    @Override
    public String generateText(String prompt, String personaHint) {
        if (personaHint != null && !personaHint.isBlank()) {
            return "Понял Вас. " + prompt;
        }
        return "AI-ответ: " + prompt;
    }

    @Override
    public String rewriteText(String text, String style) {
        return "[" + style + "] " + text;
    }

    @Override
    public String summarizeMessages(List<String> messages) {
        return "Сводка(" + messages.size() + "): " + String.join(" ", messages);
    }

    @Override
    public String translateText(String direction, String text) {
        return "[" + direction + "] " + text;
    }
}
