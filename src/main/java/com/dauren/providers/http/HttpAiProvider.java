package com.dauren.providers.http;

import com.dauren.providers.AiProvider;

import java.util.List;

public class HttpAiProvider implements AiProvider {
    @Override
    public String generateText(String prompt, String personaHint) {
        return "HTTP provider placeholder: " + prompt;
    }

    @Override
    public String rewriteText(String text, String style) {
        return "HTTP rewrite placeholder";
    }

    @Override
    public String summarizeMessages(List<String> messages) {
        return "HTTP summarize placeholder";
    }

    @Override
    public String translateText(String direction, String text) {
        return "HTTP translate placeholder";
    }
}
