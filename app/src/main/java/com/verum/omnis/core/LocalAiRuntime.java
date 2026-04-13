package com.verum.omnis.core;

import android.content.Context;

import com.google.mediapipe.tasks.genai.llminference.LlmInference;
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Shared runtime for deterministic on-device model packs.
 *
 * The forensic engine remains the constitutional spine. Local models are
 * optional reviewers that can be swapped in explicitly and kept offline.
 */
public final class LocalAiRuntime {

    private static final int MEDIAPIPE_MAX_TOTAL_TOKENS = 512;
    private static final int RESERVED_OUTPUT_TOKENS = 96;
    private static final int MIN_USER_PROMPT_TOKENS = 48;

    public interface Callback {
        void onSuccess(String response);
        void onError(String errorMessage);
    }

    private static LocalAiRuntime instance;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private LlmInference llmInference;
    private String activeModelId;
    private String status = "No local AI model initialized.";

    private LocalAiRuntime() {}

    public static synchronized LocalAiRuntime getInstance() {
        if (instance == null) {
            instance = new LocalAiRuntime();
        }
        return instance;
    }

    public synchronized String getStatus() {
        return status;
    }

    public void warmUp(Context context, String modelId, String systemPrompt, Callback callback) {
        executor.execute(() -> {
            try {
                ensureInitialized(context.getApplicationContext(), modelId);
                callback.onSuccess(LocalAiModelRegistry.get(modelId).displayName + " ready on-device.");
            } catch (Exception e) {
                callback.onError(LocalAiModelRegistry.get(modelId).displayName + " initialization failed: " + e.getMessage());
            }
        });
    }

    public void generateResponse(Context context, String modelId, String systemPrompt, String userPrompt, Callback callback) {
        executor.execute(() -> {
            try {
                callback.onSuccess(generateResponseBlocking(context, modelId, systemPrompt, userPrompt));
            } catch (Exception e) {
                callback.onError(LocalAiModelRegistry.get(modelId).displayName + " response failed: " + e.getMessage());
            }
        });
    }

    public String generateResponseBlocking(Context context, String modelId, String systemPrompt, String userPrompt) throws Exception {
        ensureInitialized(context.getApplicationContext(), modelId);
        LocalAiModelRegistry.ModelDescriptor descriptor = LocalAiModelRegistry.get(modelId);
        PromptWindow promptWindow = fitPromptWindow(systemPrompt, userPrompt, descriptor.promptBudgetChars);
        synchronized (this) {
            LlmInferenceSession session = createSession(descriptor, promptWindow.systemPrompt);
            session.addQueryChunk(promptWindow.userPrompt);
            return session.generateResponse();
        }
    }

    private synchronized void ensureInitialized(Context context, String modelId) throws Exception {
        LocalAiModelRegistry.ModelDescriptor descriptor = LocalAiModelRegistry.get(modelId);
        if (!LocalAiModelRegistry.isRunnableWithCurrentRuntime(descriptor.id)) {
            throw new IllegalStateException(
                    descriptor.displayName
                            + " is staged as a "
                            + descriptor.backend.name()
                            + " model pack. The current runtime only supports MediaPipe .task models."
            );
        }
        if (llmInference != null && descriptor.id.equals(activeModelId)) {
            return;
        }

        File modelFile = LocalAiModelRegistry.resolveModelFile(context, descriptor.id);
        status = "Initializing " + descriptor.displayName + " from " + modelFile.getAbsolutePath();

        LlmInference.LlmInferenceOptions options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.getAbsolutePath())
                .setMaxTopK(descriptor.inferenceMaxTopK)
                .build();

        llmInference = LlmInference.createFromOptions(context, options);
        activeModelId = descriptor.id;
        status = descriptor.displayName + " ready. Model: " + descriptor.fileName;
    }

    private LlmInferenceSession createSession(LocalAiModelRegistry.ModelDescriptor descriptor, String systemPrompt) {
        LlmInferenceSession.LlmInferenceSessionOptions sessionOptions =
                LlmInferenceSession.LlmInferenceSessionOptions.builder()
                        .setTopK(descriptor.sessionTopK)
                        .setTemperature(descriptor.sessionTemperature)
                        .build();

        LlmInferenceSession session = LlmInferenceSession.createFromOptions(llmInference, sessionOptions);
        if (systemPrompt != null && !systemPrompt.trim().isEmpty()) {
            session.addQueryChunk(systemPrompt.trim());
        }
        return session;
    }

    private PromptWindow fitPromptWindow(String systemPrompt, String userPrompt, int maxPromptChars) {
        String safeSystem = normalize(systemPrompt);
        String safeUser = trimPrompt(userPrompt, maxPromptChars);

        int safeInputBudget = Math.max(MIN_USER_PROMPT_TOKENS, MEDIAPIPE_MAX_TOTAL_TOKENS - RESERVED_OUTPUT_TOKENS);
        int systemTokens = estimateTokenCount(safeSystem);
        if (systemTokens > safeInputBudget - MIN_USER_PROMPT_TOKENS) {
            safeSystem = clipToApproxTokens(safeSystem, safeInputBudget - MIN_USER_PROMPT_TOKENS);
            systemTokens = estimateTokenCount(safeSystem);
        }

        int userBudget = Math.max(MIN_USER_PROMPT_TOKENS, safeInputBudget - systemTokens);
        safeUser = clipToApproxTokens(safeUser, userBudget);
        status = LocalAiModelRegistry.get(activeModelId).displayName
                + " prompt budget: system≈" + systemTokens
                + " tokens, user≈" + estimateTokenCount(safeUser) + " tokens.";
        return new PromptWindow(safeSystem, safeUser);
    }

    private String trimPrompt(String userPrompt, int maxPromptChars) {
        if (userPrompt == null) {
            return "";
        }
        String normalized = userPrompt.trim();
        if (normalized.length() <= Math.max(1, maxPromptChars)) {
            return normalized;
        }
        return normalized.substring(0, Math.max(1, maxPromptChars))
                + "\n\n[Prompt clipped to stay within the safe local model context budget.]";
    }

    private String clipToApproxTokens(String text, int maxTokens) {
        String normalized = normalize(text);
        if (normalized.isEmpty()) {
            return "";
        }
        if (estimateTokenCount(normalized) <= Math.max(1, maxTokens)) {
            return normalized;
        }

        int maxChars = Math.max(120, maxTokens * 3);
        if (normalized.length() <= maxChars) {
            return normalized;
        }

        String clipped = normalized.substring(0, maxChars).trim();
        int breakIdx = Math.max(
                Math.max(clipped.lastIndexOf('\n'), clipped.lastIndexOf(". ")),
                Math.max(clipped.lastIndexOf("; "), clipped.lastIndexOf(", "))
        );
        if (breakIdx > 80) {
            clipped = clipped.substring(0, breakIdx).trim();
        }
        if (estimateTokenCount(clipped) > maxTokens && clipped.length() > 120) {
            clipped = clipped.substring(0, Math.min(clipped.length(), Math.max(120, maxChars / 2))).trim();
        }
        return clipped + "\n\n[Prompt clipped to stay within the safe local model context budget.]";
    }

    private int estimateTokenCount(String text) {
        String normalized = normalize(text);
        if (normalized.isEmpty()) {
            return 0;
        }
        int nonWhitespace = normalized.replaceAll("\\s+", "").length();
        return (int) Math.ceil(nonWhitespace / 2.75d);
    }

    private String normalize(String text) {
        return text == null ? "" : text.trim();
    }

    private static final class PromptWindow {
        final String systemPrompt;
        final String userPrompt;

        PromptWindow(String systemPrompt, String userPrompt) {
            this.systemPrompt = systemPrompt;
            this.userPrompt = userPrompt;
        }
    }
}
