package com.verum.omnis.core;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class Phi3RuntimeBridgeTest {

    @Test
    public void shellCommandUsesExpectedLlamaCliAndModelPath() {
        String command = Phi3RuntimeBridge.buildShellCommand("/sdcard/Android/data/com.verum.omnis/files/models/Phi-3-mini-4k-instruct-q4.gguf", "test prompt");
        assertTrue(command.contains("/data/local/tmp/llama.cpp/bin/llama-cli"));
        assertTrue(command.contains("Phi-3-mini-4k-instruct-q4.gguf"));
        assertTrue(command.contains("-c 4096"));
        assertTrue(command.contains("-n 256"));
    }
}
