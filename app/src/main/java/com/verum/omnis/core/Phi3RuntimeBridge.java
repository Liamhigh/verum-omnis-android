package com.verum.omnis.core;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * External GGUF bridge for Phi-3 Mini.
 *
 * This bridge does not bundle the 2.2 GB model or the llama.cpp Android build
 * into the APK. Instead, it calls a staged local llama.cpp CLI on the device.
 *
 * Runtime contract:
 * - llama.cpp is staged under /data/local/tmp/llama.cpp
 * - the Phi-3 GGUF is staged in the app's external models directory
 * - execution remains fully offline
 */
public final class Phi3RuntimeBridge {

    private static final String DEFAULT_LLAMA_ROOT = "/data/local/tmp/llama.cpp";
    private static final String DEFAULT_LLAMA_BIN = DEFAULT_LLAMA_ROOT + "/bin/llama-cli";
    private static final String DEFAULT_LLAMA_LIB = DEFAULT_LLAMA_ROOT + "/lib";
    private static final String DEFAULT_LLAMA_MODEL_DIR = DEFAULT_LLAMA_ROOT + "/models";
    private static final int DEFAULT_CONTEXT = 4096;
    private static final int DEFAULT_TOKENS = 256;

    private Phi3RuntimeBridge() {}

    public static String describeRuntime(Context context) {
        File modelFile = resolveModelFile(context.getApplicationContext());
        StringBuilder sb = new StringBuilder();
        sb.append("Phi-3 model: ")
                .append(modelFile.exists() ? "staged" : "missing")
                .append(" at ")
                .append(modelFile.getAbsolutePath())
                .append(". ");
        sb.append("llama.cpp bridge: ").append(isLlamaCliPresent() ? "staged" : "missing");
        if (!isLlamaCliPresent()) {
            sb.append(" at ").append(DEFAULT_LLAMA_BIN);
        }
        return sb.toString();
    }

    public static boolean isReady(Context context) {
        return Phi3ModelPackManager.isStaged(context.getApplicationContext()) && isLlamaCliPresent();
    }

    public static String generateResponseBlocking(Context context, String prompt) throws Exception {
        Context appContext = context.getApplicationContext();
        if (!Phi3ModelPackManager.isStaged(appContext)) {
            throw new IllegalStateException("Phi-3 Mini GGUF is not staged on the device yet.");
        }
        if (!isLlamaCliPresent()) {
            throw new IllegalStateException("llama.cpp Android CLI is not staged on the device yet.");
        }

        File modelFile = resolveModelFile(appContext);
        if (modelFile == null || !modelFile.exists()) {
            throw new IllegalStateException("Phi-3 Mini GGUF is not readable from the runtime staging paths.");
        }
        String command = buildShellCommand(modelFile.getAbsolutePath(), prompt);
        ProcessBuilder pb = new ProcessBuilder("/system/bin/sh", "-c", command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (output.length() > 0) {
                    output.append('\n');
                }
                output.append(line);
            }
        }

        int exit = process.waitFor();
        if (exit != 0) {
            throw new IllegalStateException("Phi-3 bridge exited with code " + exit + ": " + clip(output.toString(), 1200));
        }
        return sanitizeResponse(output.toString());
    }

    static String buildShellCommand(String modelPath, String prompt) {
        String escapedPrompt = shellQuote(buildPrompt(prompt));
        String escapedModel = shellQuote(modelPath);
        String escapedLib = shellQuote(DEFAULT_LLAMA_LIB);
        String escapedBin = shellQuote(DEFAULT_LLAMA_BIN);
        return "export LD_LIBRARY_PATH=" + escapedLib
                + " && " + escapedBin
                + " -m " + escapedModel
                + " -c " + DEFAULT_CONTEXT
                + " -n " + DEFAULT_TOKENS
                + " --temp 0.2"
                + " -p " + escapedPrompt;
    }

    private static String buildPrompt(String prompt) {
        String userPrompt = prompt == null ? "" : prompt.trim();
        return "<|user|>\n" + clip(userPrompt, 6000) + "\n<|assistant|>\n";
    }

    private static String shellQuote(String value) {
        String safe = value == null ? "" : value;
        return "'" + safe.replace("'", "'\"'\"'") + "'";
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        if (normalized.length() <= max) {
            return normalized;
        }
        return normalized.substring(0, Math.max(1, max));
    }

    private static String sanitizeResponse(String raw) {
        if (raw == null) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        for (String line : raw.split("\\r?\\n")) {
            String trimmed = line == null ? "" : line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String lower = trimmed.toLowerCase(Locale.US);
            if (lower.startsWith("load_tensors:")
                    || lower.startsWith("llama_context:")
                    || lower.startsWith("common_init_from_params:")
                    || lower.startsWith("system_info:")
                    || lower.startsWith("main:")) {
                continue;
            }
            lines.add(trimmed);
        }
        return String.join("\n", lines).trim();
    }

    private static boolean isLlamaCliPresent() {
        File file = new File(DEFAULT_LLAMA_BIN);
        return file.exists() && file.length() > 0L;
    }

    private static File resolveModelFile(Context context) {
        String fileName = LocalAiModelRegistry.get(LocalAiModelRegistry.MODEL_PHI3_MINI).fileName;
        File runtimeModel = new File(DEFAULT_LLAMA_MODEL_DIR, fileName);
        if (runtimeModel.exists() && runtimeModel.length() > 0L) {
            return runtimeModel;
        }
        return Phi3ModelPackManager.getExpectedFile(context);
    }
}
