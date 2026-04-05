package com.dauren;

/**
 * SttProvider defines an abstraction over a speech-to-text service.
 * Implementations should take raw audio data and return its text
 * transcription.  The language hint allows callers to provide a hint
 * when the user explicitly specifies a language (for example, ru
 * or kz).  If the hint is null or empty, automatic language
 * detection should be performed by the implementation if available.
 */
public interface SttProvider {
    /**
     * Transcribe speech audio into text.
     *
     * @param audioData the raw audio data as a byte array
     * @param languageHint two-letter language code (e.g. "ru", "kz") or null
     * @return the transcribed text
     * @throws Exception if the transcription fails
     */
    String transcribe(byte[] audioData, String languageHint) throws Exception;
}