package com.dauren;

/**
 * Dummy implementation of SttProvider used for development.  It
 * simply returns a placeholder string rather than performing
 * real speech-to-text conversion.  Replace with a real
 * implementation that calls an STT service.
 */
public class DummySttProvider implements SttProvider {
    @Override
    public String transcribe(byte[] audioData, String languageHint) {
        // This dummy does not perform real transcription.
        return "<voice transcription not implemented>";
    }
}