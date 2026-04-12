package com.dauren.providers;

public interface SttProvider {
    String transcribe(String audioPath, String langHint);
}
