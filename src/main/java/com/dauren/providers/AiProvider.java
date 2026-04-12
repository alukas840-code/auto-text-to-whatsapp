package com.dauren.providers;

import java.util.List;

public interface AiProvider {
    String generateText(String prompt, String personaHint);
    String rewriteText(String text, String style);
    String summarizeMessages(List<String> messages);
    String translateText(String direction, String text);
}
