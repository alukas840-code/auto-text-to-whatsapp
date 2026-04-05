package com.dauren;

/**
 * Dummy implementation of AiProvider used for development.  It
 * generates trivial responses and should be replaced with a real
 * provider implementation that calls an AI backend.  The methods
 * intentionally return simple values rather than printing to the
 * console to keep the server output clean.
 */
public class DummyAiProvider implements AiProvider {
    @Override
    public String generateText(String prompt) {
        // Echo the prompt back with a prefix.  Replace with call to AI.
        return "(AI) " + prompt;
    }

    @Override
    public String rewriteStyle(String prompt, String style) {
        // This dummy does not change style.  Replace with real logic.
        return prompt;
    }

    @Override
    public String summarize(String[] messages) {
        if (messages == null || messages.length == 0) {
            return "";
        }
        // Concatenate messages and truncate for brevity
        StringBuilder sb = new StringBuilder();
        for (String msg : messages) {
            if (sb.length() > 0) sb.append(" | ");
            sb.append(msg);
            if (sb.length() > 200) {
                sb.append(" ...");
                break;
            }
        }
        return sb.toString();
    }
}