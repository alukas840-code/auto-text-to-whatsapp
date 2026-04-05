package com.dauren;

/**
 * AiProvider defines an abstraction over the AI backend.  Implementations
 * of this interface should provide methods to generate responses,
 * rewrite text into different styles and summarise a collection of
 * messages.  By coding against this interface the bot can be
 * configured to use different AI models or services without changing
 * the core plugin.
 */
public interface AiProvider {
    /**
     * Generate a response to a prompt.  This is the fundamental
     * capability used by the /ai command or free-form DM queries.
     *
     * @param prompt the user prompt
     * @return the generated response
     */
    String generateText(String prompt);

    /**
     * Rewrite a piece of text into a different style.  Used by the
     * /rw command.  Typical styles might be "official", "short", etc.
     *
     * @param prompt the input text
     * @param style the target style
     * @return the rewritten text
     */
    String rewriteStyle(String prompt, String style);

    /**
     * Summarise an array of messages.  Used by the /sum command.
     *
     * @param messages the messages to summarise
     * @return a concise summary
     */
    String summarize(String[] messages);
}