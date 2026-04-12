package com.dauren.providers.mock;

import com.dauren.providers.SttProvider;

public class MockSttProvider implements SttProvider {
    @Override
    public String transcribe(String audioPath, String langHint) {
        if ("kz".equalsIgnoreCase(langHint)) {
            return "Бұл тесттік транскрипция.";
        }
        return "Это тестовая транскрипция.";
    }
}
