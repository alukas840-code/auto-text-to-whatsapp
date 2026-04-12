package com.dauren.providers.http;

import com.dauren.providers.SttProvider;

public class HttpSttProvider implements SttProvider {
    @Override
    public String transcribe(String audioPath, String langHint) {
        return "HTTP STT placeholder";
    }
}
